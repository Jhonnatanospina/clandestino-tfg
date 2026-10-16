package com.clandestino.api.modelo;

import java.util.Locale;

/**
 * Cómo ve un día el cliente en el calendario de "Pedir cita" (RN-06).
 *
 * No se guarda en ninguna tabla: se calcula cada vez a partir del horario semanal, de las
 * excepciones y de las citas activas de esa fecha.
 */
public enum EstadoDia {
    /** Abierto y con cupo. Es el único en el que se puede pedir cita. */
    LIBRE,
    /** Abierto pero con el cupo ya alcanzado. */
    COMPLETO,
    /** Hay una excepción "cerrado" para esa fecha. */
    VACACIONES,
    /** Ese día de la semana no hay horario (o está desactivado). */
    CERRADO;

    public String enJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
