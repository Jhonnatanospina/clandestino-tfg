package com.clandestino.app.ui.cliente;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.clandestino.app.R;
import com.clandestino.app.modelo.Dia;
import com.clandestino.app.modelo.Hueco;
import com.clandestino.app.modelovista.ModeloVistaPedirCita;
import com.clandestino.app.ui.FragmentoPanel;
import com.clandestino.app.ui.Pantallas;
import com.clandestino.app.utilidades.Formatos;
import com.clandestino.app.utilidades.Resultado;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * C2 · Pedir cita. El cliente toca un día libre, se cargan sus horas, elige una y pide.
 *
 * El día y la hora elegidos son de la pantalla, no de la API, así que se guardan en el Bundle
 * (onSaveInstanceState) para no perderlos al girar el móvil.
 */
public class FragmentoPedirCita extends FragmentoPanel {

    private static final String CLAVE_DIA = "dia_elegido";
    private static final String CLAVE_HORA = "hora_elegida";

    private ModeloVistaPedirCita modelo;
    private SwipeRefreshLayout deslizar;
    private TextView mes;
    private TextView tituloHoras;
    private TextView ayudaHoras;
    private ChipGroup horas;
    private MaterialButton botonPedir;
    private AdaptadorCalendario calendario;

    private LocalDate diaElegido;
    private LocalTime horaElegida;
    private boolean pidiendo;

    public FragmentoPedirCita() {
        super(R.layout.fragmento_pedir_cita);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estadoGuardado) {
        modelo = new ViewModelProvider(this).get(ModeloVistaPedirCita.class);
        recuperar(estadoGuardado);
        ponerCabecera(vista, getString(R.string.pedir_ceja), R.string.pedir_titulo);

        deslizar = vista.findViewById(R.id.deslizar);
        mes = vista.findViewById(R.id.mes);
        tituloHoras = vista.findViewById(R.id.titulo_horas);
        ayudaHoras = vista.findViewById(R.id.ayuda_horas);
        horas = vista.findViewById(R.id.horas);
        botonPedir = vista.findViewById(R.id.boton_pedir);

        pintarLetras(vista.findViewById(R.id.letras));
        calendario = new AdaptadorCalendario(this::elegirDia);
        RecyclerView rejilla = vista.findViewById(R.id.calendario);
        rejilla.setLayoutManager(new GridLayoutManager(requireContext(), 7));
        rejilla.setAdapter(calendario);

        deslizar.setColorSchemeResources(R.color.acento);
        deslizar.setOnRefreshListener(modelo::cargarDias);
        botonPedir.setOnClickListener(v -> modelo.pedir(diaElegido, horaElegida));

        modelo.getDias().observe(getViewLifecycleOwner(), this::pintarDias);
        modelo.getHuecos().observe(getViewLifecycleOwner(), this::pintarHuecos);
        modelo.getOperacion().observe(getViewLifecycleOwner(), this::pintarOperacion);
        if (modelo.getDias().getValue() == null) {
            modelo.cargarDias();
        }
        pintarBoton();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle estado) {
        super.onSaveInstanceState(estado);
        // Se guardan como texto ("2027-02-11", "19:00"): LocalDate no se puede meter tal cual.
        if (diaElegido != null) {
            estado.putString(CLAVE_DIA, diaElegido.toString());
        }
        if (horaElegida != null) {
            estado.putString(CLAVE_HORA, horaElegida.toString());
        }
    }

    private void recuperar(@Nullable Bundle estado) {
        if (estado == null) {
            return;
        }
        String dia = estado.getString(CLAVE_DIA);
        String hora = estado.getString(CLAVE_HORA);
        diaElegido = dia == null ? null : LocalDate.parse(dia);
        horaElegida = hora == null ? null : LocalTime.parse(hora);
    }

