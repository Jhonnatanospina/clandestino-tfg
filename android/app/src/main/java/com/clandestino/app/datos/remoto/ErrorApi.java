package com.clandestino.app.datos.remoto;

/**
 * Un error de la API o de la red. Guarda el código estable de la API (dia_lleno,
 * hueco_ocupado...) para que la pantalla pueda reaccionar a casos concretos, y el mensaje en
 * español que ya manda la API para enseñárselo a la persona.
 *
 * Los fallos de red no tienen código HTTP: van con estado 0 y el código "sin_conexion".
 */
public class ErrorApi extends Exception {

    private final int estadoHttp;
    private final String codigo;

    public ErrorApi(int estadoHttp, String codigo, String mensaje) {
        super(mensaje);
        this.estadoHttp = estadoHttp;
        this.codigo = codigo;
    }

    public int getEstadoHttp() {
        return estadoHttp;
    }

    public String getCodigo() {
        return codigo;
    }

    /**
     * Falta el token, está caducado o es de un usuario que ya no existe. No vale con mirar el
     * 401: un login con la contraseña mal también da 401 (credenciales_invalidas).
     */
    public boolean sesionNoValida() {
        return "no_autenticado".equals(codigo) || "token_invalido".equals(codigo);
    }

    /** El barbero ha bloqueado la cuenta (o aún no la ha aprobado) con la sesión abierta. */
    public boolean cuentaNoAprobada() {
        return "no_aprobado".equals(codigo);
    }
}
