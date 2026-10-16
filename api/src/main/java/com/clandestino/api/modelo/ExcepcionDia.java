package com.clandestino.api.modelo;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Una fecha concreta que manda sobre el horario semanal (RN-01, RN-02): vacaciones, un día
 * libre, un sábado extra...
 *
 * En un día CERRADO las horas y el cupo son null (la base de datos lo obliga con un CHECK).
 * En un día ABIERTO las horas son obligatorias y el cupo es opcional.
 */
public record ExcepcionDia(
        Integer id,
        LocalDate fecha,
        TipoExcepcion tipo,
        LocalTime horaInicio,
        LocalTime horaFin,
        Integer cupo,
        String nota) {

    /** Cupo que se usa cuando un día abierto no dice el suyo (el mismo valor por defecto de la tabla de horarios). */
    public static final int CUPO_POR_DEFECTO = 2;

    public int cupoEfectivo() {
        return cupo != null ? cupo : CUPO_POR_DEFECTO;
    }

    /** La misma excepción para otra fecha. Sirve para crear varias de golpe (vacaciones de una semana). */
    public ExcepcionDia paraFecha(LocalDate otraFecha) {
        return new ExcepcionDia(null, otraFecha, tipo, horaInicio, horaFin, cupo, nota);
    }
}
