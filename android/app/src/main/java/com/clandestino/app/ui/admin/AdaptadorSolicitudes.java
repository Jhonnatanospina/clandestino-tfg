package com.clandestino.app.ui.admin;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clandestino.app.R;
import com.clandestino.app.modelo.Cita;
import com.clandestino.app.ui.Pantallas;
import com.clandestino.app.utilidades.Formatos;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tarjetas de D1: cada solicitud pendiente con su hora final y los botones de decidir. */
public class AdaptadorSolicitudes extends RecyclerView.Adapter<AdaptadorSolicitudes.Tarjeta> {

    /** Lo que hace la pantalla con cada botón. */
    public interface Acciones {
        void confirmar(Cita cita, LocalTime horaFinal);

        void rechazar(Cita cita);
    }

    // DP-04: la hora final se elige de 08:00 a 22:00 cada media hora, igual que en la API.
    private static final LocalTime PRIMERA_HORA = LocalTime.of(8, 0);
    private static final LocalTime ULTIMA_HORA = LocalTime.of(22, 0);
    private static final List<String> HORAS_POSIBLES = horasPosibles();

    private final Acciones acciones;
    private List<Cita> citas = Collections.emptyList();
    // La hora que ha elegido el barbero en cada tarjeta, por id de cita. Si no se guardara aquí,
    // al reciclar las vistas (hacer scroll) volvería a salir la hora pedida.
    private final Map<Long, LocalTime> horasElegidas = new HashMap<>();

    public AdaptadorSolicitudes(Acciones acciones) {
        this.acciones = acciones;
    }

    private static List<String> horasPosibles() {
        List<String> horas = new ArrayList<>();
        for (LocalTime hora = PRIMERA_HORA; !hora.isAfter(ULTIMA_HORA); hora = hora.plusMinutes(30)) {
            horas.add(Formatos.hora(hora));
        }
        return horas;
    }

    // Son pocas solicitudes a la vez: se repintan todas.
    @SuppressLint("NotifyDataSetChanged")
    public void mostrar(List<Cita> citas) {
        this.citas = citas;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return citas.size();
    }

    @NonNull
    @Override
    public Tarjeta onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        View vista = LayoutInflater.from(padre.getContext())
                .inflate(R.layout.item_solicitud, padre, false);
        return new Tarjeta(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Tarjeta tarjeta, int posicion) {
        tarjeta.pintar(citas.get(posicion));
    }

    class Tarjeta extends RecyclerView.ViewHolder {
        private final TextView usuario;
        private final TextView chipEstado;
        private final TextView detalle;
        private final MaterialAutoCompleteTextView horaFinal;

        Tarjeta(View vista) {
            super(vista);
            usuario = vista.findViewById(R.id.usuario);
            chipEstado = vista.findViewById(R.id.chip_estado);
            detalle = vista.findViewById(R.id.detalle);
            horaFinal = vista.findViewById(R.id.hora_final);
            horaFinal.setAdapter(new ArrayAdapter<>(vista.getContext(),
                    android.R.layout.simple_list_item_1, HORAS_POSIBLES));
        }

        void pintar(Cita cita) {
            usuario.setText(itemView.getContext().getString(R.string.arroba_usuario,
                    cita.getClienteUsuario()));
            Pantallas.pintarChip(chipEstado, cita.getEstado().etiqueta(), false);
            detalle.setText(itemView.getContext().getString(R.string.pidio_sobre,
                    Formatos.diaCorto(cita.getFecha()), Formatos.hora(cita.getHoraPedida())));

            // Por defecto, la hora que pidió el cliente.
            LocalTime elegida = horasElegidas.getOrDefault(cita.getId(), cita.getHoraPedida());
            // OJO: con "false" no se filtra la lista; si no, el desplegable solo enseñaría esa hora.
            horaFinal.setText(Formatos.hora(elegida), false);
            horaFinal.setOnItemClickListener((lista, fila, posicion, id) ->
                    horasElegidas.put(cita.getId(), LocalTime.parse(HORAS_POSIBLES.get(posicion))));

            itemView.findViewById(R.id.boton_confirmar).setOnClickListener(v ->
                    acciones.confirmar(cita, horasElegidas.getOrDefault(cita.getId(),
                            cita.getHoraPedida())));
            itemView.findViewById(R.id.boton_rechazar).setOnClickListener(v -> acciones.rechazar(cita));
        }
    }
}
