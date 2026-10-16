package com.clandestino.api.http;

import com.clandestino.api.modelo.EstadoUsuario;
import com.clandestino.api.modelo.Rol;
import com.clandestino.api.modelo.Usuario;
import com.clandestino.api.servicio.ErrorNegocio;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruebaConversorJson {

    private static final Usuario MARCOS = new Usuario(7, "marcos", "+34611222333", "hash-secreto",
            Rol.CLIENTE, EstadoUsuario.PENDIENTE, new BigDecimal("15"), LocalDateTime.of(2026, 10, 1, 11, 5, 0), null);

    @Test
    void losImportesSalenConDosDecimalesYSinComillas() {
        String texto = ConversorJson.cliente(MARCOS).toString();
        assertTrue(texto.contains("\"precio_corte\":15.00"), texto);
    }

    @Test
    void losCamposVaciosSalenComoNullYNoDesaparecen() {
        String texto = ConversorJson.cliente(MARCOS).toString();
        assertTrue(texto.contains("\"aprobado_en\":null"), texto);
    }

    @Test
    void laCuentaNoLlevaElHashDeLaContrasena() {
        assertFalse(ConversorJson.cuenta(MARCOS).toString().contains("hash"));
        assertFalse(ConversorJson.cliente(MARCOS).toString().contains("hash"));
    }

    @Test
    void lasFechasYHorasVanEnElFormatoDelContrato() {
        assertEquals("2026-10-01T11:05:00", ConversorJson.fechaHora(MARCOS.creadoEn()));
        assertEquals("2026-10-06", ConversorJson.fecha(LocalDate.of(2026, 10, 6)));
        assertEquals("09:00", ConversorJson.hora(LocalTime.of(9, 0)));
    }

    @Test
    void unaFechaQueNoExisteSeRechazaEnVezDeCorregirse() {
        // Sin STRICT, el 30 de febrero se convertiría en silencio en el 2 de marzo.
        ErrorNegocio error = assertThrows(ErrorNegocio.class, () -> ConversorJson.fechaDeTexto("2026-02-30", "fecha"));
        assertEquals("datos_invalidos", error.getCodigo());
        assertThrows(ErrorNegocio.class, () -> ConversorJson.fechaDeTexto("6/10/2026", "fecha"));
        assertThrows(ErrorNegocio.class, () -> ConversorJson.horaDeTexto("25:00", "hora"));
        assertThrows(ErrorNegocio.class, () -> ConversorJson.horaDeTexto("9:00", "hora"));
    }

    @Test
    void unEnteroTieneQueSerUnNumeroDeVerdad() {
        JSONObject json = new JSONObject("{\"a\": 3, \"b\": \"3\", \"c\": 3.7}");
        assertEquals(3, ConversorJson.entero(json, "a", null));
        assertThrows(ErrorNegocio.class, () -> ConversorJson.entero(json, "b", null));
        assertThrows(ErrorNegocio.class, () -> ConversorJson.entero(json, "c", null));
        assertEquals(2, ConversorJson.entero(json, "no_esta", 2));
        assertThrows(ErrorNegocio.class, () -> ConversorJson.entero(json, "no_esta", null));
    }

    @Test
    void losDecimalesNoArrastranErroresDeDouble() {
        JSONObject json = new JSONObject("{\"propina\": 2.1}");
        assertEquals(new BigDecimal("2.1"), ConversorJson.decimalOpcional(json, "propina"));
    }
}
