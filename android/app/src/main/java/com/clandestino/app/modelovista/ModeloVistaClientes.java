package com.clandestino.app.modelovista;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.clandestino.app.R;
import com.clandestino.app.datos.remoto.Callback;
import com.clandestino.app.datos.remoto.ErrorApi;
import com.clandestino.app.datos.repositorio.RepositorioClientes;
import com.clandestino.app.modelo.Invitacion;
import com.clandestino.app.modelo.Usuario;
import com.clandestino.app.utilidades.Resultado;

import java.util.List;

/** D4: aprobar o bloquear clientes y generar códigos de invitación. */
public class ModeloVistaClientes extends ModeloVistaBase {

    private final RepositorioClientes repo;
    private final MutableLiveData<Resultado<List<Usuario>>> clientes = new MutableLiveData<>();
    private final MutableLiveData<Resultado<List<Invitacion>>> invitaciones = new MutableLiveData<>();
    // El último código generado, para enseñarlo en grande con el botón de copiar.
    private final MutableLiveData<Invitacion> codigoNuevo = new MutableLiveData<>();

    public ModeloVistaClientes(@NonNull Application aplicacion) {
        super(aplicacion);
        repo = new RepositorioClientes(app().api());
    }

    public LiveData<Resultado<List<Usuario>>> getClientes() {
        return clientes;
    }

    public LiveData<Resultado<List<Invitacion>>> getInvitaciones() {
        return invitaciones;
    }

    public LiveData<Invitacion> getCodigoNuevo() {
        return codigoNuevo;
    }

    public void cargar() {
        cargarClientes();
        cargarInvitaciones();
    }

    private void cargarClientes() {
        repo.clientes(Resultado.en(clientes));
    }

    private void cargarInvitaciones() {
        repo.invitaciones(Resultado.en(invitaciones));
    }

    public void aprobar(long id) {
        repo.aprobar(id, accion(R.string.cliente_aprobado, this::cargarClientes));
    }

    public void bloquear(long id) {
        repo.bloquear(id, accion(R.string.cliente_bloqueado, this::cargarClientes));
    }

    /**
     * Aquí no vale accion(): hace falta la invitación que devuelve la API para enseñar el
     * código. El aviso de éxito sobra, porque el código aparece en la tarjeta.
     */
    public void generarInvitacion(String nota) {
        avisar(Resultado.cargando(null));
        repo.generarInvitacion(nota, new Callback<>() {
            @Override
            public void onExito(Invitacion invitacion) {
                avisar(Resultado.exito(null));
                codigoNuevo.setValue(invitacion);
                cargarInvitaciones();
            }

            @Override
            public void onError(ErrorApi error) {
                avisar(Resultado.error(error));
            }
        });
    }
}
