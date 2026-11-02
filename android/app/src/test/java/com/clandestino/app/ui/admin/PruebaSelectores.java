package com.clandestino.app.ui.admin;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

/** Las horas del desplegable de D1 y D5, que tienen que cuadrar con DP-04 de la API. */
public class PruebaSelectores {

    @Test
    public void vanDeOchoADiezDeLaNocheCadaMediaHora() {
        List<String> horas = Selectores.HORAS_FINALES;
        assertEquals("08:00", horas.get(0));
        assertEquals("08:30", horas.get(1));
        assertEquals("22:00", horas.get(horas.size() - 1));
        // 14 horas enteras, dos opciones por hora, y las 22:00 al final.
        assertEquals(29, horas.size());
    }
}
