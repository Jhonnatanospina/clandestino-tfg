package com.clandestino.api.seguridad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruebaContrasenas {

    // Pocas vueltas para que los tests vayan rápido. La lógica es la misma que con 600 000.
    private final Contrasenas contrasenas = new Contrasenas(1000);

    @Test
    void laContrasenaCorrectaSeAcepta() {
        String hash = contrasenas.hashear("cortesDelBarrio1");
        assertTrue(contrasenas.verificar("cortesDelBarrio1", hash));
    }

    @Test
    void unaContrasenaDistintaSeRechaza() {
        String hash = contrasenas.hashear("cortesDelBarrio1");
        assertFalse(contrasenas.verificar("cortesdelbarrio1", hash));
        assertFalse(contrasenas.verificar("", hash));
    }

    @Test
    void laMismaContrasenaDaHuellasDistintasPorLaSal() {
        String primera = contrasenas.hashear("igual-para-los-dos");
        String segunda = contrasenas.hashear("igual-para-los-dos");
        assertNotEquals(primera, segunda);
        assertTrue(contrasenas.verificar("igual-para-los-dos", primera));
        assertTrue(contrasenas.verificar("igual-para-los-dos", segunda));
    }

    @Test
    void elHashGuardaElFormatoYLasVueltas() {
        String[] partes = contrasenas.hashear("loquesea123").split("\\$");
        assertEquals(4, partes.length);
        assertEquals("pbkdf2-sha256", partes[0]);
        assertEquals("1000", partes[1]);
    }

    @Test
    void unHashHechoConOtrasVueltasSigueValiendo() {
        // Si algún día se suben las vueltas, las contraseñas viejas tienen que seguir entrando.
        String hashViejo = new Contrasenas(500).hashear("antigua-1234");
        assertTrue(contrasenas.verificar("antigua-1234", hashViejo));
    }

    @Test
    void unHashRotoNoLanzaExcepcionSinoFalse() {
        assertFalse(contrasenas.verificar("algo", "esto-no-es-un-hash"));
        assertFalse(contrasenas.verificar("algo", "pbkdf2-sha256$mil$c2Fs$aHVlbGxh"));
        assertFalse(contrasenas.verificar("algo", "pbkdf2-sha256$1000$no base64!$aHVlbGxh"));
        assertFalse(contrasenas.verificar("algo", null));
        assertFalse(contrasenas.verificar(null, contrasenas.hashear("algo")));
    }
}
