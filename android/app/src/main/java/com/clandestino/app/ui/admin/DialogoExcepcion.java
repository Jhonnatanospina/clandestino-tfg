package com.clandestino.app.ui.admin;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.clandestino.app.R;
import com.clandestino.app.modelovista.ModeloVistaHorarios;
import com.clandestino.app.ui.Pantallas;
import com.clandestino.app.utilidades.Formatos;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.timepicker.MaterialTimePicker;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Diálogo de D3 para cerrar unos días (vacaciones) o abrir uno que normalmente está cerrado
 * (día extra). Usa el modelo de vista de FragmentoHorarios, así la lista de abajo se recarga
 * sola y el aviso de "Guardado" lo enseña la pantalla.
 */
public class DialogoExcepcion extends DialogFragment {

    private static final int DIAS_MAXIMOS = 31;
    private static final String CALENDARIO = "calendario";
    private static final String RELOJ_INICIO = "reloj_inicio";
    private static final String RELOJ_FIN = "reloj_fin";
    private static final String ES_DIA_EXTRA = "es_dia_extra";
    private static final String DESDE = "desde";
    private static final String HASTA = "hasta";
    private static final String INICIO = "inicio";
    private static final String FIN = "fin";
    private static final String CUPO = "cupo";

    private ModeloVistaHorarios modelo;
    private boolean esDiaExtra;
    private LocalDate desde;
    private LocalDate hasta;
    private LocalTime inicio = LocalTime.of(10, 0);
    private LocalTime fin = LocalTime.of(14, 0);
    private int cupo = 2;

    private TextView etiquetaFechas;
    private MaterialButton botonFechas;
    private View tramo;
    private MaterialButton botonInicio;
    private MaterialButton botonFin;
    private MaterialButton botonCupo;
    private TextView campoNota;
    private View ayudaVacaciones;
    private TextView error;

