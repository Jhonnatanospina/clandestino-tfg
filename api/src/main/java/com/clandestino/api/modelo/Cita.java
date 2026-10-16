package com.clandestino.api.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Una cita de la tabla citas. Dura siempre una hora.
 *
 * clienteUsuario y clienteTelefono no son columnas de citas: las rellena la consulta con un JOIN
 * a usuarios, porque el barbero necesita saber de quién es cada cita (en D1 y D2 sale el nombre
 * y un botón para llamarle). Al cliente nunca se le mandan.
 *
 * Como el record es inmutable, los cambios de estado no modifican la cita: devuelven una copia
 * con los campos cambiados (confirmadaA, conEstado, cerrada) y el repositorio guarda esa copia.
 */
public record Cita(
        long id,
        long clienteId,
        String clienteUsuario,
        String clienteTelefono,
        LocalDate fecha,
        LocalTime horaPedida,
        LocalTime horaFinal,
        EstadoCita estado,
        BigDecimal precio,
        BigDecimal propina,
        LocalDateTime creadaEn,
        LocalDateTime decididaEn) {

    /** Cuánto dura cada cita, en minutos (plan §5). */
    public static final int DURACION_MINUTOS = 60;

    /**
     * La hora a la que de verdad es la cita: la que fijó el barbero al confirmar o, si todavía
     * no ha decidido, la que pidió el cliente. Es la misma cuenta que hace la columna generada
     * hora_ocupada en la base de datos.
     */
    public LocalTime horaEfectiva() {
        return horaFinal != null ? horaFinal : horaPedida;
    }

    /**
     * Una cita "ha pasado" cuando su hora de inicio ya ha llegado. Usamos "no es posterior" en
     * vez de "es anterior" para que a las 17:00 en punto la cita de las 17:00 ya cuente como
     * pasada: ya no se puede cancelar, pero sí marcar la asistencia.
     */
    public boolean yaHaPasado(LocalDateTime ahora) {
        return !LocalDateTime.of(fecha, horaEfectiva()).isAfter(ahora);
    }

    public Cita confirmadaA(LocalTime nuevaHoraFinal, LocalDateTime ahora) {
        return new Cita(id, clienteId, clienteUsuario, clienteTelefono, fecha, horaPedida,
                nuevaHoraFinal, EstadoCita.CONFIRMADA, precio, propina, creadaEn, ahora);
    }

    /** Para rechazar o cancelar. decididaEn solo se cambia si se pasa uno nuevo. */
    public Cita conEstado(EstadoCita nuevoEstado, LocalDateTime nuevaDecididaEn) {
        LocalDateTime decision = nuevaDecididaEn != null ? nuevaDecididaEn : decididaEn;
        return new Cita(id, clienteId, clienteUsuario, clienteTelefono, fecha, horaPedida,
                horaFinal, nuevoEstado, precio, propina, creadaEn, decision);
    }

    /** Para marcar la asistencia: HECHA con su precio, o NO_ASISTIO sin precio. */
    public Cita cerrada(EstadoCita resultado, BigDecimal precioCobrado, BigDecimal propinaRecibida) {
        return new Cita(id, clienteId, clienteUsuario, clienteTelefono, fecha, horaPedida,
                horaFinal, resultado, precioCobrado, propinaRecibida, creadaEn, decididaEn);
    }
}
