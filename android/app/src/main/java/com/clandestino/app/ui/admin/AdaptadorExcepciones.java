package com.clandestino.app.ui.admin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clandestino.app.R;
import com.clandestino.app.modelo.ExcepcionDia;
import com.clandestino.app.ui.Pantallas;
import com.clandestino.app.utilidades.Formatos;

import java.util.Collections;
import java.util.List;

/** Lista de D3 con los días de vacaciones y los días extra, de hoy en adelante. */
public class AdaptadorExcepciones extends RecyclerView.Adapter<AdaptadorExcepciones.Fila> {

    /** Lo que hace la pantalla al pulsar la X de una fila. */
    public interface AlBorrar {
        void borrar(ExcepcionDia excepcion);
    }

    private final AlBorrar alBorrar;
    private List<ExcepcionDia> excepciones = Collections.emptyList();

    public AdaptadorExcepciones(AlBorrar alBorrar) {
        this.alBorrar = alBorrar;
    }

    // Suelen ser pocas filas: se repintan todas.
    @SuppressLint("NotifyDataSetChanged")
    public void mostrar(List<ExcepcionDia> excepciones) {
        this.excepciones = excepciones;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return excepciones.size();
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        View vista = LayoutInflater.from(padre.getContext())
                .inflate(R.layout.item_excepcion, padre, false);
        return new Fila(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        fila.pintar(excepciones.get(posicion), posicion == 0);
    }

    class Fila extends RecyclerView.ViewHolder {
        private final View separador;
        private final TextView fecha;
        private final TextView detalle;
        private final TextView chipEstado;

        Fila(View vista) {
            super(vista);
            separador = vista.findViewById(R.id.separador);
            fecha = vista.findViewById(R.id.fecha);
            detalle = vista.findViewById(R.id.detalle);
            chipEstado = vista.findViewById(R.id.chip_estado);
        }

        void pintar(ExcepcionDia excepcion, boolean primera) {
            Context contexto = itemView.getContext();
            separador.setVisibility(primera ? View.GONE : View.VISIBLE);
            fecha.setText(Formatos.diaLargo(excepcion.getFecha()));
            detalle.setText(textoDetalle(contexto, excepcion));
            boolean extra = excepcion.esDiaExtra();
            Pantallas.pintarChip(chipEstado, contexto.getString(extra ? R.string.abierto : R.string.cerrado),
                    extra);
            itemView.findViewById(R.id.boton_borrar).setOnClickListener(v -> alBorrar.borrar(excepcion));
        }

        /** "Vacaciones" (la nota) en un cierre; "10:00–14:00 · Día extra" en un día extra. */
        private String textoDetalle(Context contexto, ExcepcionDia excepcion) {
            String nota = excepcion.getNota();
            if (!excepcion.esDiaExtra()) {
                return nota != null ? nota : contexto.getString(R.string.cerrado_todo_el_dia);
            }
            String explicacion = nota != null ? nota : contexto.getString(R.string.dia_extra);
            return contexto.getString(R.string.tramo_dia_extra, Formatos.hora(excepcion.getInicio()),
                    Formatos.hora(excepcion.getFin()), explicacion);
        }
    }
}
