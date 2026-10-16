package com.clandestino.app.ui;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.clandestino.app.R;
import com.clandestino.app.modelovista.ModeloVistaBase;

/**
 * Base de las pestañas de los dos paneles. Junta lo que hacían todas igual: rellenar la
 * cabecera (layout/cabecera.xml), enseñar sus botones y avisar del resultado de las acciones.
 */
public abstract class FragmentoPanel extends Fragment {

    protected FragmentoPanel(@LayoutRes int layout) {
        super(layout);
    }

    protected ActividadPanel panel() {
        return (ActividadPanel) requireActivity();
    }

    /** "Hola, @marta", la ceja de las pantallas que no tienen otra. */
    protected String saludo() {
        return getString(R.string.hola_usuario, panel().usuario());
    }

    protected void ponerCabecera(View vista, String ceja, @StringRes int titulo) {
        ((TextView) vista.findViewById(R.id.ceja)).setText(ceja);
        ((TextView) vista.findViewById(R.id.titulo)).setText(titulo);
    }

    /** Enseña uno de los botones de la cabecera (refrescar, salir o guardar). */
    protected View mostrarBoton(View vista, @IdRes int boton, View.OnClickListener alPulsar) {
        View encontrado = vista.findViewById(boton);
        encontrado.setVisibility(View.VISIBLE);
        encontrado.setOnClickListener(alPulsar);
        return encontrado;
    }

    /**
     * El aviso de las acciones (cancelar, confirmar, guardar...). Se observa con el ciclo de vida
     * de la vista y no con el del fragmento: si no, tras un giro quedarían dos observadores.
     */
    protected void avisarOperaciones(ModeloVistaBase modelo) {
        modelo.getOperacion().observe(getViewLifecycleOwner(),
                resultado -> Pantallas.avisarOperacion(requireActivity(), resultado));
    }

    /** Pregunta antes de cerrar sesión, como en el botón de Ajustes o el de Clientes. */
    protected void preguntarCerrarSesion() {
        Pantallas.confirmar(requireContext(), getString(R.string.cerrar_sesion_pregunta),
                getString(R.string.cerrar_sesion_texto), R.string.cerrar_sesion,
                () -> Pantallas.cerrarSesion(requireActivity()));
    }
}
