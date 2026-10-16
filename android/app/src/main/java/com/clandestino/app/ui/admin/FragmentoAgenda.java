package com.clandestino.app.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.clandestino.app.R;
import com.clandestino.app.modelo.Cita;
import com.clandestino.app.modelovista.ModeloVistaCitasAdmin;
import com.clandestino.app.ui.FragmentoPanel;
import com.clandestino.app.ui.Pantallas;
import com.clandestino.app.utilidades.Formatos;
import com.clandestino.app.utilidades.Resultado;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * D2 · Agenda de la semana. Se piden a la API las citas confirmadas de lunes a domingo de una
 * vez, y al tocar un día de la tira solo se filtra lo que ya está cargado.
 */
public class FragmentoAgenda extends FragmentoPanel {

    private static final String CLAVE_DIA = "dia_elegido";

    private ModeloVistaCitasAdmin modelo;
    private SwipeRefreshLayout deslizar;
    private TextView semana;
    private LinearLayout tiraDias;
    private TextView tituloDia;
    private TextView numeroCitas;
    private View vacio;
    private AdaptadorAgenda adaptador;

    private LocalDate diaElegido;
    private List<Cita> citasSemana = Collections.emptyList();

    public FragmentoAgenda() {
        super(R.layout.fragmento_agenda);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estadoGuardado) {
        modelo = new ViewModelProvider(this).get(ModeloVistaCitasAdmin.class);
        String guardado = estadoGuardado == null ? null : estadoGuardado.getString(CLAVE_DIA);
        diaElegido = guardado == null ? Formatos.hoy() : LocalDate.parse(guardado);

        ponerCabecera(vista, getString(R.string.agenda_ceja), R.string.agenda_titulo);
        mostrarBoton(vista, R.id.boton_refrescar, v -> modelo.recargar());
        buscarVistas(vista);
        crearTira();

        adaptador = new AdaptadorAgenda(new AdaptadorAgenda.Acciones() {
            @Override
            public void marcarAsistencia(Cita cita, boolean vino) {
                modelo.marcarAsistencia(cita.getId(), vino);
            }

            @Override
            public void cancelar(Cita cita) {
                preguntarCancelar(cita);
            }
        });
        ((RecyclerView) vista.findViewById(R.id.lista)).setAdapter(adaptador);

        modelo.getCitas().observe(getViewLifecycleOwner(), this::pintar);
        avisarOperaciones(modelo);
        if (modelo.getCitas().getValue() == null) {
            cargarSemana();
        }
        pintarSemana();
    }

    private void buscarVistas(View vista) {
        semana = vista.findViewById(R.id.semana);
        tiraDias = vista.findViewById(R.id.tira_dias);
        tituloDia = vista.findViewById(R.id.titulo_dia);
        numeroCitas = vista.findViewById(R.id.numero_citas);
        vacio = vista.findViewById(R.id.vacio);
        deslizar = vista.findViewById(R.id.deslizar);
        deslizar.setColorSchemeResources(R.color.acento);
        deslizar.setOnRefreshListener(modelo::recargar);
        vista.findViewById(R.id.boton_semana_anterior).setOnClickListener(v -> cambiarSemana(-1));
        vista.findViewById(R.id.boton_semana_siguiente).setOnClickListener(v -> cambiarSemana(1));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle estado) {
        super.onSaveInstanceState(estado);
        if (diaElegido != null) {
            estado.putString(CLAVE_DIA, diaElegido.toString());
        }
    }

    @Override
    public void onHiddenChanged(boolean escondido) {
        super.onHiddenChanged(escondido);
        // Al volver de Solicitudes puede haber citas recién confirmadas.
        if (!escondido && modelo != null) {
            modelo.recargar();
        }
    }

    /** Las siete casillas de la tira se crean una vez; luego solo se repintan. */
    private void crearTira() {
        LayoutInflater inflador = getLayoutInflater();
        for (int i = 0; i < 7; i++) {
            View casilla = inflador.inflate(R.layout.item_dia_semana, tiraDias, false);
            int desplazamiento = i;
            casilla.setOnClickListener(v -> elegirDia(lunes().plusDays(desplazamiento)));
            tiraDias.addView(casilla);
        }
    }

    private LocalDate lunes() {
        return diaElegido.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private void cargarSemana() {
        modelo.cargar("confirmada", lunes(), lunes().plusDays(6));
    }

    /** Pasa a la semana de antes o de después dejando elegido el mismo día de la semana. */
    private void cambiarSemana(int semanas) {
        diaElegido = diaElegido.plusWeeks(semanas);
        pintarSemana();
        cargarSemana();
    }

    private void elegirDia(LocalDate fecha) {
        diaElegido = fecha;
        pintarSemana();
    }

    private void pintar(Resultado<List<Cita>> resultado) {
        deslizar.setRefreshing(resultado.estaCargando());
        if (resultado.datos() != null) {
            citasSemana = resultado.datos();
            pintarSemana();
        }
        Pantallas.avisarSiFalla(requireActivity(), resultado);
    }

    private void pintarSemana() {
        semana.setText(Formatos.semana(lunes()));
        for (int i = 0; i < 7; i++) {
            pintarCasilla(tiraDias.getChildAt(i), lunes().plusDays(i));
        }
        List<Cita> delDia = citasDe(diaElegido);
        tituloDia.setText(Formatos.diaLargo(diaElegido));
        numeroCitas.setText(getResources().getQuantityString(R.plurals.citas_del_dia,
                delDia.size(), delDia.size()));
        adaptador.mostrar(delDia, Formatos.ahora());
        vacio.setVisibility(delDia.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void pintarCasilla(View casilla, LocalDate fecha) {
        boolean elegida = fecha.equals(diaElegido);
        casilla.setBackgroundResource(elegida ? R.drawable.dia_elegido : R.drawable.celda_libre);
        casilla.setContentDescription(Formatos.diaLargo(fecha));

        TextView nombre = casilla.findViewById(R.id.dia_semana);
        TextView numero = casilla.findViewById(R.id.numero);
        nombre.setText(Formatos.diaDeLaSemanaCorto(fecha));
        numero.setText(String.valueOf(fecha.getDayOfMonth()));
        nombre.setTextColor(color(elegida ? R.color.acento_tinta : R.color.secundario));
        numero.setTextColor(color(elegida ? R.color.acento_tinta : R.color.tinta));
        // INVISIBLE y no GONE: así todas las casillas miden lo mismo, tengan punto o no.
        casilla.findViewById(R.id.punto)
                .setVisibility(citasDe(fecha).isEmpty() ? View.INVISIBLE : View.VISIBLE);
    }

    private int color(int recurso) {
        return ContextCompat.getColor(requireContext(), recurso);
    }

    private List<Cita> citasDe(LocalDate fecha) {
        List<Cita> delDia = new ArrayList<>();
        for (Cita cita : citasSemana) {
            if (cita.getFecha().equals(fecha)) {
                delDia.add(cita);
            }
        }
        return delDia;
    }

    private void preguntarCancelar(Cita cita) {
        String titulo = getString(R.string.cancelar_cita_cliente_pregunta, cita.getClienteUsuario());
        String texto = getString(R.string.cancelar_cita_cliente_texto,
                Formatos.diaYMes(cita.getFecha()), Formatos.hora(cita.horaEfectiva()));
        Pantallas.confirmar(requireContext(), titulo, texto, R.string.si_cancelar,
                () -> modelo.cancelar(cita.getId()));
    }
}
