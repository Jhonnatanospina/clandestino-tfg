package com.clandestino.app.modelovista;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.clandestino.app.datos.repositorio.RepositorioAcceso;
import com.clandestino.app.modelo.Usuario;
import com.clandestino.app.utilidades.Resultado;

/** C3: los datos de la cuenta y el tema claro u oscuro. */
public class ModeloVistaAjustes extends ModeloVistaBase {

    private final RepositorioAcceso repo;
    private final MutableLiveData<Resultado<Usuario>> perfil = new MutableLiveData<>();
    // El tema es un valor local: no hay nada que cargar, así que no lleva Resultado.
    private final MutableLiveData<Boolean> temaOscuro = new MutableLiveData<>();

    public ModeloVistaAjustes(@NonNull Application aplicacion) {
        super(aplicacion);
        repo = new RepositorioAcceso(app().api(), app().sesion());
        temaOscuro.setValue(app().temaOscuro());
    }

    public LiveData<Resultado<Usuario>> getPerfil() {
        return perfil;
    }

    public LiveData<Boolean> getTemaOscuro() {
        return temaOscuro;
    }

    public void cargarPerfil() {
        repo.perfil(Resultado.en(perfil));
    }

    /**
     * Guarda la preferencia y la aplica. OJO: aplicar el tema recrea la actividad, así que
     * después de esta llamada la pantalla se vuelve a construir entera.
     */
    public void cambiarTema(boolean oscuro) {
        if (Boolean.valueOf(oscuro).equals(temaOscuro.getValue())) {
            return;
        }
        temaOscuro.setValue(oscuro);
        app().guardarTemaOscuro(oscuro);
    }
}
