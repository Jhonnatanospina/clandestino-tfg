package com.clandestino.api.http;

import com.clandestino.api.Aplicacion;
import com.clandestino.api.BaseDePruebas;
import com.clandestino.api.RelojMovible;
import com.clandestino.api.repositorio.BaseDatos;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La API entera por HTTP, como la usará la app: servidor de verdad en un puerto libre, peticiones
 * con HttpClient y respuestas en JSON. Comprueba sobre todo el orden de api-contrato.md §6
 * (404/405, 401, 403 por estado antes que por rol, 400) y el recorrido completo de una cita.
 */
class PruebaApi {

    private static final String SECRETO = "clave-de-pruebas-con-mas-de-32-caracteres";

    private final HttpClient cliente = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private ServidorApi servidor;
    private String base;

    /** Lo que nos interesa de cada respuesta. */
    private record Respuesta(int estado, String texto, HttpResponse<String> http) {
        JSONObject json() {
            return new JSONObject(texto);
        }

        String error() {
            return json().getString("error");
        }
    }

    @BeforeEach
    void arrancar() throws IOException {
        BaseDatos baseDatos = BaseDePruebas.conectar();
        BaseDePruebas.vaciar(baseDatos);
        BaseDePruebas.crearAdmin(baseDatos, "barbero", "clave-del-barbero");

        RelojMovible reloj = new RelojMovible(LocalDateTime.of(2026, 10, 5, 10, 0));
        servidor = new ServidorApi();
        // Puerto 0: el sistema operativo elige uno libre. Así el test no choca con la API si está arrancada.
        servidor.iniciar("127.0.0.1", 0, Aplicacion.montar(baseDatos, SECRETO, reloj.comoReloj()));
        base = "http://127.0.0.1:" + servidor.puerto();
    }

    @AfterEach
    void parar() {
        servidor.parar();
    }

    @Test
    void saludRespondeSinSesion() throws Exception {
        Respuesta respuesta = pedir("GET", "/api/salud", null, null);
        assertEquals(200, respuesta.estado());
        assertEquals("ok", respuesta.json().getString("base_de_datos"));
        assertEquals("no-store", respuesta.http().headers().firstValue("Cache-Control").orElse(""));
    }

    @Test
    void rutasQueNoExistenYMetodosNoPermitidos() throws Exception {
        Respuesta noExiste = pedir("GET", "/api/no-existe", null, null);
        assertEquals(404, noExiste.estado());
        assertEquals("ruta_no_encontrada", noExiste.error());

        Respuesta metodo = pedir("DELETE", "/api/salud", null, null);
        assertEquals(405, metodo.estado());
        assertEquals("GET", metodo.http().headers().firstValue("Allow").orElse(""));

        // El 404/405 va antes que el token: una ruta inventada no dice "no autenticado".
        assertEquals(404, pedir("GET", "/api/admin/no-existe", null, null).estado());
    }

    @Test
    void sinTokenOConTokenFalsoEs401() throws Exception {
        Respuesta sinToken = pedir("GET", "/api/yo", null, null);
        assertEquals(401, sinToken.estado());
        assertEquals("no_autenticado", sinToken.error());
        assertEquals("Bearer", sinToken.http().headers().firstValue("WWW-Authenticate").orElse(""));

        Respuesta tokenFalso = pedir("GET", "/api/yo", "esto.no.vale", null);
        assertEquals(401, tokenFalso.estado());
        assertEquals("token_invalido", tokenFalso.error());
    }

    @Test
    void cuerpoRotoOEnorme() throws Exception {
        assertEquals("json_invalido", pedir("POST", "/api/auth/login", null, "{no es json").error());
        assertEquals("json_invalido", pedir("POST", "/api/auth/login", null, "").error());

        String enorme = "{\"usuario\":\"" + "a".repeat(20_000) + "\"}";
        Respuesta grande = pedir("POST", "/api/auth/login", null, enorme);
        assertEquals(400, grande.estado());
        assertEquals("cuerpo_demasiado_grande", grande.error());
    }

