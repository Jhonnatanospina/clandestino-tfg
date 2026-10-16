package com.clandestino.api.servicio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cómo se limpia lo que escribe la persona en el registro (RN-21, RN-22). */
class PruebaNormalizacion {

    @Test
    void elUsuarioSeQuedaSinEspaciosNiArrobaYEnMinusculas() {
        assertEquals("marcos_r", ServicioUsuarios.normalizarUsuario("  @Marcos_R "));
        assertEquals("", ServicioUsuarios.normalizarUsuario(null));
    }

    @Test
    void soloValenUsuariosDe3a20LetrasNumerosOBarraBaja() {
        assertDoesNotThrow(() -> ServicioUsuarios.comprobarUsuario("abc"));
        assertDoesNotThrow(() -> ServicioUsuarios.comprobarUsuario("barbero_de_la_esq_20"));
        assertThrows(ErrorNegocio.class, () -> ServicioUsuarios.comprobarUsuario("ab"));
        assertThrows(ErrorNegocio.class, () -> ServicioUsuarios.comprobarUsuario("con espacio"));
        assertThrows(ErrorNegocio.class, () -> ServicioUsuarios.comprobarUsuario("ñandú"));
        assertThrows(ErrorNegocio.class, () -> ServicioUsuarios.comprobarUsuario("a".repeat(21)));
    }

    @Test
    void losTelefonosSePasanAFormatoInternacional() {
        assertEquals("+34611222333", ServicioUsuarios.normalizarTelefono("611 22 23 33"));
        assertEquals("+34611222333", ServicioUsuarios.normalizarTelefono("611-22-23-33"));
        assertEquals("+34611222333", ServicioUsuarios.normalizarTelefono("0034 611 222 333"));
        assertEquals("+351912345678", ServicioUsuarios.normalizarTelefono("+351 912 345 678"));
        assertEquals("+34611222333", ServicioUsuarios.normalizarTelefono("(611) 222.333"));
    }

    @Test
    void unTelefonoSinSentidoSeRechaza() {
        assertThrows(ErrorNegocio.class, () -> ServicioUsuarios.comprobarTelefono(ServicioUsuarios.normalizarTelefono("12345")));
        assertThrows(ErrorNegocio.class, () -> ServicioUsuarios.comprobarTelefono(ServicioUsuarios.normalizarTelefono("telefono")));
        assertThrows(ErrorNegocio.class, () -> ServicioUsuarios.comprobarTelefono("+0611222333"));
    }

    @Test
    void elCodigoDeInvitacionIgnoraMayusculasEspaciosYGuiones() {
        assertEquals("K7M2QX9PTD4R", ServicioUsuarios.normalizarCodigo("k7m2-qx9p td4r"));
        assertThrows(ErrorNegocio.class, () -> ServicioUsuarios.normalizarCodigo("   "));
        assertThrows(ErrorNegocio.class, () -> ServicioUsuarios.normalizarCodigo("A".repeat(33)));
    }

    @Test
    void laContrasenaTieneQueTenerEntre8y128Caracteres() {
        assertThrows(ErrorNegocio.class, () -> ServicioUsuarios.comprobarContrasena("1234567"));
        assertThrows(ErrorNegocio.class, () -> ServicioUsuarios.comprobarContrasena(null));
        assertThrows(ErrorNegocio.class, () -> ServicioUsuarios.comprobarContrasena("x".repeat(129)));
        assertDoesNotThrow(() -> ServicioUsuarios.comprobarContrasena("12345678"));
    }

    @Test
    void losCodigosGeneradosNoLlevanLetrasQueSeConfunden() {
        for (int i = 0; i < 200; i++) {
            String codigo = ServicioUsuarios.generarCodigo();
            assertEquals(12, codigo.length());
            assertTrue(codigo.matches("[A-Z2-9]{12}"), codigo);
            assertFalse(codigo.matches(".*[01OIL].*"), codigo);
        }
    }
}
