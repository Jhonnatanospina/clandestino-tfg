package com.clandestino.app.modelo;

import java.util.Locale;

/** Cómo está un día para pedir cita (RN-06). Lo calcula la API; la app solo lo pinta. */
public enum EstadoDia {
    /** Abierto y con cupo libre. */
    LIBRE,
    /** Abierto, pero ya tiene todas las citas que admite. */
    COMPLETO,
    /** El barbero lo ha cerrado con una excepción. */
    VACACIONES,
    /** No hay horario ese día de la semana. */
    CERRADO;

    public static EstadoDia desdeApi(String texto) {
        return valueOf(texto.toUpperCase(Locale.ROOT));
    }
}