    /** Las letras L M X J V S D encima de cada columna del calendario. */
    private void pintarLetras(LinearLayout fila) {
        for (char letra : getString(R.string.letras_semana).toCharArray()) {
            TextView texto = new TextView(requireContext(), null, 0, R.style.Texto_Ceja);
            texto.setText(String.valueOf(letra));
            texto.setGravity(Gravity.CENTER);
            fila.addView(texto, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private void elegirDia(LocalDate fecha) {
        diaElegido = fecha;
        horaElegida = null;
        calendario.marcar(fecha);
        modelo.cargarHuecos(fecha);
        pintarBoton();
    }

    private void pintarDias(Resultado<List<Dia>> resultado) {
        deslizar.setRefreshing(resultado.estaCargando());
        List<Dia> dias = resultado.datos();
        if (dias != null && !dias.isEmpty()) {
            mes.setText(Formatos.meses(dias.get(0).getFecha(), dias.get(dias.size() - 1).getFecha()));
            comprobarDiaElegido(dias);
            calendario.mostrar(dias, Formatos.hoy());
            calendario.marcar(diaElegido);
        }
        Pantallas.avisarSiFalla(requireActivity(), resultado);
    }

    /**
     * El día elegido puede haberse llenado mientras tanto (o haber salido de los 14 días si la
     * app llevaba abierta desde ayer). En ese caso se suelta y el cliente elige otro.
     */
    private void comprobarDiaElegido(List<Dia> dias) {
        if (diaElegido == null) {
            return;
        }
        boolean sigueLibre = false;
        for (Dia dia : dias) {
            if (dia.getFecha().equals(diaElegido) && dia.estaLibre()) {
                sigueLibre = true;
            }
        }
        if (!sigueLibre) {
            diaElegido = null;
            horaElegida = null;
            modelo.olvidarHuecos();
        } else if (modelo.getHuecos().getValue() == null) {
            // Vuelve de un cierre del proceso: el día estaba en el Bundle, pero las horas no.
            modelo.cargarHuecos(diaElegido);
        }
    }

    private void pintarHuecos(Resultado<List<Hueco>> resultado) {
        tituloHoras.setText(diaElegido == null
                ? getString(R.string.horas)
                : getString(R.string.horas_del, Formatos.diaCorto(diaElegido)));
        horas.removeAllViews();
        List<Hueco> huecos = resultado == null ? null : resultado.datos();
        boolean hayLibres = false;
        boolean horaSigueLibre = false;
        if (huecos != null) {
            int ancho = anchoHora();
            for (Hueco hueco : huecos) {
                horas.addView(crearHora(hueco, ancho));
                hayLibres |= !hueco.estaOcupado();
                horaSigueLibre |= !hueco.estaOcupado() && hueco.getHora().equals(horaElegida);
            }
        }
        // Con las horas ya cargadas, si la elegida se ha ocupado (otro cliente) se suelta.
        if (huecos != null && !horaSigueLibre) {
            horaElegida = null;
        }
        pintarAyudaHoras(huecos != null, hayLibres);
        pintarBoton();
        Pantallas.avisarSiFalla(requireActivity(), resultado);
    }

    private Chip crearHora(Hueco hueco, int ancho) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_hora, horas, false);
        LocalTime hora = hueco.getHora();
        chip.setText(Formatos.hora(hora));
        chip.getLayoutParams().width = ancho;
        chip.setEnabled(!hueco.estaOcupado());
        if (hueco.estaOcupado()) {
            chip.setPaintFlags(chip.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        chip.setChecked(!hueco.estaOcupado() && hora.equals(horaElegida));
        // El listener va después de setChecked para que marcar la hora guardada no cuente
        // como si el cliente la hubiera tocado.
        chip.setOnCheckedChangeListener((boton, marcada) -> {
            if (marcada) {
                horaElegida = hora;
            } else if (hora.equals(horaElegida)) {
                horaElegida = null;
            }
            pintarBoton();
        });
        return chip;
    }

    /** Tres horas por fila: el ancho de la pantalla menos los márgenes y los dos huecos. */
    private int anchoHora() {
        int pantalla = getResources().getDisplayMetrics().widthPixels;
        int margenes = 2 * getResources().getDimensionPixelSize(R.dimen.margen_pantalla);
        int separaciones = 2 * getResources().getDimensionPixelSize(R.dimen.separacion_horas);
        return (pantalla - margenes - separaciones) / 3;
    }

    private void pintarAyudaHoras(boolean hayHoras, boolean hayLibres) {
        if (diaElegido == null) {
            ayudaHoras.setText(R.string.elige_un_dia);
            ayudaHoras.setVisibility(View.VISIBLE);
        } else if (hayHoras && !hayLibres) {
            ayudaHoras.setText(R.string.sin_horas_libres);
            ayudaHoras.setVisibility(View.VISIBLE);
        } else {
            ayudaHoras.setVisibility(View.GONE);
        }
    }

    private void pintarBoton() {
        boolean listo = diaElegido != null && horaElegida != null;
        botonPedir.setEnabled(listo && !pidiendo);
        if (listo) {
            String dia = Formatos.diaDeLaSemanaCorto(diaElegido) + " " + diaElegido.getDayOfMonth();
            botonPedir.setText(getString(R.string.pedir_cita_boton, dia, Formatos.hora(horaElegida)));
        } else {
            botonPedir.setText(R.string.elige_dia_y_hora);
        }
    }

    /** Si la cita se ha pedido, se lleva al cliente a "Mis citas", donde ya aparece pendiente. */
    private void pintarOperacion(Resultado<String> resultado) {
        pidiendo = resultado != null && resultado.estaCargando();
        if (Pantallas.avisarOperacion(requireActivity(), resultado)) {
            horaElegida = null;
            panel().irAPestana(R.id.pestana_mis_citas);
        }
        pintarBoton();
    }
}
