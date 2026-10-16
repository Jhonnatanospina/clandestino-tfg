package com.clandestino.api.modelo;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RN-04 y DP-03: las horas que se ofrecen en un tramo. */
class PruebaTramo {

    private static LocalTime h(String texto) {
        return LocalTime.parse(texto);
    }

    @Test
    void unTramoDeTardeDaUnaHoraCadaHora() {
        Tramo tramo = new Tramo(h("16:00"), h("22:00"), 2);
        assertEquals(List.of(h("16:00"), h("17:00"), h("18:00"), h("19:00"), h("20:00"), h("21:00")), tramo.huecos());
    }

    @Test
    void laUltimaCitaTieneQueAcabarAntesDelCierre() {
        // 16:00-18:30: a las 18:00 la cita acabaría a las 19:00, así que no se ofrece.
        Tramo tramo = new Tramo(h("16:00"), h("18:30"), 2);
        assertEquals(List.of(h("16:00"), h("17:00")), tramo.huecos());
    }

    @Test
    void siElTramoEmpiezaALaYMediaLaRejillaTambien() {
        Tramo tramo = new Tramo(h("14:30"), h("17:00"), 2);
        assertEquals(List.of(h("14:30"), h("15:30")), tramo.huecos());
        assertTrue(tramo.estaAlineada(h("15:30")));
        assertFalse(tramo.estaAlineada(h("15:00")));
    }

    @Test
    void unTramoMasCortoQueUnaCitaNoTieneHuecos() {
        assertTrue(new Tramo(h("10:00"), h("10:45"), 2).huecos().isEmpty());
    }

    @Test
    void unTramoHastaCasiMedianocheNoSeQuedaEnBucle() {
        Tramo tramo = new Tramo(h("21:30"), h("23:59"), 2);
        assertEquals(List.of(h("21:30"), h("22:30")), tramo.huecos());
        // 23:30 + 1 h da la vuelta a las 00:30: no cabe, aunque 00:30 sea "antes" que 23:59.
        assertFalse(tramo.cabe(h("23:30")));

        // El día entero: de 00:00 a 22:00, 23 huecos, y el bucle termina.
        assertEquals(23, new Tramo(h("00:00"), h("23:59"), 2).huecos().size());
    }

    @Test
    void cabeMiraElPrincipioYElFinal() {
        Tramo tramo = new Tramo(h("16:00"), h("20:00"), 2);
        assertTrue(tramo.cabe(h("16:00")));
        assertTrue(tramo.cabe(h("19:00")));
        assertFalse(tramo.cabe(h("15:00")));
        assertFalse(tramo.cabe(h("19:30")));
        assertFalse(tramo.cabe(h("20:00")));
    }
}
