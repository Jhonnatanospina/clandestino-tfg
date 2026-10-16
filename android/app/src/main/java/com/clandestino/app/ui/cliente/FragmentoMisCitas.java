package com.clandestino.app.ui.cliente;

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
import com.clandestino.app.modelovista.ModeloVistaMisCitas;
import com.clandestino.app.ui.FragmentoPanel;
import com.clandestino.app.ui.Pantallas;
import com.clandestino.app.utilidades.Formatos;
import com.clandestino.app.utilidades.Resultado;

import java.util.List;

/** C1 · Las citas del cliente: las próximas, que se pueden cancelar, y el historial. */
public class FragmentoMisCitas extends FragmentoPanel {

    private ModeloVistaMisCitas modelo;
    private SwipeRefreshLayout deslizar;
    private TextView vacio;
    private AdaptadorMisCitas adaptador;

    public FragmentoMisCitas() {
        super(R.layout.fragmento_lista);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estadoGuardado) {
        modelo = new ViewModelProvider(this).get(ModeloVistaMisCitas.class);

        ponerCabecera(vista, saludo(), R.string.mis_citas_titulo);
        mostrarBoton(vista, R.id.boton_refrescar, v -> modelo.cargar());

        deslizar = vista.findViewById(R.id.deslizar);
        deslizar.setColorSchemeResources(R.color.acento);
        deslizar.setOnRefreshListener(modelo::cargar);
        vacio = vista.findViewById(R.id.vacio);
        vacio.setText(R.string.sin_citas);

        adaptador = new AdaptadorMisCitas(this::preguntarCancelar);
        RecyclerView lista = vista.findViewById(R.id.lista);
        lista.setAdapter(adaptador);

        modelo.getCitas().observe(getViewLifecycleOwner(), this::pintar);
        avisarOperaciones(modelo);
        // Tras girar el móvil el modelo de vista ya tiene las citas: no se vuelven a pedir.
        if (modelo.getCitas().getValue() == null) {
            modelo.cargar();
        }
    }

    /**
     * Al volver a esta pestaña se recarga: puede que el cliente venga de pedir una cita o que
     * el barbero haya confirmado alguna mientras tanto.
     */
    @Override
    public void onHiddenChanged(boolean escondido) {
        super.onHiddenChanged(escondido);
        if (!escondido && modelo != null) {
            modelo.cargar();
        }
    }

    private void pintar(Resultado<List<Cita>> resultado) {
        deslizar.setRefreshing(resultado.estaCargando());
        List<Cita> citas = resultado.datos();
        if (citas != null) {
            adaptador.mostrar(citas, Formatos.ahora());
            vacio.setVisibility(citas.isEmpty() ? View.VISIBLE : View.GONE);
        }
        Pantallas.avisarSiFalla(requireActivity(), resultado);
    }

    private void preguntarCancelar(Cita cita) {
        String texto = getString(R.string.cancelar_cita_texto,
                Formatos.diaYMes(cita.getFecha()), Formatos.hora(cita.horaEfectiva()));
        Pantallas.confirmar(requireContext(), getString(R.string.cancelar_cita_pregunta), texto,
                R.string.si_cancelar, () -> modelo.cancelar(cita.getId()));
    }
}
