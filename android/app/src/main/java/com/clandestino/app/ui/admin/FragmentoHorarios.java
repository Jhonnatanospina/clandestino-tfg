package com.clandestino.app.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.clandestino.app.R;
import com.clandestino.app.modelo.ExcepcionDia;
import com.clandestino.app.modelo.HorarioSemanal;
import com.clandestino.app.modelovista.ModeloVistaHorarios;
import com.clandestino.app.ui.FragmentoPanel;
import com.clandestino.app.ui.Pantallas;
import com.clandestino.app.utilidades.Formatos;
import com.clandestino.app.utilidades.Resultado;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.timepicker.MaterialTimePicker;

import java.util.List;
import java.util.Locale;

/**
 * D3 · Horarios. Arriba, la plantilla de lunes a domingo: los cambios se quedan en el modelo de
 * vista hasta pulsar Guardar. Abajo, las vacaciones y los días extra, que se guardan al momento.
 */
public class FragmentoHorarios extends FragmentoPanel {

    // Etiquetas de los relojes: "inicio_3" es la hora de inicio del miércoles. Con ellas se
    // vuelve a escuchar el reloj si se gira el móvil con él abierto.
    private static final String RELOJ_INICIO = "inicio_";
    private static final String RELOJ_FIN = "fin_";
    private static final String DIALOGO_EXCEPCION = "dialogo_excepcion";

    private ModeloVistaHorarios modelo;
    private LinearProgressIndicator progreso;
    private LinearLayout filasSemana;
    private View botonGuardar;
    private TextView sinExcepciones;
    private AdaptadorExcepciones adaptador;
    private boolean cargandoSemana;
    private boolean cargandoExcepciones;
    private boolean guardando;

    public FragmentoHorarios() {
        super(R.layout.fragmento_horarios);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estadoGuardado) {
        modelo = new ViewModelProvider(this).get(ModeloVistaHorarios.class);
        ponerCabecera(vista, getString(R.string.horarios_ceja), R.string.horarios_titulo);
        botonGuardar = mostrarBoton(vista, R.id.boton_guardar, v -> guardarSemana());

        progreso = vista.findViewById(R.id.progreso);
        filasSemana = vista.findViewById(R.id.filas_semana);
        sinExcepciones = vista.findViewById(R.id.sin_excepciones);
        crearFilas();

        adaptador = new AdaptadorExcepciones(this::preguntarBorrar);
        ((RecyclerView) vista.findViewById(R.id.excepciones)).setAdapter(adaptador);
        vista.findViewById(R.id.boton_anadir).setOnClickListener(v ->
                new DialogoExcepcion().show(getChildFragmentManager(), DIALOGO_EXCEPCION));
        reengancharRelojes();

        modelo.getHorarios().observe(getViewLifecycleOwner(), this::pintarSemana);
        modelo.getExcepciones().observe(getViewLifecycleOwner(), this::pintarExcepciones);
        modelo.getOperacion().observe(getViewLifecycleOwner(), this::pintarOperacion);
        if (modelo.semanaEnPantalla() == null) {
            modelo.cargar();
        }
    }

    /** Una fila por día de la semana. Los listeners leen el horario del modelo al pulsar. */
    private void crearFilas() {
        LayoutInflater inflador = getLayoutInflater();
        for (int dia = 1; dia <= 7; dia++) {
            View fila = inflador.inflate(R.layout.item_horario_dia, filasSemana, false);
            // OJO: las siete filas repiten los mismos id. Si Android guardara su estado al girar,
            // pondría el interruptor de la última en todas. Lo que vale es lo del modelo de vista.
            fila.setSaveFromParentEnabled(false);
            ((TextView) fila.findViewById(R.id.nombre)).setText(Formatos.nombreDia(dia));

            int diaSemana = dia;
            MaterialSwitch interruptor = fila.findViewById(R.id.interruptor);
            interruptor.setOnCheckedChangeListener((boton, activo) -> {
                if (activo != horario(diaSemana).estaActivo()) {
                    modelo.cambiarDia(horario(diaSemana).conActivo(activo));
                }
            });
            fila.findViewById(R.id.boton_inicio).setOnClickListener(v -> elegirHora(diaSemana, true));
            fila.findViewById(R.id.boton_fin).setOnClickListener(v -> elegirHora(diaSemana, false));
            fila.findViewById(R.id.boton_cupo).setOnClickListener(v -> elegirCupo(diaSemana));
            filasSemana.addView(fila);
        }
    }

    private HorarioSemanal horario(int diaSemana) {
        return modelo.semanaEnPantalla().get(diaSemana - 1);
    }

    /** "lunes", para meterlo en una frase. */
    private static String nombreEnFrase(int diaSemana) {
        return Formatos.nombreDia(diaSemana).toLowerCase(Locale.ROOT);
    }

    private void pintarSemana(Resultado<List<HorarioSemanal>> resultado) {
        cargandoSemana = resultado.estaCargando();
        pintarProgreso();
        List<HorarioSemanal> semana = resultado.datos();
        if (semana != null) {
            for (int i = 0; i < semana.size(); i++) {
                pintarFila(filasSemana.getChildAt(i), semana.get(i));
            }
        }
        Pantallas.avisarSiFalla(requireActivity(), resultado);
    }

