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
import com.clandestino.app.modelo.Invitacion;
import com.clandestino.app.ui.Pantallas;
import com.clandestino.app.utilidades.Formatos;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

/** Lista de D4 con los códigos de invitación generados, del más nuevo al más viejo. */
public class AdaptadorInvitaciones extends RecyclerView.Adapter<AdaptadorInvitaciones.Fila> {

    private List<Invitacion> invitaciones = Collections.emptyList();
    private LocalDate hoy = LocalDate.now();

    // Suelen ser pocas filas: se repintan todas.
    @SuppressLint("NotifyDataSetChanged")
    public void mostrar(List<Invitacion> invitaciones, LocalDate hoy) {
        this.invitaciones = invitaciones;
        this.hoy = hoy;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return invitaciones.size();
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        View vista = LayoutInflater.from(padre.getContext())
                .inflate(R.layout.item_invitacion, padre, false);
        return new Fila(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        fila.pintar(invitaciones.get(posicion), posicion == 0);
    }

    class Fila extends RecyclerView.ViewHolder {
        private final View separador;
        private final TextView codigo;
        private final TextView detalle;
        private final TextView chipEstado;

        Fila(View vista) {
            super(vista);
            separador = vista.findViewById(R.id.separador);
            codigo = vista.findViewById(R.id.codigo);
            detalle = vista.findViewById(R.id.detalle);
            chipEstado = vista.findViewById(R.id.chip_estado);
        }

        void pintar(Invitacion invitacion, boolean primera) {
            Context contexto = itemView.getContext();
            separador.setVisibility(primera ? View.GONE : View.VISIBLE);
            codigo.setText(invitacion.getCodigo());
            String estado = textoEstado(contexto, invitacion);
            detalle.setText(invitacion.getNota() == null ? estado
                    : contexto.getString(R.string.detalle_invitacion, invitacion.getNota(), estado));
            Pantallas.pintarChip(chipEstado, invitacion.etiqueta(), invitacion.estaUsada());
        }

        /** "usada por @diego", "caduca en 3 días" o "caducó el 24 de enero". */
        private String textoEstado(Context contexto, Invitacion invitacion) {
            if (invitacion.estaUsada()) {
                return contexto.getString(R.string.usada_por, invitacion.getUsadaPor());
            }
            LocalDate caduca = invitacion.getCaducaEn().toLocalDate();
            if (invitacion.estaCaducada()) {
                return contexto.getString(R.string.caduco_el, Formatos.diaYMes(caduca));
            }
            int dias = (int) ChronoUnit.DAYS.between(hoy, caduca);
            if (dias <= 0) {
                return contexto.getString(R.string.caduca_hoy);
            }
            if (dias == 1) {
                return contexto.getString(R.string.caduca_manana);
            }
            return contexto.getResources().getQuantityString(R.plurals.caduca_en_dias, dias, dias);
        }
    }
}
