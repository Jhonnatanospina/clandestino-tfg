package com.clandestino.app.ui.admin;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.clandestino.app.R;
import com.clandestino.app.modelo.Usuario;
import com.clandestino.app.modelovista.ModeloVistaCitasAdmin;
import com.clandestino.app.utilidades.Formatos;
import com.clandestino.app.utilidades.Resultado;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * D5 · Diálogo de la agenda para meter a mano la cita de un cliente que ha llamado por
 * teléfono o se ha pasado por la barbería. Usa el modelo de vista de FragmentoAgenda, así que
 * al guardar la agenda se recarga sola y el aviso lo enseña la pantalla.
 */
public class DialogoCitaManual extends DialogFragment {

    /** Lo que recibe la agenda al guardar, para irse al día de la cita nueva. */
    static final String RESULTADO = "cita_manual";
    static final String FECHA = "fecha";

    private static final String CALENDARIO = "calendario";
    private static final String CLIENTE_ID = "cliente_id";
    private static final String CLIENTE_USUARIO = "cliente_usuario";
    private static final String HORA = "hora";
    private static final long SIN_CLIENTE = -1;

    private ModeloVistaCitasAdmin modelo;
    private List<Usuario> clientes = new ArrayList<>();
    private long clienteId = SIN_CLIENTE;
    private String clienteUsuario;
    private LocalDate fecha;
    private LocalTime hora;

    private TextInputLayout cajaCliente;
    private MaterialAutoCompleteTextView campoCliente;
    private MaterialButton botonDia;
    private MaterialAutoCompleteTextView campoHora;
    private TextView error;

    /** @param dia el que se ve en la agenda; sale ya elegido, salvo que haya pasado */
    static DialogoCitaManual conDia(LocalDate dia) {
        Bundle argumentos = new Bundle();
        argumentos.putString(FECHA, dia.toString());
        DialogoCitaManual dialogo = new DialogoCitaManual();
        dialogo.setArguments(argumentos);
        return dialogo;
    }

