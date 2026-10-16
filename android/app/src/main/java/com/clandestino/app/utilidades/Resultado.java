package com.clandestino.app.utilidades;

import androidx.lifecycle.MutableLiveData;

import com.clandestino.app.datos.remoto.Callback;
import com.clandestino.app.datos.remoto.ErrorApi;

/**
 * Estado de una carga para la pantalla: está cargando, ha ido bien (con los datos) o ha
 * fallado (con el error). Los modelos de vista publican un Resultado en un LiveData y la
 * pantalla decide qué enseñar en cada caso: la ruedita, la lista o el aviso.
 */
public final class Resultado<T> {

    public enum Estado { CARGANDO, EXITO, ERROR }

    private final Estado estado;
    private final T datos;
    private final ErrorApi error;
    private boolean avisado;

    private Resultado(Estado estado, T datos, ErrorApi error) {
        this.estado = estado;
        this.datos = datos;
        this.error = error;
    }

    /** Mientras se recarga se conservan los datos de antes, así la lista no se queda en blanco. */
    public static <T> Resultado<T> cargando(T datosAnteriores) {
        return new Resultado<>(Estado.CARGANDO, datosAnteriores, null);
    }

    public static <T> Resultado<T> exito(T datos) {
        return new Resultado<>(Estado.EXITO, datos, null);
    }

    public static <T> Resultado<T> error(ErrorApi error) {
        return new Resultado<>(Estado.ERROR, null, error);
    }

    /**
     * Marca el LiveData como "cargando" y devuelve el Callback que lo rellenará cuando conteste
     * la API. Es el mismo trozo de código en todas las cargas de todos los modelos de vista, por
     * eso está aquí y no repetido en cada uno.
     */
    public static <T> Callback<T> en(MutableLiveData<Resultado<T>> destino) {
        Resultado<T> anterior = destino.getValue();
        destino.setValue(cargando(anterior == null ? null : anterior.datos));
        return new Callback<>() {
            @Override
            public void onExito(T datos) {
                destino.setValue(exito(datos));
            }

            @Override
            public void onError(ErrorApi error) {
                destino.setValue(Resultado.error(error));
            }
        };
    }

    public boolean estaCargando() {
        return estado == Estado.CARGANDO;
    }

    public boolean esCorrecto() {
        return estado == Estado.EXITO;
    }

    public boolean haFallado() {
        return estado == Estado.ERROR;
    }

    /**
     * Devuelve true solo la primera vez que se llama. Es para los avisos (Snackbar): al girar el
     * móvil, el LiveData vuelve a entregar el último Resultado a la pantalla nueva y, sin esto,
     * el mismo aviso saldría otra vez.
     */
    public boolean avisarUnaVez() {
        if (avisado) {
            return false;
        }
        avisado = true;
        return true;
    }

    public T datos() {
        return datos;
    }

    public ErrorApi error() {
        return error;
    }
}
