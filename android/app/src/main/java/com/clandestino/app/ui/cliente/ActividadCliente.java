package com.clandestino.app.ui.cliente;

import androidx.fragment.app.Fragment;

import com.clandestino.app.R;
import com.clandestino.app.ui.ActividadPanel;

/** Panel del cliente aprobado: Mis citas (C1), Pedir (C2) y Ajustes (C3). */
public class ActividadCliente extends ActividadPanel {

    @Override
    protected int menu() {
        return R.menu.navegacion_cliente;
    }

    // Con if y no con switch: desde el AGP 8 los R.id no son constantes y switch no los admite.
    @Override
    protected Fragment crearFragmento(int pestana) {
        if (pestana == R.id.pestana_pedir) {
            return new FragmentoPedirCita();
        }
        if (pestana == R.id.pestana_ajustes) {
            return new FragmentoAjustes();
        }
        return new FragmentoMisCitas();
    }
}