    @SuppressLint("UseGetLayoutInflater")
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle estadoGuardado) {
        modelo = new ViewModelProvider(requireParentFragment()).get(ModeloVistaCitasAdmin.class);
        recuperar(estadoGuardado);
        MaterialAlertDialogBuilder constructor = new MaterialAlertDialogBuilder(requireContext());
        // Como en DialogoExcepcion: el contexto del constructor es el que lleva el tema de los
        // diálogos de Material.
        View vista = LayoutInflater.from(constructor.getContext())
                .inflate(R.layout.dialogo_cita_manual, null);
        enlazar(vista);
        reengancharCalendario();
        pintar();

        modelo.getClientes().observe(this, this::pintarClientes);
        // Al girar el móvil sirve la lista que ya había. Si no hay ninguna (la primera vez, o
        // Android cerró la app por detrás) se pide.
        if (estadoGuardado == null || modelo.getClientes().getValue() == null) {
            modelo.cargarClientes();
        }
        return constructor
                .setTitle(R.string.nueva_cita)
                .setView(vista)
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(R.string.guardar, null)
                .create();
    }

    /** Igual que en DialogoExcepcion: si falta algo, el diálogo se queda abierto con el error. */
    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialogo = (AlertDialog) requireDialog();
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> guardar());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle estado) {
        super.onSaveInstanceState(estado);
        estado.putLong(CLIENTE_ID, clienteId);
        estado.putString(CLIENTE_USUARIO, clienteUsuario);
        estado.putString(FECHA, fecha == null ? null : fecha.toString());
        estado.putString(HORA, hora == null ? null : hora.toString());
    }

    /** Lo elegido antes de girar el móvil o, al abrir, el día que viene de la agenda. */
    private void recuperar(@Nullable Bundle estado) {
        if (estado == null) {
            LocalDate dia = LocalDate.parse(requireArguments().getString(FECHA));
            fecha = dia.isBefore(Formatos.hoy()) ? null : dia;
            return;
        }
        clienteId = estado.getLong(CLIENTE_ID);
        clienteUsuario = estado.getString(CLIENTE_USUARIO);
        String guardadaFecha = estado.getString(FECHA);
        String guardadaHora = estado.getString(HORA);
        fecha = guardadaFecha == null ? null : LocalDate.parse(guardadaFecha);
        hora = guardadaHora == null ? null : LocalTime.parse(guardadaHora);
    }

    private void enlazar(View vista) {
        cajaCliente = vista.findViewById(R.id.caja_cliente);
        campoCliente = vista.findViewById(R.id.campo_cliente);
        botonDia = vista.findViewById(R.id.boton_dia);
        campoHora = vista.findViewById(R.id.campo_hora);
        error = vista.findViewById(R.id.error);

        campoCliente.setOnItemClickListener((lista, fila, posicion, id) -> {
            Usuario elegido = clientes.get(posicion);
            clienteId = elegido.getId();
            clienteUsuario = elegido.getUsuario();
            error.setVisibility(View.GONE);
        });
        botonDia.setOnClickListener(v -> elegirDia());
        campoHora.setAdapter(new ArrayAdapter<>(vista.getContext(),
                android.R.layout.simple_list_item_1, Selectores.HORAS_FINALES));
        campoHora.setOnItemClickListener((lista, fila, posicion, id) -> {
            hora = LocalTime.parse(Selectores.HORAS_FINALES.get(posicion));
            error.setVisibility(View.GONE);
        });
    }

    private void pintar() {
        // OJO: con "false" el desplegable no filtra; si no, solo enseñaría la opción puesta.
        campoCliente.setText(clienteUsuario == null ? null
                : getString(R.string.arroba_usuario, clienteUsuario), false);
        botonDia.setText(fecha == null ? getString(R.string.toca_para_elegir)
                : Formatos.diaLargo(fecha));
        campoHora.setText(hora == null ? null : Formatos.hora(hora), false);
        error.setVisibility(View.GONE);
    }

    /** Los clientes aprobados por orden alfabético o, si no hay ninguno, por qué. */
    private void pintarClientes(Resultado<List<Usuario>> resultado) {
        if (resultado.haFallado()) {
            cajaCliente.setEnabled(false);
            cajaCliente.setError(resultado.error().getMessage());
            return;
        }
        if (resultado.datos() == null) {
            // Es la primera carga y aún no ha llegado.
            cajaCliente.setEnabled(false);
            return;
        }
        // La API los da del más nuevo al más antiguo; para buscar a alguien va mejor por nombre.
        clientes = new ArrayList<>(resultado.datos());
        clientes.sort(Comparator.comparing(Usuario::getUsuario));
        List<String> nombres = new ArrayList<>();
        for (Usuario cliente : clientes) {
            nombres.add(getString(R.string.arroba_usuario, cliente.getUsuario()));
        }
        campoCliente.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, nombres));
        cajaCliente.setEnabled(!clientes.isEmpty());
        cajaCliente.setError(clientes.isEmpty()
                ? getString(R.string.sin_clientes_aprobados) : null);
    }

    /** Calendario de Material desde hoy, abierto en el día que ya hubiera elegido. */
    private void elegirDia() {
        MaterialDatePicker.Builder<Long> constructor = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.dia)
                .setCalendarConstraints(new CalendarConstraints.Builder()
                        .setValidator(DateValidatorPointForward.now())
                        .build());
        if (fecha != null) {
            constructor.setSelection(fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
        }
        MaterialDatePicker<Long> calendario = constructor.build();
        escucharDia(calendario);
        calendario.show(getChildFragmentManager(), CALENDARIO);
    }

    private void escucharDia(MaterialDatePicker<Long> calendario) {
        calendario.addOnPositiveButtonClickListener(dia -> {
            // El calendario devuelve milisegundos en UTC a medianoche, no en hora local.
            fecha = Instant.ofEpochMilli(dia).atZone(ZoneOffset.UTC).toLocalDate();
            pintar();
        });
    }

    /** Tras girar el móvil, el calendario que estaba abierto vuelve sin su listener. */
    @SuppressWarnings("unchecked")
    private void reengancharCalendario() {
        Object calendario = getChildFragmentManager().findFragmentByTag(CALENDARIO);
        if (calendario != null) {
            escucharDia((MaterialDatePicker<Long>) calendario);
        }
    }

    private void guardar() {
        String problema = problema();
        if (problema != null) {
            error.setText(problema);
            error.setVisibility(View.VISIBLE);
            return;
        }
        modelo.crearCita(clienteId, fecha, hora);
        Bundle resultado = new Bundle();
        resultado.putString(FECHA, fecha.toString());
        getParentFragmentManager().setFragmentResult(RESULTADO, resultado);
        dismiss();
    }

    /**
     * Lo que se puede ver sin preguntar a la API. Que la hora no esté cogida ya lo dice ella.
     * Null si todo está bien.
     */
    @Nullable
    private String problema() {
        if (clienteId == SIN_CLIENTE || fecha == null || hora == null) {
            return getString(R.string.faltan_datos_cita);
        }
        if (!fecha.atTime(hora).isAfter(Formatos.ahora())) {
            return getString(R.string.hora_ya_pasada);
        }
        return null;
    }
}
