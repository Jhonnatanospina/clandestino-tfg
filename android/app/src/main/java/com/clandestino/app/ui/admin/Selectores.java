package com.clandestino.app.ui.admin;

import android.content.Context;

import com.clandestino.app.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.time.LocalTime;
import java.util.function.IntConsumer;

/**
 * Los dos selectores que comparten la plantilla semanal y el diálogo de día extra: el reloj
 * para las horas y la lista para el cupo.
 */
final class Selectores {

    // La API admite hasta 255, pero en una barbería de una persona con 10 sobra.
    private static final int CUPO_MAXIMO = 10;

    private Selectores() {
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
