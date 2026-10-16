package com.clandestino.api.http;

import com.clandestino.api.modelo.Cita;
import com.clandestino.api.modelo.ExcepcionDia;
import com.clandestino.api.modelo.HorarioSemanal;
import com.clandestino.api.modelo.Invitacion;
import com.clandestino.api.modelo.Usuario;
import com.clandestino.api.servicio.ErrorNegocio;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONString;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Map;

/**
 * Todo lo que tiene que ver con JSON: leer el cuerpo de la petición, sacar de él fechas y horas
 * comprobando el formato, escribir la respuesta y pasar los objetos del modelo a JSON con los
 * nombres de campo del contrato (api-contrato.md).
 */
public final class ConversorJson {

    /** 16 KiB de cuerpo como mucho (DP-08). Ninguna petición de la app se acerca a eso. */
    public static final int MAXIMO_BYTES_CUERPO = 16 * 1024;

    // "uuuu" y STRICT hacen que 2026-02-30 se rechace en vez de convertirse en el 2 de marzo.
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter FORMATO_FECHA_HORA =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    private ConversorJson() {
        // Solo métodos estáticos.
    }

    // ---------------------------------------------------------------------------------------
    // Leer la petición
    // ---------------------------------------------------------------------------------------

    /** Lee el cuerpo como un objeto JSON. Si no hay cuerpo, o no es JSON, o es enorme, error 400. */
    public static JSONObject leerCuerpo(HttpExchange intercambio) throws IOException {
        String texto = leerTexto(intercambio);
        if (texto.isBlank()) {
            throw jsonInvalido();
        }
        try {
            return new JSONObject(texto);
        } catch (JSONException e) {
            throw jsonInvalido();
        }
    }

    /** Igual que leerCuerpo, pero si no hay cuerpo devuelve un objeto vacío (rutas con cuerpo opcional). */
    public static JSONObject leerCuerpoOpcional(HttpExchange intercambio) throws IOException {
        String texto = leerTexto(intercambio);
        if (texto.isBlank()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(texto);
        } catch (JSONException e) {
            throw jsonInvalido();
        }
    }

