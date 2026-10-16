package com.clandestino.api.http;

import com.clandestino.api.modelo.Cita;
import com.clandestino.api.modelo.EstadoCita;
import com.clandestino.api.modelo.EstadoUsuario;
import com.clandestino.api.modelo.ExcepcionDia;
import com.clandestino.api.modelo.HorarioSemanal;
import com.clandestino.api.modelo.Invitacion;
import com.clandestino.api.modelo.TipoExcepcion;
import com.clandestino.api.modelo.Usuario;
import com.clandestino.api.servicio.ErrorNegocio;
import com.clandestino.api.servicio.Reloj;
import com.clandestino.api.servicio.ServicioCitas;
import com.clandestino.api.servicio.ServicioHorarios;
import com.clandestino.api.servicio.ServicioUsuarios;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Rutas del barbero: solicitudes y agenda (UC08, UC09), horarios y excepciones (UC10),
 * clientes (UC07) e invitaciones (UC06). El Enrutador ya ha comprobado que es el admin.
 */
public class RutasAdmin {

    private final ServicioCitas servicioCitas;
    private final ServicioHorarios servicioHorarios;
    private final ServicioUsuarios servicioUsuarios;
    private final Reloj reloj;

    public RutasAdmin(ServicioCitas servicioCitas, ServicioHorarios servicioHorarios,
                      ServicioUsuarios servicioUsuarios, Reloj reloj) {
        this.servicioCitas = servicioCitas;
        this.servicioHorarios = servicioHorarios;
        this.servicioUsuarios = servicioUsuarios;
        this.reloj = reloj;
    }

    public void registrar(Enrutador enrutador) {
        enrutador.registrar("GET", "/api/admin/citas", Acceso.ADMIN, this::listarCitas);
        enrutador.registrar("POST", "/api/admin/citas/{id}/confirmar", Acceso.ADMIN, this::confirmar);
        enrutador.registrar("POST", "/api/admin/citas/{id}/rechazar", Acceso.ADMIN, this::rechazar);
        enrutador.registrar("POST", "/api/admin/citas/{id}/cancelar", Acceso.ADMIN, this::cancelar);
        enrutador.registrar("POST", "/api/admin/citas/{id}/asistencia", Acceso.ADMIN, this::asistencia);

        enrutador.registrar("GET", "/api/admin/horarios", Acceso.ADMIN, this::horarios);
        enrutador.registrar("PUT", "/api/admin/horarios", Acceso.ADMIN, this::guardarHorarios);
        enrutador.registrar("GET", "/api/admin/excepciones", Acceso.ADMIN, this::excepciones);
        enrutador.registrar("POST", "/api/admin/excepciones", Acceso.ADMIN, this::crearExcepciones);
        enrutador.registrar("DELETE", "/api/admin/excepciones/{id}", Acceso.ADMIN, this::borrarExcepcion);

        enrutador.registrar("GET", "/api/admin/clientes", Acceso.ADMIN, this::clientes);
        enrutador.registrar("POST", "/api/admin/clientes/{id}/aprobar", Acceso.ADMIN, this::aprobar);
        enrutador.registrar("POST", "/api/admin/clientes/{id}/bloquear", Acceso.ADMIN, this::bloquear);

        enrutador.registrar("GET", "/api/admin/invitaciones", Acceso.ADMIN, this::invitaciones);
        enrutador.registrar("POST", "/api/admin/invitaciones", Acceso.ADMIN, this::generarInvitacion);
    }

    // ---------------------------------------------------------------------------------------
    // Citas
    // ---------------------------------------------------------------------------------------

    /**
     * GET /api/admin/citas?estado&desde&hasta. D1 pide estado=pendiente; D2 pide
     * estado=confirmada con las fechas de la semana o del mes que se ve.
     */
    private void listarCitas(HttpExchange intercambio, Usuario admin) throws IOException {
        List<EstadoCita> estados = estadosDeLaUrl(Enrutador.parametro(intercambio, "estado"));
        LocalDate desde = RutasCliente.fechaOpcional(intercambio, "desde");
        LocalDate hasta = RutasCliente.fechaOpcional(intercambio, "hasta");

        JSONArray citas = new JSONArray();
        for (Cita cita : servicioCitas.listar(estados, desde, hasta)) {
            citas.put(ConversorJson.citaParaAdmin(cita));
        }
        ConversorJson.responder(intercambio, 200, new JSONObject().put("citas", citas));
    }

    /** "pendiente,confirmada" pasa a la lista [PENDIENTE, CONFIRMADA]. Un estado que no existe es un 400. */
    private static List<EstadoCita> estadosDeLaUrl(String texto) {
        List<EstadoCita> estados = new ArrayList<>();
        if (texto == null) {
            return estados;
        }
        for (String trozo : texto.split(",")) {
            try {
                estados.add(EstadoCita.desdeTexto(trozo.strip()));
            } catch (IllegalArgumentException e) {
                throw ErrorNegocio.datosInvalidos("El estado '" + trozo.strip() + "' no existe.");
            }
        }
        return estados;
    }

