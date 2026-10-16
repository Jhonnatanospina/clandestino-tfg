package com.clandestino.api;

import com.clandestino.api.servicio.Reloj;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Un reloj para los tests que no avanza solo: marca la hora que le digamos y la podemos mover
 * a mano. Así un test puede decir "hoy es lunes 5 de octubre a las 10:00" y, más abajo,
 * "ahora es martes a las 19:00" para marcar la asistencia de una cita que ya pasó.
 */
public final class RelojMovible extends Clock {

    private volatile Instant ahora;

    public RelojMovible(LocalDateTime fechaHora) {
        ponerEn(fechaHora);
    }

    /** El Reloj de la API montado sobre este reloj. */
    public Reloj comoReloj() {
        return new Reloj(this);
    }

    public void ponerEn(LocalDateTime fechaHora) {
        ahora = fechaHora.atZone(Reloj.ZONA_MADRID).toInstant();
    }

    public void avanzar(Duration tiempo) {
        ahora = ahora.plus(tiempo);
    }

    @Override
    public ZoneId getZone() {
        return Reloj.ZONA_MADRID;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        // Los tests siempre trabajan en hora de Madrid; no hace falta otra zona.
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
        return ahora;
    }
}
