package com.clandestino.api.modelo;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * El horario que de verdad se aplica a una fecha, ya resuelto: o sale de una excepción
 * "abierto" o del horario semanal (RN-01). Si un día no tiene tramo, está cerrado.
 *
 * Aquí está toda la cuenta de los huecos de una hora, sin base de datos de por medio, para
 * poder probarla con tests normales.
 */
public record Tramo(LocalTime horaInicio, LocalTime horaFin, int cupo) {

    /**
     * Las horas a las que se puede pedir cita (RN-04): desde la hora de inicio, de 60 en 60
     * minutos, mientras la cita entera quepa antes de la hora de fin. Con 16:00-22:00 salen
     * 16:00, 17:00, 18:00, 19:00, 20:00 y 21:00.
     */
    public List<LocalTime> huecos() {
        // El bucle termina siempre, también cerca de medianoche: cabe() ya mira si la cita
        // "da la vuelta" (23:30 + 1 h = 00:30) y en ese caso devuelve false.
        List<LocalTime> horas = new ArrayList<>();
        for (LocalTime hora = horaInicio; cabe(hora); hora = hora.plusMinutes(Cita.DURACION_MINUTOS)) {
            horas.add(hora);
        }
        return horas;
    }

    /** La hora empieza dentro del tramo y la cita (una hora) termina antes de que se cierre. */
    public boolean cabe(LocalTime hora) {
        LocalTime fin = hora.plusMinutes(Cita.DURACION_MINUTOS);
        boolean daLaVuelta = fin.isBefore(hora);
        return !hora.isBefore(horaInicio) && !daLaVuelta && !fin.isAfter(horaFin);
    }

    /**
     * La hora cae en la rejilla del tramo: entre la hora de inicio y ella hay un número exacto
     * de horas (DP-03). Con un tramo que empieza a las 16:30, las 17:30 vale y las 17:00 no.
     */
    public boolean estaAlineada(LocalTime hora) {
        int minutosDesdeElInicio = hora.toSecondOfDay() / 60 - horaInicio.toSecondOfDay() / 60;
        return hora.getSecond() == 0 && minutosDesdeElInicio % Cita.DURACION_MINUTOS == 0;
    }
}
