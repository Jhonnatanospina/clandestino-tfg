package com.clandestino.api.modelo;

import java.time.LocalTime;

/**
 * El horario de un día de la semana (RN-03): de qué hora a qué hora se trabaja y cuántas citas
 * caben como mucho ese día.
 *
 * diaSemana va de 1 (lunes) a 7 (domingo), igual que DayOfWeek.getValue() de java.time. En el
 * Clandestino original iba de 0 a 6 empezando en domingo; lo cambiamos para no tener que
 * convertir en ningún sitio.
 *
 * id es Integer (y no int) porque cuando el barbero manda un horario nuevo todavía no tiene id.
 */
public record HorarioSemanal(
        Integer id,
        int diaSemana,
        LocalTime horaInicio,
        LocalTime horaFin,
        int cupo,
        boolean activo) {
}
