package com.clandestino.app.modelo;

import java.util.Locale;

/** Los estados de una cita, los mismos del ENUM de la tabla citas (diagrama 07). */
public enum EstadoCita {
    PENDIENTE("Pendiente"),
    CONFIRMADA("Confirmada"),
    RECHAZADA("Rechazada"),
    CANCELADA("Cancelada"),
    CADUCADA("Caducada"),
    HECHA("Hecha"),
    NO_ASISTIO("No vino");

    private final String etiqueta;

    EstadoCita(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** La API manda los estados en minúsculas ("no_asistio"). */
    public static EstadoCita desdeApi(String texto) {
        return valueOf(texto.toUpperCase(Locale.ROOT));
    }

    /** Texto del chip de estado. */
    public String etiqueta() {
        return etiqueta;
    }

    /** Las confirmadas y las hechas se pintan en rojo; el resto, en gris. */
    public boolean destacado() {
        return this == CONFIRMADA || this == HECHA;
    }

    /** Solo pendiente y confirmada ocupan hueco y cuentan para el cupo (RN-07 y RN-11). */
    public boolean ocupaHueco() {
        return this == PENDIENTE || this == CONFIRMADA;
    }
}
