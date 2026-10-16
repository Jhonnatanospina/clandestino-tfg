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
import com.clandestino.app.modelo.EstadoUsuario;
import com.clandestino.app.modelo.Usuario;
import com.clandestino.app.ui.Pantallas;
import com.clandestino.app.utilidades.Formatos;

import java.util.Collections;
import java.util.List;

/** Lista de D4 con los clientes y los botones para aprobarlos o bloquearlos. */
public class AdaptadorClientes extends RecyclerView.Adapter<AdaptadorClientes.Fila> {

    /** Lo que hace la pantalla con cada botón. */
    public interface Acciones {
        void aprobar(Usuario cliente);

        void bloquear(Usuario cliente);
    }

    private final Acciones acciones;
    private List<Usuario> clientes = Collections.emptyList();

    public AdaptadorClientes(Acciones acciones) {
        this.acciones = acciones;
    }

    // Suelen ser pocas filas: se repintan todas.
    @SuppressLint("NotifyDataSetChanged")
    public void mostrar(List<Usuario> clientes) {
        this.clientes = clientes;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return clientes.size();
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        View vista = LayoutInflater.from(padre.getContext())
                .inflate(R.layout.item_cliente, padre, false);
        return new Fila(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Fila fila, int posicion) {
        fila.pintar(clientes.get(posicion), posicion == 0);
    }

    class Fila extends RecyclerView.ViewHolder {
        private final View separador;
        private final TextView usuario;
        private final TextView telefono;
        private final TextView chipEstado;
        private final View botonAprobar;
        private final View botonBloquear;

        Fila(View vista) {
            super(vista);
            separador = vista.findViewById(R.id.separador);
            usuario = vista.findViewById(R.id.usuario);
            telefono = vista.findViewById(R.id.telefono);
            chipEstado = vista.findViewById(R.id.chip_estado);
            botonAprobar = vista.findViewById(R.id.boton_aprobar);
            botonBloquear = vista.findViewById(R.id.boton_bloquear);
        }

        void pintar(Usuario cliente, boolean primera) {
            Context contexto = itemView.getContext();
            separador.setVisibility(primera ? View.GONE : View.VISIBLE);
            usuario.setText(contexto.getString(R.string.arroba_usuario, cliente.getUsuario()));
            telefono.setText(Formatos.telefono(cliente.getTelefono()));
            EstadoUsuario estado = cliente.getEstado();
            Pantallas.pintarChip(chipEstado, estado.etiqueta(), cliente.estaAprobado());

            // Un bloqueado se desbloquea aprobándolo otra vez; un aprobado no necesita Aprobar.
            botonAprobar.setVisibility(cliente.estaAprobado() ? View.GONE : View.VISIBLE);
            botonBloquear.setVisibility(estado == EstadoUsuario.BLOQUEADO ? View.GONE : View.VISIBLE);
            botonAprobar.setOnClickListener(v -> acciones.aprobar(cliente));
            botonBloquear.setOnClickListener(v -> acciones.bloquear(cliente));
        }
    }
}
