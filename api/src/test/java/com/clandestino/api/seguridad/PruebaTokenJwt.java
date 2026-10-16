package com.clandestino.api.seguridad;

import com.clandestino.api.RelojMovible;
import com.clandestino.api.servicio.ErrorNegocio;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** RNF-SEG-02: un test por cada forma de token malo. */
class PruebaTokenJwt {

    private static final String SECRETO = "clave-de-pruebas-con-mas-de-32-caracteres";

    private final RelojMovible reloj = new RelojMovible(LocalDateTime.of(2026, 10, 5, 10, 0));
    private final TokenJwt tokenJwt = new TokenJwt(SECRETO, reloj.comoReloj(), Duration.ofDays(7));

    @Test
    void unTokenRecienHechoDevuelveElIdDelUsuario() {
        assertEquals(42L, tokenJwt.validar(tokenJwt.emitir(42)));
    }

    @Test
    void unTokenCaducadoSeRechaza() {
        String token = tokenJwt.emitir(42);
        reloj.avanzar(Duration.ofDays(7));
        esInvalido(token);
    }

    @Test
    void unTokenConElContenidoCambiadoSeRechaza() {
        // Cambiamos el usuario 42 por el 1 (el admin) sin poder volver a firmar.
        String[] partes = tokenJwt.emitir(42).split("\\.");
        String contenidoFalso = base64Url("{\"sub\":\"1\",\"iat\":0,\"exp\":9999999999}");
        esInvalido(partes[0] + "." + contenidoFalso + "." + partes[2]);
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() {
        TokenJwt otro = new TokenJwt("otra-clave-distinta-que-tambien-es-larga", reloj.comoReloj(), Duration.ofDays(7));
        esInvalido(otro.emitir(42));
    }

    @Test
    void unTokenSinFirmaConAlgNoneSeRechaza() {
        String cabecera = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String contenido = base64Url("{\"sub\":\"42\",\"iat\":0,\"exp\":9999999999}");
        esInvalido(cabecera + "." + contenido + ".");
    }

    @Test
    void unTokenMalFormadoSeRechaza() {
        esInvalido("");
        esInvalido("hola");
        esInvalido("a.b");
        esInvalido("a.b.c.d");
        esInvalido("%%%.###.!!!");
    }

    @Test
    void unaClaveCortaNoSeAdmite() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenJwt("corta", reloj.comoReloj(), Duration.ofDays(7)));
    }

    private void esInvalido(String token) {
        ErrorNegocio error = assertThrows(ErrorNegocio.class, () -> tokenJwt.validar(token));
        assertEquals("token_invalido", error.getCodigo());
        assertEquals(401, error.getEstadoHttp());
    }

    private static String base64Url(String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }
}