    private void pintarFila(View fila, HorarioSemanal horario) {
        boolean activo = horario.estaActivo();
        ((MaterialSwitch) fila.findViewById(R.id.interruptor)).setChecked(activo);
        ((TextView) fila.findViewById(R.id.nombre)).setTextColor(ContextCompat.getColor(
                requireContext(), activo ? R.color.tinta : R.color.silencio));
        pintarCasilla(fila.findViewById(R.id.boton_inicio), Formatos.hora(horario.getInicio()), activo);
        pintarCasilla(fila.findViewById(R.id.boton_fin), Formatos.hora(horario.getFin()), activo);
        pintarCasilla(fila.findViewById(R.id.boton_cupo), String.valueOf(horario.getCupo()), activo);
    }

    private void pintarCasilla(MaterialButton casilla, String texto, boolean activa) {
        casilla.setText(texto);
        casilla.setEnabled(activa);
    }

    private void elegirHora(int diaSemana, boolean esInicio) {
        HorarioSemanal actual = horario(diaSemana);
        String titulo = Formatos.nombreDia(diaSemana) + " · "
                + getString(esInicio ? R.string.inicio : R.string.fin);
        MaterialTimePicker reloj = Selectores.reloj(titulo,
                esInicio ? actual.getInicio() : actual.getFin());
        String etiqueta = (esInicio ? RELOJ_INICIO : RELOJ_FIN) + diaSemana;
        escucharReloj(reloj, etiqueta);
        reloj.show(getChildFragmentManager(), etiqueta);
    }

    private void escucharReloj(MaterialTimePicker reloj, String etiqueta) {
        boolean esInicio = etiqueta.startsWith(RELOJ_INICIO);
        int diaSemana = Integer.parseInt(etiqueta.substring(etiqueta.indexOf('_') + 1));
        reloj.addOnPositiveButtonClickListener(v -> {
            HorarioSemanal actual = horario(diaSemana);
            modelo.cambiarDia(esInicio
                    ? actual.conInicio(Selectores.horaElegida(reloj))
                    : actual.conFin(Selectores.horaElegida(reloj)));
        });
    }

    /**
     * Tras girar el móvil, Android vuelve a crear el reloj que estaba abierto, pero sin sus
     * listeners (eran lambdas del fragmento anterior). Se le vuelven a poner por su etiqueta.
     */
    private void reengancharRelojes() {
        for (Fragment hijo : getChildFragmentManager().getFragments()) {
            if (hijo instanceof MaterialTimePicker && hijo.getTag() != null) {
                escucharReloj((MaterialTimePicker) hijo, hijo.getTag());
            }
        }
    }

    private void elegirCupo(int diaSemana) {
        Selectores.elegirCupo(requireContext(),
                getString(R.string.cupo_pregunta, nombreEnFrase(diaSemana)),
                horario(diaSemana).getCupo(),
                cupo -> modelo.cambiarDia(horario(diaSemana).conCupo(cupo)));
    }

    /** Antes de mandar nada se mira que ningún día acabe antes de empezar. */
    private void guardarSemana() {
        List<HorarioSemanal> semana = modelo.semanaEnPantalla();
        if (semana == null) {
            return;
        }
        for (HorarioSemanal horario : semana) {
            if (!horario.tramoValido()) {
                Pantallas.avisar(requireActivity(),
                        getString(R.string.tramo_al_reves, nombreEnFrase(horario.getDiaSemana())));
                return;
            }
        }
        modelo.guardarSemana();
    }

    private void pintarExcepciones(Resultado<List<ExcepcionDia>> resultado) {
        cargandoExcepciones = resultado.estaCargando();
        pintarProgreso();
        List<ExcepcionDia> excepciones = resultado.datos();
        if (excepciones != null) {
            adaptador.mostrar(excepciones);
            sinExcepciones.setVisibility(excepciones.isEmpty() ? View.VISIBLE : View.GONE);
        }
        Pantallas.avisarSiFalla(requireActivity(), resultado);
    }

    private void pintarOperacion(Resultado<String> resultado) {
        guardando = resultado != null && resultado.estaCargando();
        pintarProgreso();
        Pantallas.avisarOperacion(requireActivity(), resultado);
    }

    /** La barra se ve mientras haya algo en marcha, y Guardar no se puede pulsar dos veces. */
    private void pintarProgreso() {
        boolean ocupado = cargandoSemana || cargandoExcepciones || guardando;
        progreso.setVisibility(ocupado ? View.VISIBLE : View.INVISIBLE);
        botonGuardar.setEnabled(!ocupado);
    }

    private void preguntarBorrar(ExcepcionDia excepcion) {
        Pantallas.confirmar(requireContext(),
                getString(R.string.borrar_dia_pregunta, Formatos.diaYMes(excepcion.getFecha())),
                getString(R.string.borrar_dia_texto), R.string.borrar,
                () -> modelo.borrarExcepcion(excepcion.getId()));
    }
}