    private static String leerTexto(HttpExchange intercambio) throws IOException {
        try (InputStream entrada = intercambio.getRequestBody()) {
            // Leemos un byte más del máximo: si llega a leerse, el cuerpo es demasiado grande.
            // Así nunca cargamos en memoria más de 16 KiB, mande lo que mande el cliente.
            byte[] bytes = entrada.readNBytes(MAXIMO_BYTES_CUERPO + 1);
            if (bytes.length > MAXIMO_BYTES_CUERPO) {
                throw new ErrorNegocio("cuerpo_demasiado_grande", 400, "La petición es demasiado grande.");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static ErrorNegocio jsonInvalido() {
        return new ErrorNegocio("json_invalido", 400, "El cuerpo de la petición no es un JSON válido.");
    }

    /** Un texto obligatorio. Si falta o no es un texto, datos_invalidos con el nombre del campo. */
    public static String texto(JSONObject json, String campo) {
        Object valor = json.opt(campo);
        if (!(valor instanceof String texto)) {
            throw ErrorNegocio.datosInvalidos("Falta el campo '" + campo + "' o no es un texto.");
        }
        return texto;
    }

    /** Un texto opcional: null si no viene o si viene null. */
    public static String textoOpcional(JSONObject json, String campo) {
        if (!json.has(campo) || json.isNull(campo)) {
            return null;
        }
        return texto(json, campo);
    }

    public static LocalDate fecha(JSONObject json, String campo) {
        return fechaDeTexto(texto(json, campo), campo);
    }

    public static LocalTime hora(JSONObject json, String campo) {
        return horaDeTexto(texto(json, campo), campo);
    }

    public static LocalTime horaOpcional(JSONObject json, String campo) {
        String texto = textoOpcional(json, campo);
        return texto == null ? null : horaDeTexto(texto, campo);
    }

    /** Un número entero. Si falta, devuelve porDefecto (o error si porDefecto es null). */
    public static Integer entero(JSONObject json, String campo, Integer porDefecto) {
        if (!json.has(campo) || json.isNull(campo)) {
            if (porDefecto == null) {
                throw ErrorNegocio.datosInvalidos("Falta el campo '" + campo + "'.");
            }
            return porDefecto;
        }
        Object valor = json.get(campo);
        // OJO: org.json acepta textos como "3" en getInt(). Exigimos que sea un número de verdad
        // y sin decimales, para que "3" o 3.7 no se cuelen como un 3.
        if (!(valor instanceof Integer numero)) {
            throw ErrorNegocio.datosInvalidos("El campo '" + campo + "' tiene que ser un número entero.");
        }
        return numero;
    }

    public static boolean booleano(JSONObject json, String campo, boolean porDefecto) {
        if (!json.has(campo) || json.isNull(campo)) {
            return porDefecto;
        }
        if (!(json.get(campo) instanceof Boolean valor)) {
            throw ErrorNegocio.datosInvalidos("El campo '" + campo + "' tiene que ser true o false.");
        }
        return valor;
    }

    public static BigDecimal decimalOpcional(JSONObject json, String campo) {
        if (!json.has(campo) || json.isNull(campo)) {
            return null;
        }
        if (!(json.get(campo) instanceof Number numero)) {
            throw ErrorNegocio.datosInvalidos("El campo '" + campo + "' tiene que ser un número.");
        }
        // Pasamos por el texto para no arrastrar errores de los double (0.1 + 0.2...).
        return new BigDecimal(numero.toString());
    }

    public static LocalDate fechaDeTexto(String texto, String campo) {
        try {
            return LocalDate.parse(texto, FORMATO_FECHA);
        } catch (DateTimeParseException e) {
            throw ErrorNegocio.datosInvalidos("'" + campo + "' tiene que ser una fecha real con el formato aaaa-mm-dd.");
        }
    }

    public static LocalTime horaDeTexto(String texto, String campo) {
        try {
            return LocalTime.parse(texto, FORMATO_HORA);
        } catch (DateTimeParseException e) {
            throw ErrorNegocio.datosInvalidos("'" + campo + "' tiene que ser una hora con el formato hh:mm.");
        }
    }

    // ---------------------------------------------------------------------------------------
    // Escribir la respuesta
    // ---------------------------------------------------------------------------------------

    public static void responder(HttpExchange intercambio, int estado, JSONObject json) throws IOException {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ponerCabecerasComunes(intercambio);
        intercambio.sendResponseHeaders(estado, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }

    /** 204 No Content: se usa al borrar. El -1 le dice al servidor que no hay cuerpo. */
    public static void responderSinContenido(HttpExchange intercambio) throws IOException {
        ponerCabecerasComunes(intercambio);
        intercambio.sendResponseHeaders(204, -1);
    }

    /** El formato único de error del contrato: { "error": "codigo", "mensaje": "texto" }. */
    public static void responderError(HttpExchange intercambio, ErrorNegocio error) throws IOException {
        for (Map.Entry<String, String> cabecera : error.getCabeceras().entrySet()) {
            intercambio.getResponseHeaders().set(cabecera.getKey(), cabecera.getValue());
        }
        JSONObject cuerpo = new JSONObject()
                .put("error", error.getCodigo())
                .put("mensaje", error.getMessage());
        responder(intercambio, error.getEstadoHttp(), cuerpo);
    }

    private static void ponerCabecerasComunes(HttpExchange intercambio) {
        // no-store: las respuestas llevan datos personales y no deben quedarse en ninguna caché.
        // nosniff: el navegador no intenta adivinar el tipo y trata la respuesta como JSON.
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        intercambio.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    }

    // ---------------------------------------------------------------------------------------
    // Del modelo a JSON
    // ---------------------------------------------------------------------------------------

    /** La cuenta del propio usuario (login, registro, GET /api/yo). Sin el hash de la contraseña. */
    public static JSONObject cuenta(Usuario usuario) {
        return new JSONObject()
                .put("id", usuario.id())
                .put("usuario", usuario.usuario())
                .put("telefono", usuario.telefono())
                .put("rol", usuario.rol().enBd())
                .put("estado", usuario.estado().enBd());
    }

    /** Un cliente tal como lo ve el barbero en D4. */
    public static JSONObject cliente(Usuario usuario) {
        return new JSONObject()
                .put("id", usuario.id())
                .put("usuario", usuario.usuario())
                .put("telefono", usuario.telefono())
                .put("estado", usuario.estado().enBd())
                .put("precio_corte", importe(usuario.precioCorte()))
                .put("creado_en", fechaHora(usuario.creadoEn()))
                .put("aprobado_en", fechaHora(usuario.aprobadoEn()));
    }

    /** Una cita para su cliente: sin precio, propina ni decidida_en (RN-18). */
    public static JSONObject citaParaCliente(Cita cita) {
        return new JSONObject()
                .put("id", cita.id())
                .put("fecha", fecha(cita.fecha()))
                .put("hora_pedida", hora(cita.horaPedida()))
                .put("hora_final", hora(cita.horaFinal()))
                .put("estado", cita.estado().enBd())
                .put("creada_en", fechaHora(cita.creadaEn()));
    }

    /** Una cita para el barbero: con los datos del cliente y el dinero. */
    public static JSONObject citaParaAdmin(Cita cita) {
        JSONObject cliente = new JSONObject()
                .put("id", cita.clienteId())
                .put("usuario", cita.clienteUsuario())
                .put("telefono", cita.clienteTelefono());
        return new JSONObject()
                .put("id", cita.id())
                .put("cliente", cliente)
                .put("fecha", fecha(cita.fecha()))
                .put("hora_pedida", hora(cita.horaPedida()))
                .put("hora_final", hora(cita.horaFinal()))
                .put("estado", cita.estado().enBd())
                .put("precio", importe(cita.precio()))
                .put("propina", importe(cita.propina()))
                .put("creada_en", fechaHora(cita.creadaEn()))
                .put("decidida_en", fechaHora(cita.decididaEn()));
    }

    public static JSONObject invitacion(Invitacion invitacion, LocalDateTime ahora) {
        Object usadaPor = JSONObject.NULL;
        if (invitacion.usadaPor() != null) {
            usadaPor = new JSONObject()
                    .put("id", invitacion.usadaPor())
                    .put("usuario", nuloSiFalta(invitacion.usadaPorUsuario()));
        }
        return new JSONObject()
                .put("id", invitacion.id())
                .put("codigo", invitacion.codigo())
                .put("nota", nuloSiFalta(invitacion.nota()))
                .put("estado", invitacion.estado().enBd())
                .put("caducada", invitacion.estaCaducada(ahora))
                .put("caduca_en", fechaHora(invitacion.caducaEn()))
                .put("creada_en", fechaHora(invitacion.creadaEn()))
                .put("usada_por", usadaPor);
    }

    public static JSONObject horario(HorarioSemanal horario) {
        return new JSONObject()
                .put("id", horario.id())
                .put("dia_semana", horario.diaSemana())
                .put("hora_inicio", hora(horario.horaInicio()))
                .put("hora_fin", hora(horario.horaFin()))
                .put("cupo", horario.cupo())
                .put("activo", horario.activo());
    }

    public static JSONObject excepcion(ExcepcionDia excepcion) {
        return new JSONObject()
                .put("id", excepcion.id())
                .put("fecha", fecha(excepcion.fecha()))
                .put("tipo", excepcion.tipo().enBd())
                .put("hora_inicio", hora(excepcion.horaInicio()))
                .put("hora_fin", hora(excepcion.horaFin()))
                .put("cupo", nuloSiFalta(excepcion.cupo()))
                .put("nota", nuloSiFalta(excepcion.nota()));
    }

    // ---------------------------------------------------------------------------------------
    // Valores sueltos
    // ---------------------------------------------------------------------------------------

    // OJO: en org.json, put("campo", null) BORRA el campo en vez de escribir null. El contrato
    // dice que los campos vacíos salen como null (por ejemplo "hora_final": null), así que
    // usamos JSONObject.NULL, que es lo que org.json escribe como null.

    public static Object fecha(LocalDate fecha) {
        return fecha == null ? JSONObject.NULL : fecha.format(FORMATO_FECHA);
    }

    public static Object hora(LocalTime hora) {
        return hora == null ? JSONObject.NULL : hora.format(FORMATO_HORA);
    }

    public static Object fechaHora(LocalDateTime fechaHora) {
        return fechaHora == null ? JSONObject.NULL : fechaHora.format(FORMATO_FECHA_HORA);
    }

    private static Object nuloSiFalta(Object valor) {
        return valor == null ? JSONObject.NULL : valor;
    }

    /**
     * Los importes salen siempre con dos decimales (15.00, no 15). OJO: si se mete el BigDecimal
     * tal cual, org.json le quita los ceros del final y escribe 15. Con JSONString le decimos
     * exactamente qué texto poner, y sigue siendo un número en el JSON (sin comillas).
     */
    private static Object importe(BigDecimal valor) {
        if (valor == null) {
            return JSONObject.NULL;
        }
        String texto = valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
        return (JSONString) () -> texto;
    }
}
