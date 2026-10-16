package com.clandestino.app.modelovista;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.clandestino.app.R;
import com.clandestino.app.datos.repositorio.RepositorioCitas;
import com.clandestino.app.modelo.Dia;
import com.clandestino.app.modelo.Hueco;
import com.clandestino.app.utilidades.Resultado;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** C2: el calendario de 14 días, las horas del día elegido y la petición de la cita. */
public class ModeloVistaPedirCita extends ModeloVistaBase {

    private final RepositorioCitas repo;
    private final MutableLiveData<Resultado<List<Dia>>> dias = new MutableLiveData<>();
    private final MutableLiveData<Resultado<List<Hueco>>> huecos = new MutableLiveData<>();

    public ModeloVistaPedirCita(@NonNull Application aplicacion) {
        super(aplicacion);
        repo = new RepositorioCitas(app().api());
    }

    public LiveData<Resultado<List<Dia>>> getDias() {
        return dias;
    }

    public LiveData<Resultado<List<Hueco>>> getHuecos() {
        return huecos;
    }

    public void cargarDias() {
        repo.dias(Resultado.en(dias));
    }

    public void cargarHuecos(LocalDate fecha) {
        // Se borran las horas del día anterior para que no se vean mientras llegan las nuevas.
        huecos.setValue(null);
        repo.huecos(fecha, Resultado.en(huecos));
    }

    public void olvidarHuecos() {
        huecos.setValue(null);
    }

    /**
     * Tanto si sale bien como si alguien se ha adelantado (409), se recargan el calendario y
     * las horas: el día puede haber pasado a completo.
     */
    public void pedir(LocalDate fecha, LocalTime hora) {
        repo.pedir(fecha, hora, accion(R.string.cita_pedida, () -> {
            cargarDias();
            cargarHuecos(fecha);
        }));
    }
}
