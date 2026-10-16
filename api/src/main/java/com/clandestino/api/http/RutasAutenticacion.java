package com.clandestino.api.http;

import com.clandestino.api.modelo.Usuario;
import com.clandestino.api.repositorio.BaseDatos;
import com.clandestino.api.seguridad.LimitadorIntentos;
import com.clandestino.api.servicio.ErrorNegocio;
import com.clandestino.api.servicio.ServicioUsuarios;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Rutas públicas y de la propia cuenta: salud, registro, login y "quién soy".
 *
 * El registro y el login pasan por el limitador de intentos: cada respuesta que no sea un
 * éxito cuenta como un fallo, y con 5 fallos en 15 minutos se responde 429.
 */
public class RutasAutenticacion {

    private final ServicioUsuarios servicioUsuarios;
    private final LimitadorIntentos limitador;
    private final BaseDatos baseDatos;

    public RutasAutenticacion(ServicioUsuarios servicioUsuarios, LimitadorIntentos limitador, BaseDatos baseDatos) {
        this.servicioUsuarios = servicioUsuarios;
        this.limitador = limitador;
        this.baseDatos = baseDatos;
    }

    public void registrar(Enrutador enrutador) {
        enrutador.registrar("GET", "/api/salud", Acceso.PUBLICO, this::salud);
        enrutador.registrar("POST", "/api/auth/registro", Acceso.PUBLICO, this::registro);
        enrutador.registrar("POST", "/api/auth/login", Acceso.PUBLICO, this::login);
        enrutador.registrar("GET", "/api/yo", Acceso.CUALQUIER_SESION, this::yo);
    }

    /** GET /api/salud: la API está viva y llega a MySQL (lo usa el anexo de instalación). */
    private void salud(HttpExchange intercambio, Usuario usuario) throws IOException {
        if (!baseDatos.responde()) {
            throw new ErrorNegocio("base_de_datos_no_disponible", 503, "La base de datos no responde.");
        }
        ConversorJson.responder(intercambio, 200, new JSONObject().put("estado", "ok").put("base_de_datos", "ok"));
    }

    /** POST /api/auth/registro (UC01). En el registro la clave del limitador es solo la IP. */
    private void registro(HttpExchange intercambio, Usuario usuario) throws IOException {
        String clave = "registro|" + Enrutador.ip(intercambio);
        comprobarLimitador(clave);
        try {
            JSONObject cuerpo = ConversorJson.leerCuerpo(intercambio);
            ServicioUsuarios.Sesion sesion = servicioUsuarios.registrar(
                    ConversorJson.texto(cuerpo, "codigo"),
                    ConversorJson.texto(cuerpo, "usuario"),
                    ConversorJson.texto(cuerpo, "telefono"),
                    ConversorJson.texto(cuerpo, "password"));
            ConversorJson.responder(intercambio, 201, sesionEnJson(sesion));
        } catch (ErrorNegocio e) {
            limitador.registrarFallo(clave);
            throw e;
        }
    }

    /**
     * POST /api/auth/login (UC02). Aquí la clave del limitador es la IP más el @usuario. Solo la
     * IP no valdría: desde el emulador todas las peticiones llegan desde la misma dirección, y
     * cinco fallos de un cliente bloquearían a todos los demás.
     */
    private void login(HttpExchange intercambio, Usuario usuario) throws IOException {
        // Hay que leer el cuerpo antes del limitador para saber el usuario. Si el cuerpo está
        // roto, el error se lanza después de mirar el limitador, para respetar el orden 429 -> 400.
        JSONObject cuerpo = null;
        ErrorNegocio errorDelCuerpo = null;
        try {
            cuerpo = ConversorJson.leerCuerpo(intercambio);
        } catch (ErrorNegocio e) {
            errorDelCuerpo = e;
        }
        String nombre = cuerpo == null ? "" : ServicioUsuarios.normalizarUsuario(cuerpo.optString("usuario"));
        String clave = "login|" + Enrutador.ip(intercambio) + "|" + nombre;
        comprobarLimitador(clave);

        try {
            if (errorDelCuerpo != null) {
                throw errorDelCuerpo;
            }
            ServicioUsuarios.Sesion sesion = servicioUsuarios.iniciarSesion(
                    ConversorJson.texto(cuerpo, "usuario"),
                    ConversorJson.texto(cuerpo, "password"));
            limitador.reiniciar(clave);
            ConversorJson.responder(intercambio, 200, sesionEnJson(sesion));
        } catch (ErrorNegocio e) {
            limitador.registrarFallo(clave);
            throw e;
        }
    }

    /** GET /api/yo: la cuenta del token. La pantalla de espera (A3) mira aquí el estado. */
    private void yo(HttpExchange intercambio, Usuario usuario) throws IOException {
        ConversorJson.responder(intercambio, 200, ConversorJson.cuenta(usuario));
    }

    private void comprobarLimitador(String clave) {
        if (!limitador.permitir(clave)) {
            throw new ErrorNegocio("demasiados_intentos", 429, "Demasiados intentos. Espera un rato y vuelve a probar.")
                    .conCabecera("Retry-After", String.valueOf(limitador.segundosDeEspera(clave)));
        }
    }

    private static JSONObject sesionEnJson(ServicioUsuarios.Sesion sesion) {
        return new JSONObject()
                .put("token", sesion.token())
                .put("usuario", ConversorJson.cuenta(sesion.usuario()));
    }
}
