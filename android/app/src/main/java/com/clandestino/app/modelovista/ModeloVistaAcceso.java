package com.clandestino.app.modelovista;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.clandestino.app.datos.repositorio.RepositorioAcceso;
import com.clandestino.app.modelo.Usuario;
import com.clandestino.app.utilidades.Resultado;

/**
 * Modelo de vista de las tres pantallas de acceso (A1, A2 y A3). Las tres acaban igual: con
 * la cuenta del usuario en "sesion", y la pantalla decide a dónde ir según su rol y su estado.
 */
public class ModeloVistaAcceso extends ModeloVistaBase {

    private final RepositorioAcceso repo;
    private final MutableLiveData<Resultado<Usuario>> sesion = new MutableLiveData<>();

    public ModeloVistaAcceso(@NonNull Application aplicacion) {
        super(aplicacion);
        repo = new RepositorioAcceso(app().api(), app().sesion());
    }

    public LiveData<Resultado<Usuario>> getSesion() {
        return sesion;
    }

    public boolean haySesion() {
        return repo.haySesion();
    }

    public void iniciarSesion(String usuario, String clave) {
        repo.iniciarSesion(usuario, clave, Resultado.en(sesion));
    }

    public void registrarse(String codigo, String usuario, String telefono, String clave) {
        repo.registrarse(codigo, usuario, telefono, clave, Resultado.en(sesion));
    }

    /** Pregunta a la API cómo está la cuenta del token guardado (por si ya la han aprobado). */
    public void consultarEstado() {
        repo.perfil(Resultado.en(sesion));
    }
}
