package com.clandestino.api.modelo;

import java.util.Locale;

/**
 * Estados por los que pasa una cita (diagrama 07 y reglas-negocio.md §9).
 *
 * Solo PENDIENTE y CONFIRMADA ocupan hueco y cupo. El resto son estados finales: una vez
 * que la cita llega a uno de ellos ya no se mueve.
 */
public enum EstadoCita {
    PENDIENTE,
    CONFIRMADA,
    RECHAZADA,
    CANCELADA,
    CADUCADA,
    HECHA,
    NO_ASISTIO;

    /** Una cita activa es la que todavía cuenta para el cupo del día y bloquea su hora. */
    public boolean esActiva() {
        return this == PENDIENTE || this == CONFIRMADA;
    }

    public String enBd() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static EstadoCita desdeTexto(String texto) {
        return valueOf(texto.toUpperCase(Locale.ROOT));
    }
}
