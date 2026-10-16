package com.clandestino.api.http;

import com.clandestino.api.modelo.Rol;
import com.clandestino.api.modelo.Usuario;
import com.clandestino.api.seguridad.FiltroAutenticacion;
import com.clandestino.api.servicio.ErrorNegocio;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Recibe todas las peticiones y decide qué manejador las atiende.
 *
 * HttpServer solo sabe repartir por prefijo ("/api/..."), no entiende rutas con trozos
 * variables como /api/citas/{id}/cancelar. Por eso registramos un único contexto "/" y aquí
 * hacemos el reparto a mano: partimos la ruta por "/" y la comparamos trozo a trozo.
 *
 * El orden de cada petición es el de api-contrato.md §6:
 *   1. ruta y método (404, 405)
 *   2. limitador de intentos (solo login y registro; lo hacen sus propios manejadores)
 *   3. token (401)
 *   4. usuario en la base de datos, con su rol y su estado de verdad (401)
 *   5. estado aprobado y rol correcto (403)
 *   6. cuerpo y formato de los campos (400), ya en el manejador
 *   7. reglas de negocio, en el servicio
 * Cualquier ErrorNegocio que salga de ahí se convierte en la respuesta de error del contrato,
 * y cualquier otra excepción en un 500 genérico (el detalle se queda en el registro).
 */
public class Enrutador implements HttpHandler {

    private static final Logger registro = Logger.getLogger(Enrutador.class.getName());

    // Nombre del atributo del intercambio en el que dejamos los trozos variables de la ruta.
    private static final String ATRIBUTO_PARAMETROS = "parametrosRuta";

    /** Una ruta registrada: método, patrón ya partido en trozos, quién puede usarla y qué hace. */
    private record Ruta(String metodo, String[] trozos, Acceso acceso, Manejador manejador) {
    }

    private final List<Ruta> rutas = new ArrayList<>();
    private final FiltroAutenticacion filtro;

    public Enrutador(FiltroAutenticacion filtro) {
        this.filtro = filtro;
    }

    /**
     * Añade una ruta. En el patrón, un trozo entre llaves es variable:
     * "/api/citas/{id}/cancelar" encaja con "/api/citas/12/cancelar".
     */
    public void registrar(String metodo, String patron, Acceso acceso, Manejador manejador) {
        rutas.add(new Ruta(metodo, partir(patron), acceso, manejador));
    }

    @Override
    public void handle(HttpExchange intercambio) {
        despachar(intercambio);
    }

    public void despachar(HttpExchange intercambio) {
        long inicio = System.nanoTime();
        String metodo = intercambio.getRequestMethod();
        String ruta = intercambio.getRequestURI().getPath();
        try {
            Ruta elegida = elegirRuta(metodo, ruta, intercambio);
            Usuario usuario = comprobarAcceso(elegida.acceso(), intercambio);
            elegida.manejador().atender(intercambio, usuario);
        } catch (ErrorNegocio error) {
            intentarResponderError(intercambio, error);
        } catch (Exception e) {
            // Un fallo que no esperábamos. Al cliente no le damos detalles (podrían revelar cómo
            // está hecho el servidor); la traza completa va al registro.
            registro.log(Level.SEVERE, "Error inesperado en " + metodo + " " + ruta, e);
            intentarResponderError(intercambio,
                    new ErrorNegocio("error_interno", 500, "Ha fallado algo en el servidor. Inténtalo más tarde."));
        } finally {
            intercambio.close();
            long milisegundos = (System.nanoTime() - inicio) / 1_000_000;
            // Solo método, ruta, código y tiempo. Nunca cuerpos, tokens ni teléfonos.
            registro.info(metodo + " " + ruta + " -> " + intercambio.getResponseCode() + " (" + milisegundos + " ms)");
        }
    }

    /** Busca la ruta. Si la ruta existe pero no con ese método, 405 con la cabecera Allow. */
    private Ruta elegirRuta(String metodo, String ruta, HttpExchange intercambio) {
        String[] trozos = partir(ruta);
        Set<String> metodosDeEstaRuta = new LinkedHashSet<>();
        for (Ruta candidata : rutas) {
            Map<String, String> parametros = encajar(candidata.trozos(), trozos);
            if (parametros == null) {
                continue;
            }
            if (candidata.metodo().equals(metodo)) {
                intercambio.setAttribute(ATRIBUTO_PARAMETROS, parametros);
                return candidata;
            }
            metodosDeEstaRuta.add(candidata.metodo());
        }
        if (metodosDeEstaRuta.isEmpty()) {
            throw new ErrorNegocio("ruta_no_encontrada", 404, "Esa ruta no existe.");
        }
        throw new ErrorNegocio("metodo_no_permitido", 405, "Esa ruta no admite el método " + metodo + ".")
                .conCabecera("Allow", String.join(", ", metodosDeEstaRuta));
    }

