package com.clandestino.api.modelo;

import java.util.Locale;

/**
 * Estado de la cuenta de un cliente (RN-23).
 *
 * Un cliente nace PENDIENTE al registrarse y el barbero lo aprueba o lo bloquea. Nunca se
 * vuelve a PENDIENTE. El administrador siempre está APROBADO.
 */
public enum EstadoUsuario {
    PENDIENTE,
    APROBADO,
    BLOQUEADO;

    public String enBd() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static EstadoUsuario desdeTexto(String texto) {
        return valueOf(texto.toUpperCase(Locale.ROOT));
    }
}
