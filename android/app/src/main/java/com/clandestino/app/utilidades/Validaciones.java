package com.clandestino.app.utilidades;

import java.util.Locale;

/**
 * Las comprobaciones del registro que se hacen mientras se escribe (A2). Son las mismas
 * reglas que aplica la API en ServicioUsuarios; aquí solo sirven para avisar antes de enviar,
 * porque quien decide de verdad es la API.
 */
public final class Validaciones {

    private static final int MINIMO_CONTRASENA = 8;

    private Validaciones() {
    }

    /** Admite "@Marta_R" igual que la API: sin la @ y en minúsculas queda "marta_r". */
    public static boolean usuarioValido(String usuario) {
        String limpio = usuario.trim().toLowerCase(Locale.ROOT);
        if (limpio.startsWith("@")) {
            limpio = limpio.substring(1);
        }
        return limpio.matches("[a-z0-9_]{3,20}");
    }

    /**
     * Acepta los espacios, guiones y puntos que la gente pone al escribir un teléfono, y 9 cifras
     * sin prefijo (la API les pone +34). Luego exige el formato internacional: + y de 8 a 15 cifras.
     */
    public static boolean telefonoValido(String telefono) {
        String limpio = telefono.replaceAll("[\\s.\\-()]", "");
        if (limpio.startsWith("00")) {
            limpio = "+" + limpio.substring(2);
        }
        if (limpio.matches("[0-9]{9}")) {
            return true;
        }
        return limpio.matches("\\+[1-9][0-9]{7,14}");
    }

    public static boolean contrasenaValida(String contrasena) {
        return contrasena.length() >= MINIMO_CONTRASENA;
    }

    /** El código son 12 letras y números; se toleran espacios y guiones al pegarlo. */
    public static boolean codigoValido(String codigo) {
        return codigo.replaceAll("[\\s\\-]", "").matches("[A-Za-z0-9]{12}");
    }
}