    /**
     * Compara la ruta pedida con el patrón trozo a trozo. Devuelve los trozos variables
     * ({id} -> "12") si encaja, o null si no.
     */
    private static Map<String, String> encajar(String[] patron, String[] ruta) {
        if (patron.length != ruta.length) {
            return null;
        }
        Map<String, String> parametros = new HashMap<>();
        for (int i = 0; i < patron.length; i++) {
            if (patron[i].startsWith("{") && patron[i].endsWith("}")) {
                parametros.put(patron[i].substring(1, patron[i].length() - 1), ruta[i]);
            } else if (!patron[i].equals(ruta[i])) {
                return null;
            }
        }
        return parametros;
    }

    private static String[] partir(String ruta) {
        // "/api/citas/12/" y "/api/citas/12" se tratan igual.
        String sinBarras = ruta.replaceAll("^/+|/+$", "");
        return sinBarras.isEmpty() ? new String[0] : sinBarras.split("/");
    }

    /**
     * Pasos 3 a 5: quién es y si puede usar esta ruta. Primero el estado de la cuenta y después
     * el rol (RN-09, pasos 3 y 4). Devuelve null en las rutas públicas.
     */
    private Usuario comprobarAcceso(Acceso acceso, HttpExchange intercambio) {
        if (acceso == Acceso.PUBLICO) {
            return null;
        }
        Usuario usuario = filtro.autenticar(intercambio.getRequestHeaders().getFirst("Authorization"));
        if (acceso == Acceso.CUALQUIER_SESION) {
            return usuario;
        }
        if (!usuario.estaAprobado()) {
            throw new ErrorNegocio("no_aprobado", 403, "Tu cuenta todavía no está aprobada o está bloqueada.");
        }
        Rol rolNecesario = acceso == Acceso.ADMIN ? Rol.ADMIN : Rol.CLIENTE;
        if (usuario.rol() != rolNecesario) {
            throw new ErrorNegocio("sin_permiso", 403, "No tienes permiso para hacer esto.");
        }
        return usuario;
    }

    private static void intentarResponderError(HttpExchange intercambio, ErrorNegocio error) {
        try {
            ConversorJson.responderError(intercambio, error);
        } catch (IOException | IllegalStateException e) {
            // Pasa si ya se habían enviado las cabeceras o si el cliente cortó la conexión.
            // No se puede hacer nada más; queda en el registro con el código que se envió.
        }
    }

    // ---------------------------------------------------------------------------------------
    // Ayudas para los manejadores
    // ---------------------------------------------------------------------------------------

    /** El {id} de la ruta como número. Si no es un número, datos_invalidos (400). */
    public static long idDeRuta(HttpExchange intercambio) {
        @SuppressWarnings("unchecked")
        Map<String, String> parametros = (Map<String, String>) intercambio.getAttribute(ATRIBUTO_PARAMETROS);
        String id = parametros == null ? null : parametros.get("id");
        try {
            long valor = Long.parseLong(id);
            if (valor <= 0) {
                throw new NumberFormatException();
            }
            return valor;
        } catch (NumberFormatException e) {
            throw ErrorNegocio.datosInvalidos("El id de la ruta tiene que ser un número.");
        }
    }

    /** Un parámetro de la URL (?fecha=2026-10-06), o null si no viene o viene vacío. */
    public static String parametro(HttpExchange intercambio, String nombre) {
        String consulta = intercambio.getRequestURI().getRawQuery();
        if (consulta == null) {
            return null;
        }
        try {
            for (String par : consulta.split("&")) {
                int igual = par.indexOf('=');
                String clave = URLDecoder.decode(igual >= 0 ? par.substring(0, igual) : par, StandardCharsets.UTF_8);
                if (clave.equals(nombre)) {
                    String valor = igual >= 0 ? URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8) : "";
                    return valor.isBlank() ? null : valor;
                }
            }
            return null;
        } catch (IllegalArgumentException e) {
            // URLDecoder se queja de cosas como "%zz". Es un error de quien llama, no nuestro.
            throw ErrorNegocio.datosInvalidos("La dirección tiene caracteres mal codificados.");
        }
    }

    /** La IP de quien hace la petición (para el limitador). Desde el emulador siempre es 127.0.0.1. */
    public static String ip(HttpExchange intercambio) {
        return intercambio.getRemoteAddress().getAddress().getHostAddress();
    }
}