    /** POST /api/admin/citas/{id}/confirmar con { "hora_final": "17:30" } opcional. */
    private void confirmar(HttpExchange intercambio, Usuario admin) throws IOException {
        JSONObject cuerpo = ConversorJson.leerCuerpoOpcional(intercambio);
        var horaFinal = ConversorJson.horaOpcional(cuerpo, "hora_final");
        long citaId = Enrutador.idDeRuta(intercambio);
        responderCita(intercambio, servicioCitas.confirmar(citaId, horaFinal));
    }

    private void rechazar(HttpExchange intercambio, Usuario admin) throws IOException {
        responderCita(intercambio, servicioCitas.rechazar(Enrutador.idDeRuta(intercambio)));
    }

    private void cancelar(HttpExchange intercambio, Usuario admin) throws IOException {
        responderCita(intercambio, servicioCitas.cancelarPorAdmin(Enrutador.idDeRuta(intercambio)));
    }

    /** POST /api/admin/citas/{id}/asistencia con { "resultado": "hecha" | "no_asistio", "propina": 2.5 }. */
    private void asistencia(HttpExchange intercambio, Usuario admin) throws IOException {
        JSONObject cuerpo = ConversorJson.leerCuerpo(intercambio);
        String resultado = ConversorJson.texto(cuerpo, "resultado");
        if (!resultado.equals("hecha") && !resultado.equals("no_asistio")) {
            throw ErrorNegocio.datosInvalidos("'resultado' tiene que ser 'hecha' o 'no_asistio'.");
        }
        boolean vino = resultado.equals("hecha");
        // Con no_asistio la propina se ignora (RN-19), aunque venga.
        BigDecimal propina = vino ? ConversorJson.decimalOpcional(cuerpo, "propina") : null;
        long citaId = Enrutador.idDeRuta(intercambio);
        responderCita(intercambio, servicioCitas.marcarAsistencia(citaId, vino, propina));
    }

    private static void responderCita(HttpExchange intercambio, Cita cita) throws IOException {
        ConversorJson.responder(intercambio, 200, ConversorJson.citaParaAdmin(cita));
    }

    // ---------------------------------------------------------------------------------------
    // Horarios y excepciones
    // ---------------------------------------------------------------------------------------

    private void horarios(HttpExchange intercambio, Usuario admin) throws IOException {
        responderHorarios(intercambio, servicioHorarios.horarios());
    }

    /** PUT /api/admin/horarios con { "horarios": [ { "dia_semana": 5, ... } ] }. */
    private void guardarHorarios(HttpExchange intercambio, Usuario admin) throws IOException {
        JSONObject cuerpo = ConversorJson.leerCuerpo(intercambio);
        JSONArray lista = cuerpo.optJSONArray("horarios");
        if (lista == null) {
            throw ErrorNegocio.datosInvalidos("Falta la lista 'horarios'.");
        }
        List<HorarioSemanal> nuevos = new ArrayList<>();
        for (int i = 0; i < lista.length(); i++) {
            JSONObject json = lista.optJSONObject(i);
            if (json == null) {
                throw ErrorNegocio.datosInvalidos("Cada horario tiene que ser un objeto.");
            }
            nuevos.add(new HorarioSemanal(null,
                    ConversorJson.entero(json, "dia_semana", null),
                    ConversorJson.hora(json, "hora_inicio"),
                    ConversorJson.hora(json, "hora_fin"),
                    ConversorJson.entero(json, "cupo", 2),
                    ConversorJson.booleano(json, "activo", true)));
        }
        responderHorarios(intercambio, servicioHorarios.guardarHorarios(nuevos));
    }

    private static void responderHorarios(HttpExchange intercambio, List<HorarioSemanal> horarios)
            throws IOException {
        JSONArray lista = new JSONArray();
        for (HorarioSemanal horario : horarios) {
            lista.put(ConversorJson.horario(horario));
        }
        ConversorJson.responder(intercambio, 200, new JSONObject().put("horarios", lista));
    }

    /** GET /api/admin/excepciones?desde (por defecto, hoy). */
    private void excepciones(HttpExchange intercambio, Usuario admin) throws IOException {
        LocalDate desde = RutasCliente.fechaOpcional(intercambio, "desde");
        responderExcepciones(intercambio, 200, servicioHorarios.excepciones(desde));
    }

