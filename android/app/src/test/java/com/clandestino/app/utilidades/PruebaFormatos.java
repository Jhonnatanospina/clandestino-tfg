package com.clandestino.app.utilidades;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;

/** Cómo se escriben fechas, horas y teléfonos: tiene que salir igual que en los mockups. */
public class PruebaFormatos {

    // Martes 2 de febrero de 2027
    private static final LocalDate MARTES = LocalDate.of(2027, 2, 2);

    @Test
    public void laHoraVaSiempreConDosCifras() {
        assertEquals("09:30", Formatos.hora(LocalTime.of(9, 30)));
    }

    @Test
    public void diaCortoComoEnLasTarjetas() {
        assertEquals("MAR 2 FEB", Formatos.diaCorto(MARTES));
    }

    @Test
    public void diaLargoYDiaYMes() {
        assertEquals("Martes 2 de febrero", Formatos.diaLargo(MARTES));
        assertEquals("2 de febrero", Formatos.diaYMes(MARTES));
    }

    @Test
    public void rangoSoloRepiteElMesSiCambia() {
        assertEquals("Del 2 al 6 de noviembre", Formatos.rango(LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 6)));
        assertEquals("Del 30 de octubre al 3 de noviembre", Formatos.rango(
                LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 3)));
    }

    @Test
    public void nombreDiaEmpiezaEnLunesComoLaApi() {
        assertEquals("Lunes", Formatos.nombreDia(1));
        assertEquals("Domingo", Formatos.nombreDia(7));
    }

    @Test
    public void mesesDeUnSoloMesODeDos() {
        assertEquals("OCTUBRE 2026", Formatos.meses(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)));
        assertEquals("OCTUBRE – NOVIEMBRE 2026", Formatos.meses(LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 11, 14)));
        assertEquals("DICIEMBRE 2026 – ENERO 2027", Formatos.meses(LocalDate.of(2026, 12, 15),
                LocalDate.of(2027, 1, 14)));
    }

    @Test
    public void semanaQueCambiaDeMes() {
        assertEquals("5 – 11 OCT 2026", Formatos.semana(LocalDate.of(2026, 10, 5)));
        assertEquals("28 SEP – 4 OCT 2026", Formatos.semana(LocalDate.of(2026, 9, 28)));
    }

    @Test
    public void telefonoEspanolSeSeparaYElRestoSeDejaIgual() {
        assertEquals("+34 611 222 333", Formatos.telefono("+34611222333"));
        assertEquals("+447700900123", Formatos.telefono("+447700900123"));
    }
}
