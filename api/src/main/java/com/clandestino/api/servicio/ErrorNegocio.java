package com.clandestino.api.servicio;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Error "previsto": algo que el usuario ha hecho mal o que no se puede hacer ahora (el día está
 * lleno, la invitación ha caducado...). No es un fallo del programa.
 *
 * Lleva el código estable que entiende la app (por ejemplo "dia_lleno"), el código HTTP con el
 * que se responde y un mensaje en español. El Enrutador lo convierte en la respuesta
 * { "error": "...", "mensaje": "..." } del contrato de la API (api-contrato.md §5).
 *
 * Es una RuntimeException para no tener que declararla en todos los métodos. Además, al
 * lanzarse dentro de una transacción, BaseDatos hace rollback (ver BaseDatos.enTransaccion).
 */
public class ErrorNegocio extends RuntimeException {

    private final String codigo;
    private final int estadoHttp;
    private final Map<String, String> cabeceras = new LinkedHashMap<>();

    public ErrorNegocio(String codigo, int estadoHttp, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
        this.estadoHttp = estadoHttp;
    }

    public String getCodigo() {
        return codigo;
    }

    public int getEstadoHttp() {
        return estadoHttp;
    }

    public Map<String, String> getCabeceras() {
        return cabeceras;
    }

    /** Algunas respuestas de error llevan una cabecera extra (Retry-After en el 429, Allow en el 405). */
    public ErrorNegocio conCabecera(String nombre, String valor) {
        cabeceras.put(nombre, valor);
        return this;
    }

    // Atajos para los errores que más se repiten. El resto se crean con el constructor.

    public static ErrorNegocio datosInvalidos(String mensaje) {
        return new ErrorNegocio("datos_invalidos", 400, mensaje);
    }

    public static ErrorNegocio noEncontrado(String mensaje) {
        return new ErrorNegocio("no_encontrado", 404, mensaje);
    }

    public static ErrorNegocio estadoInvalido(String mensaje) {
        return new ErrorNegocio("estado_invalido", 409, mensaje);
    }
}
