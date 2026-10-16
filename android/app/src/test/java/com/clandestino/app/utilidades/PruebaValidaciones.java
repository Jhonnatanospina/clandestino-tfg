package com.clandestino.app.utilidades;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Las reglas del registro (A2). Tienen que coincidir con las de ServicioUsuarios en la API. */
public class PruebaValidaciones {

    @Test
    public void usuarioAdmiteArrobaYMayusculas() {
        assertTrue(Validaciones.usuarioValido("@Marta_R"));
        assertTrue(Validaciones.usuarioValido("  diego "));
    }

    @Test
    public void usuarioRechazaCortosLargosYRaros() {
        assertFalse(Validaciones.usuarioValido("ab"));
        assertFalse(Validaciones.usuarioValido("a".repeat(21)));
        assertFalse(Validaciones.usuarioValido("raúl"));
        assertFalse(Validaciones.usuarioValido("con espacio"));
    }

    @Test
    public void telefonoComoLoEscribeLaGente() {
        assertTrue(Validaciones.telefonoValido("611 22 23 33"));
        assertTrue(Validaciones.telefonoValido("+34 611-222-333"));
        assertTrue(Validaciones.telefonoValido("0034611222333"));
    }

    @Test
    public void telefonoRechazaLoQueNoEsUnNumero() {
        assertFalse(Validaciones.telefonoValido("6112223"));
        assertFalse(Validaciones.telefonoValido("+0611222333"));
        assertFalse(Validaciones.telefonoValido("seis once"));
    }

    @Test
    public void contrasenaDeOchoComoMinimo() {
        assertFalse(Validaciones.contrasenaValida("1234567"));
        assertTrue(Validaciones.contrasenaValida("12345678"));
    }

    @Test
    public void codigoDeDoceToleraGuionesAlPegar() {
        assertTrue(Validaciones.codigoValido("H8TQ-5C3Z-N6VA"));
        assertFalse(Validaciones.codigoValido("H8TQ5C3ZN6V"));
    }
}
