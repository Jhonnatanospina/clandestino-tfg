package com.clandestino.app.modelovista;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.clandestino.app.AplicacionClandestino;
import com.clandestino.app.datos.remoto.Callback;
import com.clandestino.app.datos.remoto.ErrorApi;
import com.clandestino.app.utilidades.Resultado;

/**
 * Lo que comparten todos los modelos de vista: llegar a las piezas de la aplicación y avisar
 * del resultado de una acción (confirmar, cancelar, guardar...).
 *
 * Son AndroidViewModel y no ViewModel a secas porque necesitan la aplicación para crear sus
 * repositorios y para sacar los textos de strings.xml.
 */
public abstract class ModeloVistaBase extends AndroidViewModel {

    private final MutableLiveData<Resultado<String>> operacion = new MutableLiveData<>();

    protected ModeloVistaBase(@NonNull Application aplicacion) {
        super(aplicacion);
    }

    protected AplicacionClandestino app() {
        return getApplication();
    }

    /**
     * Cómo ha ido la última acción, con el texto que hay que enseñar. La pantalla lo muestra
     * con Resultado.avisarUnaVez() para que no se repita al girar el móvil.
     */
    public LiveData<Resultado<String>> getOperacion() {
        return operacion;
    }

    /** Para las acciones que no encajan en accion() y llevan su propio Callback. */
    protected void avisar(Resultado<String> estado) {
        operacion.setValue(estado);
    }

    /**
     * Callback para una acción. Mientras dura, la operación está "cargando" y la pantalla
     * desactiva los botones para que no se pulse dos veces. Si sale bien deja el mensaje y
     * ejecuta "recargar".
     *
     * Con un 409 también se recarga: el conflicto quiere decir que alguien ha cambiado algo
     * mientras tanto (otra persona cogió la hora, el barbero ya decidió...) y lo que se ve en la
     * pantalla ha dejado de ser verdad.
     */
    protected <T> Callback<T> accion(@StringRes int mensajeExito, Runnable recargar) {
        operacion.setValue(Resultado.cargando(null));
        return new Callback<>() {
            @Override
            public void onExito(T datos) {
                operacion.setValue(Resultado.exito(app().getString(mensajeExito)));
                recargar.run();
            }

            @Override
            public void onError(ErrorApi error) {
                operacion.setValue(Resultado.error(error));
                if (error.getEstadoHttp() == 409) {
                    recargar.run();
                }
            }
        };
    }
}
