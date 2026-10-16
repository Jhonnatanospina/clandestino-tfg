package com.clandestino.app.modelovista;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.clandestino.app.R;
import com.clandestino.app.datos.repositorio.RepositorioCitas;
import com.clandestino.app.modelo.Cita;
import com.clandestino.app.utilidades.Resultado;

import java.util.List;

/** C1: las citas del cliente y la cancelación. */
public class ModeloVistaMisCitas extends ModeloVistaBase {

    private final RepositorioCitas repo;
    private final MutableLiveData<Resultado<List<Cita>>> citas = new MutableLiveData<>();

    public ModeloVistaMisCitas(@NonNull Application aplicacion) {
        super(aplicacion);
        repo = new RepositorioCitas(app().api());
    }

    public LiveData<Resultado<List<Cita>>> getCitas() {
        return citas;
    }

    public void cargar() {
        repo.misCitas(Resultado.en(citas));
    }

    public void cancelar(long citaId) {
        repo.cancelar(citaId, accion(R.string.cita_cancelada, this::cargar));
    }
}
