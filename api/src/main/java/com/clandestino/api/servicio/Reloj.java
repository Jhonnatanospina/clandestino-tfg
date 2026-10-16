package com.clandestino.api.servicio;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * De aquí sale la hora "de ahora" para toda la API.
 *
 * La barbería está en España, así que todo va en hora de Madrid (Europe/Madrid), también
 * cuando el servidor tenga otra zona configurada. Las fechas y horas de la base de datos ya
 * están en hora de Madrid y no se convierten.
 *
 * ¿Por qué una clase y no llamar a LocalDateTime.now() directamente? Por los tests. Muchas
 * reglas dependen del momento actual (no se puede pedir una hora que ya ha pasado, el
 * horizonte empieza hoy...). Con un Reloj podemos fijar en un test que "hoy" es un lunes a las
 * 10:00 y el resultado no cambia según el día en que se ejecute.
 */
public class Reloj {

    public static final ZoneId ZONA_MADRID = ZoneId.of("Europe/Madrid");

    private final Clock reloj;

    public Reloj(Clock reloj) {
        this.reloj = reloj;
    }

    /** El reloj de verdad, el que usa el servidor. */
    public static Reloj deMadrid() {
        return new Reloj(Clock.system(ZONA_MADRID));
    }

    /**
     * Sin nanosegundos: en la base de datos los DATETIME se guardan a segundos y así lo que
     * devolvemos al crear algo coincide con lo que luego se lee.
     */
    public LocalDateTime ahora() {
        return LocalDateTime.now(reloj).withNano(0);
    }

    public LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    /** El instante exacto, sin zona. Lo usan el token (fechas en segundos desde 1970) y el limitador. */
    public Instant instante() {
        return reloj.instant();
    }
}
