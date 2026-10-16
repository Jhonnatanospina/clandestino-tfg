package com.clandestino.app.modelo;

import java.util.Locale;

/** Estado de una cuenta. Un cliente nace pendiente y el barbero lo aprueba o lo bloquea. */
public enum EstadoUsuario {
    PENDIENTE("Pendiente"),
    APROBADO("Aprobado"),
    BLOQUEADO("Bloqueado");

    private final String etiqueta;

    EstadoUsuario(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public static EstadoUsuario desdeApi(String texto) {
        return valueOf(texto.toUpperCase(Locale.ROOT));
    }

    public String etiqueta() {
        return etiqueta;
    }
}
