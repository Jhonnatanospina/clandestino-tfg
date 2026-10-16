package com.clandestino.api.http;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Arranca el servidor HTTP que trae el propio JDK (com.sun.net.httpserver.HttpServer).
 *
 * No usamos Spring ni ningún framework: para una API de este tamaño el servidor del JDK basta,
 * y así se ve cómo funciona por dentro una API REST (sockets, hilos, cabeceras, códigos HTTP),
 * que es lo que se trabaja en Programación de Servicios y Procesos.
 */
public class ServidorApi {

    // OJO: si no se le da un Executor, HttpServer atiende todas las peticiones en un único
    // hilo, una detrás de otra. Con un login de casi un segundo (PBKDF2), todos los demás
    // esperarían. Con un pool fijo de 8 hilos se atienden hasta 8 peticiones a la vez y las
    // demás esperan en cola. 8 es de sobra para una barbería y no se come la RAM del PC.
    private static final int HILOS = 8;

    private HttpServer servidor;
    private ExecutorService hilos;

    /**
     * Escucha en host:puerto. Con host 127.0.0.1 solo se puede llamar desde este ordenador, y
     * basta para el emulador, porque su dirección 10.0.2.2 apunta al 127.0.0.1 del PC (y para un
     * móvil por cable se usa "adb reverse"). Así la API no queda abierta a toda la red wifi.
     */
    public void iniciar(String host, int puerto, Enrutador enrutador) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(host, puerto), 0);
        servidor.createContext("/", enrutador);
        hilos = Executors.newFixedThreadPool(HILOS);
        servidor.setExecutor(hilos);
        servidor.start();
    }

    /** Para el servidor dando 2 segundos a las peticiones que estén a medias. */
    public void parar() {
        if (servidor != null) {
            servidor.stop(2);
            hilos.shutdown();
            try {
                hilos.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** El puerto en el que escucha de verdad (los tests piden el puerto 0 y el sistema elige uno libre). */
    public int puerto() {
        return servidor.getAddress().getPort();
    }
}