    /**
     * POST /api/admin/excepciones con { "fechas": [...], "tipo": "cerrado" | "abierto",
     * "hora_inicio", "hora_fin", "cupo", "nota" }. Crea la misma excepción en cada fecha.
     */
    private void crearExcepciones(HttpExchange intercambio, Usuario admin) throws IOException {
        JSONObject cuerpo = ConversorJson.leerCuerpo(intercambio);
        JSONArray listaFechas = cuerpo.optJSONArray("fechas");
        if (listaFechas == null) {
            throw ErrorNegocio.datosInvalidos("Falta la lista 'fechas'.");
        }
        List<LocalDate> fechas = new ArrayList<>();
        for (int i = 0; i < listaFechas.length(); i++) {
            Object valor = listaFechas.get(i);
            if (!(valor instanceof String texto)) {
                throw ErrorNegocio.datosInvalidos("Cada fecha tiene que ser un texto aaaa-mm-dd.");
            }
            fechas.add(ConversorJson.fechaDeTexto(texto, "fechas"));
        }

        TipoExcepcion tipo;
        try {
            tipo = TipoExcepcion.desdeTexto(ConversorJson.texto(cuerpo, "tipo"));
        } catch (IllegalArgumentException e) {
            throw ErrorNegocio.datosInvalidos("'tipo' tiene que ser 'cerrado' o 'abierto'.");
        }
        Integer cupo = cuerpo.has("cupo") && !cuerpo.isNull("cupo") ? ConversorJson.entero(cuerpo, "cupo", null) : null;
        String nota = ConversorJson.textoOpcional(cuerpo, "nota");
        ExcepcionDia plantilla = new ExcepcionDia(null, null, tipo,
                ConversorJson.horaOpcional(cuerpo, "hora_inicio"),
                ConversorJson.horaOpcional(cuerpo, "hora_fin"),
                cupo,
                nota == null || nota.isBlank() ? null : nota.strip());

        responderExcepciones(intercambio, 201, servicioHorarios.crearExcepciones(fechas, plantilla));
    }

    private void borrarExcepcion(HttpExchange intercambio, Usuario admin) throws IOException {
        long id = Enrutador.idDeRuta(intercambio);
        if (id > Integer.MAX_VALUE) {
            throw ErrorNegocio.noEncontrado("Esa excepción no existe.");
        }
        servicioHorarios.borrarExcepcion((int) id);
        ConversorJson.responderSinContenido(intercambio);
    }

    private static void responderExcepciones(HttpExchange intercambio, int estado, List<ExcepcionDia> excepciones)
            throws IOException {
        JSONArray lista = new JSONArray();
        for (ExcepcionDia excepcion : excepciones) {
            lista.put(ConversorJson.excepcion(excepcion));
        }
        ConversorJson.responder(intercambio, estado, new JSONObject().put("excepciones", lista));
    }

    // ---------------------------------------------------------------------------------------
    // Clientes e invitaciones
    // ---------------------------------------------------------------------------------------

    /** GET /api/admin/clientes?estado (pendiente, aprobado o bloqueado; sin él, todos). */
    private void clientes(HttpExchange intercambio, Usuario admin) throws IOException {
        String texto = Enrutador.parametro(intercambio, "estado");
        EstadoUsuario estado = null;
        if (texto != null) {
            try {
                estado = EstadoUsuario.desdeTexto(texto);
            } catch (IllegalArgumentException e) {
                throw ErrorNegocio.datosInvalidos("El estado '" + texto + "' no existe.");
            }
        }
        JSONArray lista = new JSONArray();
        for (Usuario cliente : servicioUsuarios.clientes(estado)) {
            lista.put(ConversorJson.cliente(cliente));
        }
        ConversorJson.responder(intercambio, 200, new JSONObject().put("clientes", lista));
    }

    private void aprobar(HttpExchange intercambio, Usuario admin) throws IOException {
        Usuario cliente = servicioUsuarios.aprobar(Enrutador.idDeRuta(intercambio));
        ConversorJson.responder(intercambio, 200, ConversorJson.cliente(cliente));
    }

    private void bloquear(HttpExchange intercambio, Usuario admin) throws IOException {
        Usuario cliente = servicioUsuarios.bloquear(Enrutador.idDeRuta(intercambio));
        ConversorJson.responder(intercambio, 200, ConversorJson.cliente(cliente));
    }

    private void invitaciones(HttpExchange intercambio, Usuario admin) throws IOException {
        LocalDateTime ahora = reloj.ahora();
        JSONArray lista = new JSONArray();
        for (Invitacion invitacion : servicioUsuarios.invitaciones()) {
            lista.put(ConversorJson.invitacion(invitacion, ahora));
        }
        ConversorJson.responder(intercambio, 200, new JSONObject().put("invitaciones", lista));
    }

    /** POST /api/admin/invitaciones con { "nota": "..." } opcional (y el cuerpo también). */
    private void generarInvitacion(HttpExchange intercambio, Usuario admin) throws IOException {
        JSONObject cuerpo = ConversorJson.leerCuerpoOpcional(intercambio);
        Invitacion nueva = servicioUsuarios.generarInvitacion(admin.id(), ConversorJson.textoOpcional(cuerpo, "nota"));
        ConversorJson.responder(intercambio, 201, ConversorJson.invitacion(nueva, reloj.ahora()));
    }
}
