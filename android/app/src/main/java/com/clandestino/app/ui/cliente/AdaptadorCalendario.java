package com.clandestino.app.ui.cliente;

import android.annotation.SuppressLint;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.clandestino.app.R;
import com.clandestino.app.modelo.Dia;
import com.clandestino.app.modelo.EstadoDia;
import com.clandestino.app.utilidades.Formatos;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calendario de C2 en una rejilla de 7 columnas. La API manda los días que se pueden pedir
 * (unos 14); aquí se completan las semanas de lunes a domingo para que cada número caiga
 * debajo de su letra. Los días que no vienen de la API se pintan apagados y no se pueden tocar.
 */
public class AdaptadorCalendario extends RecyclerView.Adapter<AdaptadorCalendario.Celda> {

    /** Lo que hace la pantalla al tocar un día libre. */
    public interface AlElegir {
        void elegir(LocalDate fecha);
    }

    private final AlElegir alElegir;
    private final Map<LocalDate, Dia> dias = new HashMap<>();
    private List<LocalDate> celdas = Collections.emptyList();
    private LocalDate hoy;
    private LocalDate elegido;

    public AdaptadorCalendario(AlElegir alElegir) {
        this.alElegir = alElegir;
    }

    /**
     * Todas las fechas de la rejilla: desde el lunes de la semana de "desde" hasta el domingo
     * de la semana de "hasta". Es estático y no toca vistas para poder probarlo con JUnit.
     */
    public static List<LocalDate> celdas(LocalDate desde, LocalDate hasta) {
        LocalDate lunes = desde.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate domingo = hasta.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<LocalDate> fechas = new ArrayList<>();
        for (LocalDate fecha = lunes; !fecha.isAfter(domingo); fecha = fecha.plusDays(1)) {
            fechas.add(fecha);
        }
        return fechas;
    }

    // Como mucho son tres semanas (21 celdas): no compensa calcular qué ha cambiado.
    @SuppressLint("NotifyDataSetChanged")
    public void mostrar(List<Dia> lista, LocalDate hoy) {
        this.hoy = hoy;
        dias.clear();
        for (Dia dia : lista) {
            dias.put(dia.getFecha(), dia);
        }
        celdas = lista.isEmpty()
                ? Collections.emptyList()
                : celdas(lista.get(0).getFecha(), lista.get(lista.size() - 1).getFecha());
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void marcar(LocalDate fecha) {
        elegido = fecha;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return celdas.size();
    }

    @NonNull
    @Override
    public Celda onCreateViewHolder(@NonNull ViewGroup padre, int tipo) {
        View vista = LayoutInflater.from(padre.getContext())
                .inflate(R.layout.item_dia_calendario, padre, false);
        return new Celda(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Celda celda, int posicion) {
        LocalDate fecha = celdas.get(posicion);
        celda.pintar(fecha, dias.get(fecha));
    }

    class Celda extends RecyclerView.ViewHolder {
        private final TextView numero;

        Celda(View vista) {
            super(vista);
            numero = vista.findViewById(R.id.numero);
        }

        void pintar(LocalDate fecha, Dia dia) {
            // Un día que no viene de la API (fuera de los 14) se trata como cerrado.
            EstadoDia estado = dia == null ? EstadoDia.CERRADO : dia.getEstado();
            boolean marcado = fecha.equals(elegido);

            numero.setText(String.valueOf(fecha.getDayOfMonth()));
            numero.setContentDescription(Formatos.diaLargo(fecha));
            numero.setBackgroundResource(fondo(estado, marcado, fecha.equals(hoy)));
            numero.setTextColor(ContextCompat.getColor(itemView.getContext(), color(estado, marcado)));
            numero.setTypeface(null, marcado ? Typeface.BOLD : Typeface.NORMAL);
            tachar(estado == EstadoDia.COMPLETO && !marcado);

            boolean libre = dia != null && dia.estaLibre();
            itemView.setOnClickListener(libre ? v -> alElegir.elegir(fecha) : null);
            itemView.setClickable(libre);
        }

        private int fondo(EstadoDia estado, boolean marcado, boolean esHoy) {
            if (marcado) {
                return R.drawable.celda_elegida;
            }
            if (esHoy) {
                return R.drawable.celda_hoy;
            }
            switch (estado) {
                case LIBRE:
                    return R.drawable.celda_libre;
                case COMPLETO:
                    return R.drawable.celda_completo;
                case VACACIONES:
                    return R.drawable.celda_vacaciones;
                default:
                    return 0; // cerrado: sin fondo
            }
        }

        private int color(EstadoDia estado, boolean marcado) {
            if (marcado) {
                return R.color.blanco;
            }
            switch (estado) {
                case LIBRE:
                    return R.color.tinta;
                case COMPLETO:
                    return R.color.silencio;
                case VACACIONES:
                    return R.color.info_tinta;
                default:
                    return R.color.apagado;
            }
        }

        private void tachar(boolean tachado) {
            int flags = numero.getPaintFlags();
            numero.setPaintFlags(tachado
                    ? flags | Paint.STRIKE_THRU_TEXT_FLAG
                    : flags & ~Paint.STRIKE_THRU_TEXT_FLAG);
        }
    }
}
