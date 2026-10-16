package com.clandestino.api.seguridad;

import com.clandestino.api.RelojMovible;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruebaLimitadorIntentos {

    private final RelojMovible reloj = new RelojMovible(LocalDateTime.of(2026, 10, 5, 10, 0));
    private final LimitadorIntentos limitador = new LimitadorIntentos(5, Duration.ofMinutes(15), reloj.comoReloj());

    @Test
    void conCuatroFallosTodaviaSePuede() {
        fallar("login|127.0.0.1|marcos", 4);
        assertTrue(limitador.permitir("login|127.0.0.1|marcos"));
    }

    @Test
    void alQuintoFalloSeBloqueaYDiceCuantoEsperar() {
        fallar("login|127.0.0.1|marcos", 5);
        assertFalse(limitador.permitir("login|127.0.0.1|marcos"));
        assertEquals(15 * 60, limitador.segundosDeEspera("login|127.0.0.1|marcos"));

        reloj.avanzar(Duration.ofMinutes(10));
        assertEquals(5 * 60, limitador.segundosDeEspera("login|127.0.0.1|marcos"));
    }

    @Test
    void elBloqueoDeUnUsuarioNoAfectaAOtro() {
        fallar("login|127.0.0.1|marcos", 5);
        assertTrue(limitador.permitir("login|127.0.0.1|lucia"));
    }

    @Test
    void pasadaLaVentanaSeVuelveAPoder() {
        fallar("registro|127.0.0.1", 5);
        reloj.avanzar(Duration.ofMinutes(15));
        assertTrue(limitador.permitir("registro|127.0.0.1"));
    }

    @Test
    void soloCuentanLosFallosDeLosUltimosQuinceMinutos() {
        fallar("login|127.0.0.1|marcos", 3);
        reloj.avanzar(Duration.ofMinutes(14));
        fallar("login|127.0.0.1|marcos", 2);
        assertFalse(limitador.permitir("login|127.0.0.1|marcos"));

        // Al minuto 15 caducan los tres primeros y quedan solo dos.
        reloj.avanzar(Duration.ofMinutes(1));
        assertTrue(limitador.permitir("login|127.0.0.1|marcos"));
    }

    @Test
    void unLoginCorrectoPoneElContadorACero() {
        fallar("login|127.0.0.1|marcos", 4);
        limitador.reiniciar("login|127.0.0.1|marcos");
        fallar("login|127.0.0.1|marcos", 4);
        assertTrue(limitador.permitir("login|127.0.0.1|marcos"));
    }

    @Test
    void conMuchasClavesSeLimpianLasCaducadasSinRomperse() {
        for (int i = 0; i < 1000; i++) {
            limitador.registrarFallo("login|127.0.0.1|usuario" + i);
        }
        reloj.avanzar(Duration.ofMinutes(16));
        // Este fallo dispara la limpieza del mapa (antes daba ConcurrentModificationException).
        limitador.registrarFallo("login|127.0.0.1|otro");
        assertTrue(limitador.permitir("login|127.0.0.1|usuario0"));
    }

    private void fallar(String clave, int veces) {
        for (int i = 0; i < veces; i++) {
            limitador.registrarFallo(clave);
        }
    }
}
