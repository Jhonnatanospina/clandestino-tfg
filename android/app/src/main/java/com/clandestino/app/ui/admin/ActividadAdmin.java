package com.clandestino.app.ui.admin;

import androidx.fragment.app.Fragment;

import com.clandestino.app.R;
import com.clandestino.app.ui.ActividadPanel;

/** Panel del barbero: Solicitudes (D1), Agenda (D2), Horarios (D3) y Clientes (D4). */
public class ActividadAdmin extends ActividadPanel {

    @Override
    protected int menu() {
        return R.menu.navegacion_admin;
    }

    @Override
    protected Fragment crearFragmento(int pestana) {
        if (pestana == R.id.pestana_agenda) {
            return new FragmentoAgenda();
        }
        if (pestana == R.id.pestana_horarios) {
            return new FragmentoHorarios();
        }
        if (pestana == R.id.pestana_clientes) {
            return new FragmentoClientes();
        }
        return new FragmentoSolicitudes();
    }
}
