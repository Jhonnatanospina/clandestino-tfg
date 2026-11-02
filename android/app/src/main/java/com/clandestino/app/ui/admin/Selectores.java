package com.clandestino.app.ui.admin;

import android.content.Context;

import com.clandestino.app.R;
import com.clandestino.app.utilidades.Formatos;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Los selectores que se repiten en las pantallas del barbero: el reloj y la lista del cupo de
 * la plantilla semanal y del día extra, y las horas del desplegable de hora final, que usan
 * las solicitudes (D1) y la cita a mano (D5).
 */
final class Selectores {

    // La API admite hasta 255, pero en una barbería de una persona con 10 sobra.
    private static final int CUPO_MAXIMO = 10;

    // DP-04: la hora final se elige de 08:00 a 22:00 cada media hora, igual que en la API.
    private static final LocalTime PRIMERA_HORA = LocalTime.of(8, 0);
    private static final LocalTime ULTIMA_HORA = LocalTime.of(22, 0);

    /** "08:00", "08:30"... "22:00", ya escritas para el desplegable. */
    static final List<String> HORAS_FINALES = horasFinales();

    private Selectores() {
    }

    private static List<String> horasFinales() {
        List<String> horas = new ArrayList<>();
        for (LocalTime hora = PRIMERA_HORA; !hora.isAfter(ULTIMA_HORA); hora = hora.plusMinutes(30)) {
            horas.add(Formatos.hora(hora));
        }
        return Collections.unmodifiableList(horas);
    }

    /** Reloj de Material en formato de 24 horas, empezando en la hora que ya había. */
    static MaterialTimePicker reloj(String titulo, LocalTime hora) {
        return new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(hora.getHour())
                .setMinute(hora.getMinute())
                .setTitleText(titulo)
                .build();
    }

    static LocalTime horaElegida(MaterialTimePicker reloj) {
        return LocalTime.of(reloj.getHour(), reloj.getMinute());
    }

    /**
     * Lista de 0 a 10 con el cupo actual marcado. Con 0 el día queda siempre completo, que
     * también es una forma válida de cerrarlo sin apagarlo.
     */
    static void elegirCupo(Context contexto, String titulo, int actual, IntConsumer alElegir) {
        String[] opciones = new String[CUPO_MAXIMO + 1];
        for (int cupo = 0; cupo <= CUPO_MAXIMO; cupo++) {
            opciones[cupo] = String.valueOf(cupo);
        }
        new MaterialAlertDialogBuilder(contexto)
                .setTitle(titulo)
                .setSingleChoiceItems(opciones, Math.min(actual, CUPO_MAXIMO), (dialogo, elegido) -> {
                    alElegir.accept(elegido);
                    dialogo.dismiss();
                })
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }
}
