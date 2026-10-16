package com.clandestino.api.modelo;

import java.util.Locale;

/**
 * Estado guardado de una invitación (RN-22).
 *
 * "Caducada" no está aquí a propósito: no se guarda, se calcula comparando caduca_en con la
 * hora actual (ver Invitacion.estaCaducada). Así no hace falta ninguna tarea que vaya
 * cambiando el estado cuando pasan los 7 días.
 */
public enum EstadoInvitacion {
    PENDIENTE,
    USADA;

    public String enBd() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static EstadoInvitacion desdeTexto(String texto) {
        return valueOf(texto.toUpperCase(Locale.ROOT));
    }
}