    @Test
    void alQuintoLoginFallidoSeBloqueaCon429() throws Exception {
        String malo = "{\"usuario\":\"barbero\",\"password\":\"no-es-esta\"}";
        for (int i = 0; i < 5; i++) {
            assertEquals(401, pedir("POST", "/api/auth/login", null, malo).estado());
        }
        Respuesta bloqueado = pedir("POST", "/api/auth/login", null, malo);
        assertEquals(429, bloqueado.estado());
        assertEquals("demasiados_intentos", bloqueado.error());
        assertTrue(bloqueado.http().headers().firstValue("Retry-After").isPresent());

        // Bloqueado aunque ahora ponga la contraseña buena: hay que esperar.
        String bueno = "{\"usuario\":\"barbero\",\"password\":\"clave-del-barbero\"}";
        assertEquals(429, pedir("POST", "/api/auth/login", null, bueno).estado());
    }

    /**
     * El camino completo: el barbero invita, el cliente se registra y espera, el barbero lo
     * aprueba, el cliente pide cita y el barbero la confirma.
     */
    @Test
    void recorridoCompletoDeUnaCita() throws Exception {
        String admin = login("barbero", "clave-del-barbero");

        // El barbero pone horario el martes y genera una invitación.
        Respuesta horario = pedir("PUT", "/api/admin/horarios", admin,
                "{\"horarios\":[{\"dia_semana\":2,\"hora_inicio\":\"16:00\",\"hora_fin\":\"20:00\"}]}");
        assertEquals(200, horario.estado(), horario.texto());
        assertEquals(2, horario.json().getJSONArray("horarios").getJSONObject(0).getInt("cupo"));

        Respuesta invitacion = pedir("POST", "/api/admin/invitaciones", admin, "{\"nota\":\"Marcos\"}");
        assertEquals(201, invitacion.estado(), invitacion.texto());
        String codigo = invitacion.json().getString("codigo");

        // El cliente se registra y sale pendiente.
        Respuesta registro = pedir("POST", "/api/auth/registro", null, new JSONObject()
                .put("codigo", codigo).put("usuario", "Marcos").put("telefono", "611 22 23 33")
                .put("password", "una-clave-larga").toString());
        assertEquals(201, registro.estado(), registro.texto());
        String marcos = registro.json().getString("token");
        long marcosId = registro.json().getJSONObject("usuario").getLong("id");
        assertEquals("pendiente", pedir("GET", "/api/yo", marcos, null).json().getString("estado"));

        // Pendiente: no puede ver los días. Y en una ruta de admin, el estado se mira antes que el rol.
        assertEquals("no_aprobado", pedir("GET", "/api/dias", marcos, null).error());
        assertEquals("no_aprobado", pedir("GET", "/api/admin/clientes", marcos, null).error());

        // El barbero lo aprueba. El precio sale con dos decimales.
        Respuesta aprobado = pedir("POST", "/api/admin/clientes/" + marcosId + "/aprobar", admin, null);
        assertEquals(200, aprobado.estado(), aprobado.texto());
        assertTrue(aprobado.texto().contains("\"precio_corte\":15.00"), aprobado.texto());

        // Ya aprobado, en una ruta de admin lo que falla es el rol.
        assertEquals("sin_permiso", pedir("GET", "/api/admin/clientes", marcos, null).error());

        // Ve los 14 días del horizonte, con el martes libre.
        JSONObject dias = pedir("GET", "/api/dias", marcos, null).json();
        JSONArray lista = dias.getJSONArray("dias");
        assertEquals(14, lista.length());
        assertEquals("2026-10-06", lista.getJSONObject(1).getString("fecha"));
        assertEquals("libre", lista.getJSONObject(1).getString("estado"));

        JSONArray huecos = pedir("GET", "/api/huecos?fecha=2026-10-06", marcos, null).json().getJSONArray("huecos");
        assertEquals(4, huecos.length());

        // Pide las 17:00.
        Respuesta cita = pedir("POST", "/api/citas", marcos, "{\"fecha\":\"2026-10-06\",\"hora_pedida\":\"17:00\"}");
        assertEquals(201, cita.estado(), cita.texto());
        assertEquals("pendiente", cita.json().getString("estado"));
        assertTrue(cita.json().isNull("hora_final"));
        // Al cliente no se le enseña el precio (RN-18).
        assertTrue(!cita.json().has("precio"));
        long citaId = cita.json().getLong("id");

        // El barbero la ve en solicitudes y la confirma a las 17:30.
        JSONArray pendientes = pedir("GET", "/api/admin/citas?estado=pendiente", admin, null).json().getJSONArray("citas");
        assertEquals(1, pendientes.length());
        assertEquals("marcos", pendientes.getJSONObject(0).getJSONObject("cliente").getString("usuario"));

        Respuesta confirmada = pedir("POST", "/api/admin/citas/" + citaId + "/confirmar", admin, "{\"hora_final\":\"17:30\"}");
        assertEquals(200, confirmada.estado(), confirmada.texto());
        assertEquals("confirmada", confirmada.json().getString("estado"));
        assertEquals("17:30", confirmada.json().getString("hora_final"));

        JSONArray misCitas = pedir("GET", "/api/mis-citas", marcos, null).json().getJSONArray("citas");
        assertEquals("confirmada", misCitas.getJSONObject(0).getString("estado"));

        // Errores de formato en la ruta y en los parámetros.
        assertEquals("datos_invalidos", pedir("POST", "/api/admin/citas/abc/rechazar", admin, null).error());
        assertEquals("datos_invalidos", pedir("GET", "/api/admin/citas?estado=inventado", admin, null).error());
        assertEquals("datos_invalidos", pedir("GET", "/api/huecos?fecha=2026-02-30", marcos, null).error());
        assertEquals("no_encontrado", pedir("POST", "/api/admin/citas/999999/rechazar", admin, null).error());
    }