    @SuppressLint("UseGetLayoutInflater")
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle estadoGuardado) {
        modelo = new ViewModelProvider(requireParentFragment()).get(ModeloVistaHorarios.class);
        recuperar(estadoGuardado);
        MaterialAlertDialogBuilder constructor = new MaterialAlertDialogBuilder(requireContext());
        // Se infla con el contexto del constructor porque lleva el tema de los diálogos de
        // Material; el de getLayoutInflater() sería el de la pantalla de detrás.
        View vista = LayoutInflater.from(constructor.getContext())
                .inflate(R.layout.dialogo_excepcion, null);
        enlazar(vista);
        reengancharSelectores();
        pintar();
        return constructor
                .setTitle(R.string.dia_especial_titulo)
                .setView(vista)
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(R.string.guardar, null)
                .create();
    }

    /**
     * El botón Guardar se cambia aquí y no en el builder: así, si falta algo, el diálogo se
     * queda abierto enseñando el error en vez de cerrarse solo.
     */
    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialogo = (AlertDialog) requireDialog();
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> guardar());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle estado) {
        super.onSaveInstanceState(estado);
        estado.putBoolean(ES_DIA_EXTRA, esDiaExtra);
        estado.putString(DESDE, desde == null ? null : desde.toString());
        estado.putString(HASTA, hasta == null ? null : hasta.toString());
        estado.putString(INICIO, inicio.toString());
        estado.putString(FIN, fin.toString());
        estado.putInt(CUPO, cupo);
    }

    private void recuperar(@Nullable Bundle estado) {
        if (estado == null) {
            return;
        }
        esDiaExtra = estado.getBoolean(ES_DIA_EXTRA);
        String guardadoDesde = estado.getString(DESDE);
        String guardadoHasta = estado.getString(HASTA);
        desde = guardadoDesde == null ? null : LocalDate.parse(guardadoDesde);
        hasta = guardadoHasta == null ? null : LocalDate.parse(guardadoHasta);
        inicio = LocalTime.parse(estado.getString(INICIO));
        fin = LocalTime.parse(estado.getString(FIN));
        cupo = estado.getInt(CUPO);
    }

    private void enlazar(View vista) {
        etiquetaFechas = vista.findViewById(R.id.etiqueta_fechas);
        botonFechas = vista.findViewById(R.id.boton_fechas);
        tramo = vista.findViewById(R.id.tramo);
        botonInicio = vista.findViewById(R.id.boton_inicio);
        botonFin = vista.findViewById(R.id.boton_fin);
        botonCupo = vista.findViewById(R.id.boton_cupo);
        campoNota = vista.findViewById(R.id.campo_nota);
        ayudaVacaciones = vista.findViewById(R.id.ayuda_vacaciones);
        error = vista.findViewById(R.id.error);

        MaterialButtonToggleGroup tipo = vista.findViewById(R.id.tipo);
        tipo.check(esDiaExtra ? R.id.boton_dia_extra : R.id.boton_vacaciones);
        tipo.addOnButtonCheckedListener((grupo, boton, marcado) -> {
            boolean extra = boton == R.id.boton_dia_extra;
            // Las fechas de un tipo no sirven para el otro (un rango frente a un solo día).
            if (marcado && extra != esDiaExtra) {
                esDiaExtra = extra;
                desde = null;
                hasta = null;
                pintar();
            }
        });
        botonFechas.setOnClickListener(v -> elegirFechas());
        botonInicio.setOnClickListener(v -> elegirHora(true));
        botonFin.setOnClickListener(v -> elegirHora(false));
        botonCupo.setOnClickListener(v -> Selectores.elegirCupo(requireContext(),
                getString(R.string.cupo), cupo, elegido -> {
                    cupo = elegido;
                    pintar();
                }));
    }

    private void pintar() {
        etiquetaFechas.setText(esDiaExtra ? R.string.dia : R.string.dias);
        botonFechas.setText(textoFechas());
        tramo.setVisibility(esDiaExtra ? View.VISIBLE : View.GONE);
        ayudaVacaciones.setVisibility(esDiaExtra ? View.GONE : View.VISIBLE);
        botonInicio.setText(Formatos.hora(inicio));
        botonFin.setText(Formatos.hora(fin));
        botonCupo.setText(String.valueOf(cupo));
        error.setVisibility(View.GONE);
    }

    private String textoFechas() {
        if (desde == null) {
            return getString(R.string.elige_fechas);
        }
        if (desde.equals(hasta)) {
            return Formatos.diaLargo(desde);
        }
        return Formatos.rango(desde, hasta);
    }

    /** Calendario de Material: un rango para vacaciones y un solo día para el día extra. */
    private void elegirFechas() {
        CalendarConstraints desdeHoy = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build();
        if (esDiaExtra) {
            MaterialDatePicker<Long> calendario = MaterialDatePicker.Builder.datePicker()
                    .setTitleText(R.string.dia)
                    .setCalendarConstraints(desdeHoy)
                    .build();
            escucharDia(calendario);
            calendario.show(getChildFragmentManager(), CALENDARIO);
        } else {
            MaterialDatePicker<Pair<Long, Long>> calendario =
                    MaterialDatePicker.Builder.dateRangePicker()
                            .setTitleText(R.string.dias)
                            .setCalendarConstraints(desdeHoy)
                            .build();
            escucharRango(calendario);
            calendario.show(getChildFragmentManager(), CALENDARIO);
        }
    }

    private void escucharDia(MaterialDatePicker<Long> calendario) {
        calendario.addOnPositiveButtonClickListener(dia -> {
            desde = fecha(dia);
            hasta = desde;
            pintar();
        });
    }

    private void escucharRango(MaterialDatePicker<Pair<Long, Long>> calendario) {
        calendario.addOnPositiveButtonClickListener(rango -> {
            desde = fecha(rango.first);
            hasta = fecha(rango.second);
            pintar();
        });
    }

    /** El calendario de Material devuelve milisegundos en UTC a medianoche, no en hora local. */
    private static LocalDate fecha(long milisegundos) {
        return Instant.ofEpochMilli(milisegundos).atZone(ZoneOffset.UTC).toLocalDate();
    }

    private void elegirHora(boolean esInicio) {
        MaterialTimePicker reloj = Selectores.reloj(getString(esInicio ? R.string.inicio : R.string.fin),
                esInicio ? inicio : fin);
        escucharReloj(reloj, esInicio);
        reloj.show(getChildFragmentManager(), esInicio ? RELOJ_INICIO : RELOJ_FIN);
    }

    private void escucharReloj(MaterialTimePicker reloj, boolean esInicio) {
        reloj.addOnPositiveButtonClickListener(v -> {
            if (esInicio) {
                inicio = Selectores.horaElegida(reloj);
            } else {
                fin = Selectores.horaElegida(reloj);
            }
            pintar();
        });
    }

    /** Igual que en FragmentoHorarios: tras girar, el selector abierto vuelve sin listeners. */
    @SuppressWarnings("unchecked")
    private void reengancharSelectores() {
        Object calendario = getChildFragmentManager().findFragmentByTag(CALENDARIO);
        if (calendario != null) {
            if (esDiaExtra) {
                escucharDia((MaterialDatePicker<Long>) calendario);
            } else {
                escucharRango((MaterialDatePicker<Pair<Long, Long>>) calendario);
            }
        }
        Object relojInicio = getChildFragmentManager().findFragmentByTag(RELOJ_INICIO);
        if (relojInicio != null) {
            escucharReloj((MaterialTimePicker) relojInicio, true);
        }
        Object relojFin = getChildFragmentManager().findFragmentByTag(RELOJ_FIN);
        if (relojFin != null) {
            escucharReloj((MaterialTimePicker) relojFin, false);
        }
    }

    private void guardar() {
        String problema = problema();
        if (problema != null) {
            error.setText(problema);
            error.setVisibility(View.VISIBLE);
            return;
        }
        String nota = Pantallas.texto(campoNota);
        String notaFinal = nota.isEmpty() ? null : nota;
        if (esDiaExtra) {
            modelo.abrirDiaExtra(desde, inicio, fin, cupo, notaFinal);
        } else {
            modelo.cerrarDias(desde, hasta, notaFinal);
        }
        dismiss();
    }

    /** Lo mismo que comprobaría la API, para avisar antes de mandar nada. Null si todo está bien. */
    @Nullable
    private String problema() {
        if (desde == null) {
            return getString(R.string.falta_fecha);
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > DIAS_MAXIMOS) {
            return getString(R.string.como_mucho_31);
        }
        if (esDiaExtra && !inicio.isBefore(fin)) {
            return getString(R.string.tramo_al_reves, Formatos.diaYMes(desde));
        }
        return null;
    }
}
