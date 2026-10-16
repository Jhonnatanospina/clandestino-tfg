package com.clandestino.app.datos.remoto;

/**
 * Lo que se hace cuando llega la respuesta de la API. ClienteApi llama a uno de los dos
 * métodos, siempre en el hilo principal, así que desde aquí se pueden tocar las vistas.
 */
public interface Callback<T> {

    void onExito(T datos);

    void onError(ErrorApi error);
}