    @Test
    void excepcionesSeCreanYSeBorran() throws Exception {
        String admin = login("barbero", "clave-del-barbero");

        Respuesta creadas = pedir("POST", "/api/admin/excepciones", admin,
                "{\"fechas\":[\"2026-10-06\",\"2026-10-07\"],\"tipo\":\"cerrado\",\"nota\":\"Vacaciones\"}");
        assertEquals(201, creadas.estado(), creadas.texto());
        JSONArray excepciones = creadas.json().getJSONArray("excepciones");
        assertEquals(2, excepciones.length());
        assertTrue(excepciones.getJSONObject(0).isNull("hora_inicio"));

        long id = excepciones.getJSONObject(0).getLong("id");
        Respuesta borrada = pedir("DELETE", "/api/admin/excepciones/" + id, admin, null);
        assertEquals(204, borrada.estado());
        assertEquals(404, pedir("DELETE", "/api/admin/excepciones/" + id, admin, null).estado());

        assertEquals(1, pedir("GET", "/api/admin/excepciones", admin, null).json().getJSONArray("excepciones").length());

        // Un día cerrado no lleva horas.
        Respuesta mal = pedir("POST", "/api/admin/excepciones", admin,
                "{\"fechas\":[\"2026-10-08\"],\"tipo\":\"cerrado\",\"hora_inicio\":\"10:00\"}");
        assertEquals("datos_invalidos", mal.error());
    }

    // ---------------------------------------------------------------------------------------

    private String login(String usuario, String contrasena) throws Exception {
        Respuesta respuesta = pedir("POST", "/api/auth/login", null,
                new JSONObject().put("usuario", usuario).put("password", contrasena).toString());
        assertEquals(200, respuesta.estado(), respuesta.texto());
        return respuesta.json().getString("token");
    }

    private Respuesta pedir(String metodo, String ruta, String token, String cuerpo) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(base + ruta))
                .method(metodo, cuerpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cuerpo != null) {
            peticion.header("Content-Type", "application/json");
        }
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> respuesta = cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
        return new Respuesta(respuesta.statusCode(), respuesta.body(), respuesta);
    }
}
