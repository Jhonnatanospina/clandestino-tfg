package com.clandestino.app.ui.cliente;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/** Las celdas del calendario de C2: semanas enteras de lunes a domingo. */
public class PruebaCalendario {

    @Test
    public void empiezaEnLunesYAcabaEnDomingo() {
        // Del jueves 15 de octubre al sábado 14 de noviembre de 2026.
        List<LocalDate> celdas = AdaptadorCalendario.celdas(LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 11, 14));
        assertEquals(LocalDate.of(2026, 10, 12), celdas.get(0));
        assertEquals(LocalDate.of(2026, 11, 15), celdas.get(celdas.size() - 1));
        assertEquals(DayOfWeek.MONDAY, celdas.get(0).getDayOfWeek());
        assertEquals(0, celdas.size() % 7);
    }

    @Test
    public void siYaEsLunesNoRetrocede() {
        List<LocalDate> celdas = AdaptadorCalendario.celdas(LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 18));
        assertEquals(7, celdas.size());
    }
}
