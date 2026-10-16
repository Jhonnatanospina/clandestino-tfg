package com.clandestino.app.ui.cliente;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clandestino.app.R;
import com.clandestino.app.modelo.Cita;
import com.clandestino.app.modelo.EstadoCita;
import com.clandestino.app.ui.Pantallas;
import com.clandestino.app.utilidades.Formatos;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lista de C1 con dos tipos de fila: los títulos de sección (Próximas, Historial) y las
 * tarjetas de cita. Las filas se guardan en una sola lista de Object: un Integer es el texto
 * de un título y una Cita es una tarjeta.
 */
public class AdaptadorMisCitas extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** Lo que hace la pantalla cuando se pulsa "Cancelar" en una tarjeta. */
    public interface AlCancelar {
        void cancelar(Cita cita);
    }

    private static final int TIPO_SECCION = 0;
    private static final int TIPO_CITA = 1;

    // Primero la más cercana; en el historial al revés, la última que pasó arriba.
    private static final Comparator<Cita> POR_FECHA =
            Comparator.comparing(Cita::getFecha).thenComparing(Cita::horaEfectiva);

    private final AlCancelar alCancelar;
    private final List<Object> filas = new ArrayList<>();
    private LocalDateTime ahora = Formatos.ahora();

    public AdaptadorMisCitas(AlCancelar alCancelar) {
        this.alCancelar = alCancelar;
    }

    /**
     * Reparte las citas entre las dos secciones. Una cita es "próxima" si todavía ocupa su hora
     * (pendiente o confirmada) y no ha empezado; todo lo demás va al historial.
     */
    // Son unas pocas tarjetas: repintarlas todas es más sencillo que calcular las diferencias.
    @SuppressLint("NotifyDataSetChanged")
    public void mostrar(List<Cita> citas, LocalDateTime ahora) {
        this.ahora = ahora;
        List<Cita> proximas = new ArrayList<>();
        List<Cita> historial = new ArrayList<>();
        for (Cita cita : citas) {
            if (esProxima(cita)) {
                proximas.add(cita);
            } else {
                historial.add(cita);
            }
        }
        proximas.sort(POR_FECHA);
        historial.sort(POR_FECHA.reversed());

        filas.clear();
        anadirSeccion(R.string.proximas, proximas);
        anadirSeccion(R.string.historial, historial);
        notifyDataSetChanged();
    }

    private void anadirSeccion(int titulo, List<Cita> citas) {
        if (!citas.isEmpty()) {
            filas.add(titulo);
            filas.addAll(citas);
        }
    }

    private boolean esProxima(Cita cita) {
        return cita.getEstado().ocupaHueco() && !cita.yaHaPasado(ahora);
    }

    @Override
    public int getItemViewType(int posicion) {
        return filas.get(posicion) instanceof Cita ? TIPO_CITA : TIPO_SECCION;
    }

    @Override
    public int getItemCount() {
        return filas.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        LayoutInflater inflador = LayoutInflater.from(padre.getContext());
        if (tipo == TIPO_SECCION) {
            return new Seccion(inflador.inflate(R.layout.item_seccion, padre, false));
        }
        return new Tarjeta(inflador.inflate(R.layout.item_cita_cliente, padre, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder fila, int posicion) {
        Object elemento = filas.get(posicion);
        if (fila instanceof Tarjeta) {
            ((Tarjeta) fila).pintar((Cita) elemento);
        } else {
            ((Seccion) fila).titulo.setText((Integer) elemento);
        }
    }

    static class Seccion extends RecyclerView.ViewHolder {
        final TextView titulo;

        Seccion(View vista) {
            super(vista);
            titulo = (TextView) vista;
        }
    }

    class Tarjeta extends RecyclerView.ViewHolder {
        private final TextView fecha;
        private final TextView chipEstado;
        private final TextView detalle;
        private final TextView botonCancelar;

        Tarjeta(View vista) {
            super(vista);
            fecha = vista.findViewById(R.id.fecha);
            chipEstado = vista.findViewById(R.id.chip_estado);
            detalle = vista.findViewById(R.id.detalle);
            botonCancelar = vista.findViewById(R.id.boton_cancelar);
        }

        void pintar(Cita cita) {
            EstadoCita estado = cita.getEstado();
            fecha.setText(Formatos.diaCorto(cita.getFecha()));
            Pantallas.pintarChip(chipEstado, estado.etiqueta(), estado.destacado());

            boolean proxima = esProxima(cita);
            detalle.setText(textoDetalle(cita, proxima));
            botonCancelar.setVisibility(proxima ? View.VISIBLE : View.GONE);
            botonCancelar.setText(estado == EstadoCita.PENDIENTE
                    ? R.string.cancelar_solicitud : R.string.cancelar_cita);
            botonCancelar.setOnClickListener(v -> alCancelar.cancelar(cita));
        }

        /** En las próximas se explica la hora; en el historial basta con la hora sola. */
        private String textoDetalle(Cita cita, boolean proxima) {
            String hora = Formatos.hora(cita.horaEfectiva());
            if (!proxima) {
                return hora;
            }
            int texto = cita.getEstado() == EstadoCita.PENDIENTE
                    ? R.string.pediste_a_las : R.string.confirmada_a_las;
            return itemView.getContext().getString(texto, hora);
        }
    }
}
