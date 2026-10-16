package com.clandestino.api.seguridad;

import com.clandestino.api.servicio.ErrorNegocio;
import com.clandestino.api.servicio.Reloj;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;

/**
 * Crea y comprueba los tokens de sesión (JWT firmados con HS256).
 *
 * Un JWT son tres trozos en Base64 separados por puntos: cabecera.contenido.firma. La firma es
 * un HMAC-SHA256 de los dos primeros trozos con una clave secreta que solo conoce el servidor.
 * Si alguien cambia el contenido (por ejemplo, pone el id de otro usuario), la firma ya no
 * coincide y el token se rechaza.
 *
 * Lo hacemos a mano con javax.crypto.Mac en vez de con una librería (java-jwt, jjwt) porque
 * son pocas líneas y así se ve exactamente qué se firma y qué se comprueba. Seguimos las
 * recomendaciones del RFC 8725: solo aceptamos HS256 (nunca "none"), comprobamos la caducidad
 * y comparamos la firma en tiempo constante.
 *
 * El token solo lleva el id del usuario (sub) y las fechas (iat, exp). El rol y el estado NO
 * van dentro: se leen de la base de datos en cada petición (FiltroAutenticacion), así que si el
 * barbero bloquea a alguien, el bloqueo se nota enseguida aunque su token siga vivo.
 */
public class TokenJwt {

    private static final String ALGORITMO_MAC = "HmacSHA256";

    // La cabecera es siempre la misma, así que la codificamos una vez.
    private static final String CABECERA = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
            .getBytes(StandardCharsets.UTF_8));

    /** Mínimo de la clave: HS256 trabaja con 256 bits, así que pedimos al menos 32 bytes. */
    public static final int BYTES_MINIMOS_DE_CLAVE = 32;

    private final byte[] clave;
    private final Reloj reloj;
    private final Duration duracion;

    public TokenJwt(String secreto, Reloj reloj, Duration duracion) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < BYTES_MINIMOS_DE_CLAVE) {
            throw new IllegalArgumentException("JWT_SECRET tiene que tener al menos "
                    + BYTES_MINIMOS_DE_CLAVE + " caracteres");
        }
        this.clave = bytes;
        this.reloj = reloj;
        this.duracion = duracion;
    }

    /** Token nuevo para el usuario, válido durante la duración configurada (7 días, DP-08). */
    public String emitir(long usuarioId) {
        long ahora = reloj.instante().getEpochSecond();
        JSONObject contenido = new JSONObject()
                .put("sub", String.valueOf(usuarioId))
                .put("iat", ahora)
                .put("exp", ahora + duracion.toSeconds());

        String sinFirmar = CABECERA + "." + base64Url(contenido.toString().getBytes(StandardCharsets.UTF_8));
        return sinFirmar + "." + base64Url(firmar(sinFirmar));
    }

    /**
     * Comprueba el token y devuelve el id del usuario. Si algo falla (formato, firma, algoritmo
     * o caducidad) lanza el mismo error "token_invalido": a quien manda un token falso no le
     * contamos qué parte ha fallado.
     */
    public long validar(String token) {
        String[] partes = token.split("\\.", -1);
        if (partes.length != 3) {
            throw tokenInvalido();
        }
        try {
            // 1. La firma, lo primero: si no es nuestra, no miramos nada más del contenido.
            byte[] firmaRecibida = Base64.getUrlDecoder().decode(partes[2]);
            byte[] firmaCorrecta = firmar(partes[0] + "." + partes[1]);
            if (!MessageDigest.isEqual(firmaRecibida, firmaCorrecta)) {
                throw tokenInvalido();
            }

            // 2. El algoritmo. OJO: el famoso fallo de algunas librerías era fiarse del "alg" que
            // viene en el token; si decía "none", aceptaban tokens sin firma. Aquí solo vale HS256.
            JSONObject cabecera = new JSONObject(decodificar(partes[0]));
            if (!"HS256".equals(cabecera.optString("alg"))) {
                throw tokenInvalido();
            }

            // 3. La caducidad.
            JSONObject contenido = new JSONObject(decodificar(partes[1]));
            if (contenido.getLong("exp") <= reloj.instante().getEpochSecond()) {
                throw tokenInvalido();
            }
            return Long.parseLong(contenido.getString("sub"));
        } catch (IllegalArgumentException | JSONException e) {
            // Base64 roto, JSON roto, falta "exp" o "sub" no es un número.
            throw tokenInvalido();
        }
    }

    private byte[] firmar(String texto) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO_MAC);
            mac.init(new SecretKeySpec(clave, ALGORITMO_MAC));
            return mac.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Este JDK no soporta " + ALGORITMO_MAC, e);
        }
    }

    private static String base64Url(byte[] bytes) {
        // JWT usa la variante "URL" de Base64 (- y _ en vez de + y /) y sin los = del final.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String decodificar(String trozo) {
        return new String(Base64.getUrlDecoder().decode(trozo), StandardCharsets.UTF_8);
    }

    private static ErrorNegocio tokenInvalido() {
        return new ErrorNegocio("token_invalido", 401, "La sesión no es válida o ha caducado. Vuelve a entrar.");
    }
}
