package com.clandestino.api.seguridad;

import com.clandestino.api.servicio.Reloj;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Frena a quien prueba contraseñas o códigos de invitación a lo loco.
 *
 * Apunta los intentos fallidos de cada "clave" (en el login, la IP más el @usuario; en el
 * registro, solo la IP) y, si hay 5 fallos en los últimos 15 minutos, deja de aceptar intentos
 * de esa clave hasta que el más antiguo salga de la ventana (DP-08).
 *
 * Todo se guarda en memoria: si se reinicia la API, se olvida. Para una barbería con una sola
 * instancia de la API es suficiente.
 *
 * Los métodos son synchronized porque el servidor atiende peticiones en 8 hilos a la vez y un
 * HashMap normal no aguanta que dos hilos lo modifiquen al mismo tiempo. Como el limitador solo
 * se usa en el login y en el registro, que alguien espere unos microsegundos no se nota.
 */
public class LimitadorIntentos {

    // Si el mapa crece mucho (alguien probando miles de usuarios distintos), quitamos las claves
    // cuyos fallos ya han caducado para que no se coma la memoria.
    private static final int CLAVES_ANTES_DE_LIMPIAR = 1000;

    private final int maximoFallos;
    private final Duration ventana;
    private final Reloj reloj;
    private final Map<String, Deque<Instant>> fallosPorClave = new HashMap<>();

    public LimitadorIntentos(int maximoFallos, Duration ventana, Reloj reloj) {
        this.maximoFallos = maximoFallos;
        this.ventana = ventana;
        this.reloj = reloj;
    }

    /** ¿Se puede intentar otra vez con esta clave? */
    public synchronized boolean permitir(String clave) {
        return fallosRecientes(clave) < maximoFallos;
    }

    /** Cuántos segundos faltan para poder volver a intentarlo (para la cabecera Retry-After). */
    public synchronized long segundosDeEspera(String clave) {
        if (fallosRecientes(clave) < maximoFallos) {
            return 0;
        }
        // Se libera cuando el fallo más antiguo cumpla los 15 minutos.
        Instant liberacion = fallosPorClave.get(clave).peekFirst().plus(ventana);
        long segundos = Duration.between(reloj.instante(), liberacion).toSeconds();
        return Math.max(1, segundos);
    }

    public synchronized void registrarFallo(String clave) {
        if (fallosPorClave.size() >= CLAVES_ANTES_DE_LIMPIAR) {
            fallosPorClave.values().removeIf(fallos -> {
                quitarCaducados(fallos);
                return fallos.isEmpty();
            });
        }
        fallosPorClave.computeIfAbsent(clave, k -> new ArrayDeque<>()).addLast(reloj.instante());
    }

    /** Tras un login correcto, el contador de esa clave vuelve a cero. */
    public synchronized void reiniciar(String clave) {
        fallosPorClave.remove(clave);
    }

    private int fallosRecientes(String clave) {
        Deque<Instant> fallos = fallosPorClave.get(clave);
        if (fallos == null) {
            return 0;
        }
        quitarCaducados(fallos);
        return fallos.size();
    }

    /** Los fallos se guardan por orden de llegada, así que los caducados están siempre al principio. */
    private void quitarCaducados(Deque<Instant> fallos) {
        Instant limite = reloj.instante().minus(ventana);
        while (!fallos.isEmpty() && !fallos.peekFirst().isAfter(limite)) {
            fallos.pollFirst();
        }
    }
}
