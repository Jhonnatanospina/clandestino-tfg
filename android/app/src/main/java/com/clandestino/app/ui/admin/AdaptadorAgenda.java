package com.clandestino.app.ui.admin;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clandestino.app.R;
import com.clandestino.app.modelo.Cita;
import com.clandestino.app.ui.Pantallas;
import com.clandestino.app.utilidades.Formatos;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/** Tarjetas de D2: las citas confirmadas del día elegido en la tira de la semana. */
public class AdaptadorAgenda extends RecyclerView.Adapter<AdaptadorAgenda.Tarjeta> {

    /** Lo que hace la pantalla con cada botón de la tarjeta. */
    public interface Acciones {
        void marcarAsistencia(Cita cita, boolean vino);

        void cancelar(Cita cita);
    }

    private final Acciones acciones;
    private List<Cita> citas = Collections.emptyList();
    private LocalDateTime ahora = Formatos.ahora();

    public AdaptadorAgenda(Acciones acciones) {
        this.acciones = acciones;
    }

    /** @param ahora para saber qué citas han pasado ya y qué botones se activan */
    // Un día tiene pocas citas: se repintan todas.
    @SuppressLint("NotifyDataSetChanged")
    public void mostrar(List<Cita> citas, LocalDateTime ahora) {
        this.citas = citas;
        this.ahora = ahora;
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
                .inflate(R.layout.item_cita_agenda, padre, false);
        return new Tarjeta(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Tarjeta tarjeta, int posicion) {
        tarjeta.pintar(citas.get(posicion));
    }

    class Tarjeta extends RecyclerView.ViewHolder {
        private final TextView hora;
        private final TextView usuario;
        private final TextView telefono;
        private final TextView chipEstado;
        private final View botonAsistio;
        private final View botonNoVino;
        private final View botonCancelar;

        Tarjeta(View vista) {
            super(vista);
            hora = vista.findViewById(R.id.hora);
            usuario = vista.findViewById(R.id.usuario);
            telefono = vista.findViewById(R.id.telefono);
            chipEstado = vista.findViewById(R.id.chip_estado);
            botonAsistio = vista.findViewById(R.id.boton_asistio);
            botonNoVino = vista.findViewById(R.id.boton_no_vino);
            botonCancelar = vista.findViewById(R.id.boton_cancelar);
        }

        void pintar(Cita cita) {
            hora.setText(Formatos.hora(cita.horaEfectiva()));
            usuario.setText(itemView.getContext().getString(R.string.arroba_usuario,
                    cita.getClienteUsuario()));
            telefono.setText(Formatos.telefono(cita.getClienteTelefono()));
            Pantallas.pintarChip(chipEstado, cita.getEstado().etiqueta(), cita.getEstado().destacado());

            // La API lo comprobaría igual (cita_futura), pero así ni se ofrece lo que no se puede.
            boolean pasada = cita.yaHaPasado(ahora);
            botonAsistio.setEnabled(pasada);
            botonNoVino.setEnabled(pasada);
            botonCancelar.setEnabled(!pasada);

            botonAsistio.setOnClickListener(v -> acciones.marcarAsistencia(cita, true));
            botonNoVino.setOnClickListener(v -> acciones.marcarAsistencia(cita, false));
            botonCancelar.setOnClickListener(v -> acciones.cancelar(cita));
        }
    }
}
