package com.clandestino.app.ui.admin;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import java.time.LocalTime;
import java.util.List;

/** D1 · Solicitudes: las citas pendientes, que el barbero confirma (con hora final) o rechaza. */
public class FragmentoSolicitudes extends FragmentoPanel {

    private ModeloVistaCitasAdmin modelo;
    private SwipeRefreshLayout deslizar;
    private TextView ceja;
    private TextView vacio;
    private AdaptadorSolicitudes adaptador;

    public FragmentoSolicitudes() {
        super(R.layout.fragmento_lista);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estadoGuardado) {
        modelo = new ViewModelProvider(this).get(ModeloVistaCitasAdmin.class);

        // La ceja dice cuántas hay ("3 pendientes"); hasta que cargan, el saludo.
        ponerCabecera(vista, saludo(), R.string.solicitudes_titulo);
        ceja = vista.findViewById(R.id.ceja);
        mostrarBoton(vista, R.id.boton_refrescar, v -> modelo.recargar());

        deslizar = vista.findViewById(R.id.deslizar);
        deslizar.setColorSchemeResources(R.color.acento);
        deslizar.setOnRefreshListener(modelo::recargar);
        vacio = vista.findViewById(R.id.vacio);
        vacio.setText(R.string.sin_solicitudes);

        adaptador = new AdaptadorSolicitudes(new AdaptadorSolicitudes.Acciones() {
            @Override
            public void confirmar(Cita cita, LocalTime horaFinal) {
                modelo.confirmar(cita.getId(), horaFinal);
            }

            @Override
            public void rechazar(Cita cita) {
                preguntarRechazar(cita);
            }
        });
        RecyclerView lista = vista.findViewById(R.id.lista);
        lista.setAdapter(adaptador);

        modelo.getCitas().observe(getViewLifecycleOwner(), this::pintar);
        avisarOperaciones(modelo);
        if (modelo.getCitas().getValue() == null) {
            modelo.cargar("pendiente", null, null);
        }
    }

    /** Las solicitudes llegan en cualquier momento: se recargan cada vez que se vuelve aquí. */
    @Override
    public void onHiddenChanged(boolean escondido) {
        super.onHiddenChanged(escondido);
        if (!escondido && modelo != null) {
            modelo.recargar();
        }
    }

    private void pintar(Resultado<List<Cita>> resultado) {
        deslizar.setRefreshing(resultado.estaCargando());
        List<Cita> citas = resultado.datos();
        if (citas != null) {
            adaptador.mostrar(citas);
            vacio.setVisibility(citas.isEmpty() ? View.VISIBLE : View.GONE);
            ceja.setText(getResources().getQuantityString(R.plurals.pendientes,
                    citas.size(), citas.size()));
        }
        Pantallas.avisarSiFalla(requireActivity(), resultado);
    }

    private void preguntarRechazar(Cita cita) {
        String texto = getString(R.string.rechazar_texto, cita.getClienteUsuario(),
                Formatos.diaYMes(cita.getFecha()), Formatos.hora(cita.getHoraPedida()));
        Pantallas.confirmar(requireContext(), getString(R.string.rechazar_pregunta), texto,
                R.string.rechazar, () -> modelo.rechazar(cita.getId()));
    }
}
