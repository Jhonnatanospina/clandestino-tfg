package com.clandestino.api.seguridad;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Se encarga de guardar y comprobar las contraseñas de los usuarios.
 *
 * Nunca guardamos la contraseña tal cual. Guardamos una "huella" que no se puede
 * deshacer, y para calcularla usamos PBKDF2 con SHA-256. Viene incluido en el JDK,
 * así que no hace falta ninguna librería. Es una evolución del hash con SHA-256 que
 * vimos en PSP, pero hecha a propósito lenta (repite el cálculo cientos de miles de
 * veces) para que probar millones de contraseñas cueste muchísimo tiempo.
 *
 * Además le mezclamos una sal aleatoria distinta para cada usuario. Así dos usuarios
 * con la misma contraseña acaban con huellas diferentes.
 *
 * El texto que se guarda en la base de datos tiene este formato:
 *     pbkdf2-sha256$600000$sal$huella
 * Apuntamos las iteraciones dentro del propio texto para poder subirlas en el futuro
 * sin que dejen de funcionar las contraseñas que ya estaban guardadas.
 */
public final class Contrasenas {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final String PREFIJO = "pbkdf2-sha256";
    private static final String SEPARADOR = "$";

    private static final int BYTES_DE_SAL = 16;
    private static final int BITS_DE_HUELLA = 256;

    // La hoja de OWASP sobre almacenamiento de contraseñas recomienda 600 000 vueltas
    // para PBKDF2 con SHA-256. En mi ordenador cada huella tarda entre 0,5 y 1 segundo
    // (la media de ocho seguidas fue de unos 0,75 s). Para un login es aceptable, pero hay
    // que dejarlo anotado en la memoria porque cada login ocupa un hilo ese rato.
    private static final int ITERACIONES_POR_DEFECTO = 600_000;

    // SecureRandom es el generador de números aleatorios "bueno" de Java. Random normal
    // sería predecible y no sirve para sales ni para códigos de invitación.
    private static final SecureRandom azar = new SecureRandom();

    private final int iteraciones;

    public Contrasenas() {
        this(ITERACIONES_POR_DEFECTO);
    }

    /**
     * Permite elegir las iteraciones. Los tests usan un número muy bajo (1000) para
     * que no tarden una eternidad; en el servidor real se usa siempre el valor por defecto.
     */
    public Contrasenas(int iteraciones) {
        if (iteraciones < 1) {
            throw new IllegalArgumentException("Las iteraciones tienen que ser al menos 1");
        }
        this.iteraciones = iteraciones;
    }

    /**
     * Calcula la huella de una contraseña nueva, lista para guardarla en la base de datos.
     */
    public String hashear(String contrasena) {
        byte[] sal = new byte[BYTES_DE_SAL];
        azar.nextBytes(sal);

        byte[] huella = calcularHuella(contrasena, sal, iteraciones);

        return PREFIJO + SEPARADOR + iteraciones + SEPARADOR
                + Base64.getEncoder().encodeToString(sal) + SEPARADOR
                + Base64.getEncoder().encodeToString(huella);
    }

    /**
     * Comprueba si una contraseña escrita por el usuario corresponde a la huella guardada.
     * Si el texto guardado no tiene el formato esperado devolvemos false en vez de lanzar
     * una excepción: para quien intenta entrar da igual el motivo, simplemente no entra.
     */
    public boolean verificar(String contrasena, String hashGuardado) {
        if (contrasena == null || hashGuardado == null) {
            return false;
        }

        String[] partes = hashGuardado.split("\\" + SEPARADOR);
        if (partes.length != 4 || !partes[0].equals(PREFIJO)) {
            return false;
        }

        try {
            // Usamos las iteraciones y la sal que estaban guardadas, no las actuales.
            int iteracionesGuardadas = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] huellaGuardada = Base64.getDecoder().decode(partes[3]);

            byte[] huellaNueva = calcularHuella(contrasena, sal, iteracionesGuardadas);

            // OJO: no comparamos con equals(). Un equals normal se para en el primer byte
            // distinto, y midiendo cuánto tarda se podría ir adivinando la huella.
            // MessageDigest.isEqual tarda siempre lo mismo.
            return MessageDigest.isEqual(huellaNueva, huellaGuardada);
        } catch (IllegalArgumentException e) {
            // Pasa si las iteraciones no son un número o si la sal no es Base64 válido.
            return false;
        }
    }

    private static byte[] calcularHuella(String contrasena, byte[] sal, int iteraciones) {
        PBEKeySpec especificacion = new PBEKeySpec(contrasena.toCharArray(), sal, iteraciones, BITS_DE_HUELLA);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(especificacion).getEncoded();
        } catch (GeneralSecurityException e) {
            // Con un JDK normal esto no puede pasar (el algoritmo siempre está). Si pasara,
            // el servidor está mal instalado y es mejor que se note enseguida.
            throw new IllegalStateException("Este JDK no soporta " + ALGORITMO, e);
        } finally {
            // Borramos la contraseña de la memoria en cuanto ya no hace falta.
            especificacion.clearPassword();
        }
    }
}
