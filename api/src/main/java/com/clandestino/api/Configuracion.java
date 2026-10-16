package com.clandestino.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Los datos de arranque de la API: dónde está MySQL, con qué usuario entra, la clave de los
 * tokens y el puerto.
 *
 * Se leen del fichero .env de la raíz del proyecto (el que se copia de .env.example). Si existe
 * una variable de entorno con el mismo nombre, manda la variable. Las contraseñas nunca se
 * escriben en el código ni se suben a Git: solo están en el .env, que está en el .gitignore.
 */
public final class Configuracion {

    private final Map<String, String> valores;

    private Configuracion(Map<String, String> valores) {
        this.valores = valores;
    }

    /**
     * Busca el .env en la carpeta actual y, si no está, en la de arriba. Así funciona igual si
     * se arranca desde la raíz del proyecto que desde la carpeta api/ (que es donde arranca Gradle).
     */
    public static Configuracion cargar() {
        Map<String, String> valores = new HashMap<>();
        for (Path posible : List.of(Path.of(".env"), Path.of("..", ".env"))) {
            if (Files.isRegularFile(posible)) {
                valores.putAll(leerFicheroEnv(posible));
                break;
            }
        }
        // Las variables de entorno van por encima de lo que diga el fichero.
        valores.putAll(System.getenv());
        return new Configuracion(valores);
    }

    /** Para los tests: una configuración hecha a mano, sin fichero ni variables de entorno. */
    public static Configuracion desdeMapa(Map<String, String> valores) {
        return new Configuracion(new HashMap<>(valores));
    }

    /**
     * Lee líneas CLAVE=valor. Se saltan las vacías y los comentarios (#). Si el valor va entre
     * comillas, se quitan.
     */
    static Map<String, String> leerFicheroEnv(Path fichero) {
        Map<String, String> valores = new HashMap<>();
        List<String> lineas;
        try {
            lineas = Files.readAllLines(fichero, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se ha podido leer " + fichero.toAbsolutePath(), e);
        }
        for (String linea : lineas) {
            String limpia = linea.strip();
            int igual = limpia.indexOf('=');
            if (limpia.isEmpty() || limpia.startsWith("#") || igual <= 0) {
                continue;
            }
            String clave = limpia.substring(0, igual).strip();
            String valor = limpia.substring(igual + 1).strip();
            if (valor.length() >= 2 && (valor.startsWith("\"") && valor.endsWith("\"")
                    || valor.startsWith("'") && valor.endsWith("'"))) {
                valor = valor.substring(1, valor.length() - 1);
            }
            valores.put(clave, valor);
        }
        return valores;
    }

    // ---------------------------------------------------------------------------------------
    // Los valores que usa la API
    // ---------------------------------------------------------------------------------------

    public String dbHost() {
        return opcional("DB_HOST", "127.0.0.1");
    }

    public int dbPuerto() {
        return numero("DB_PORT", 3306);
    }

    public String dbNombre() {
        return opcional("DB_NAME", "clandestino");
    }

    public String dbUsuario() {
        return obligatorio("DB_USER");
    }

    public String dbContrasena() {
        return obligatorio("DB_PASSWORD");
    }

    public String secretoJwt() {
        return obligatorio("JWT_SECRET");
    }

    /**
     * Por defecto la API solo escucha en 127.0.0.1 (este ordenador). El emulador llega igual,
     * porque su 10.0.2.2 es el 127.0.0.1 del PC. Para abrirla a la red habría que poner
     * API_HOST=0.0.0.0 a propósito.
     */
    public String apiHost() {
        return opcional("API_HOST", "127.0.0.1");
    }

    public int apiPuerto() {
        return numero("API_PORT", 8080);
    }

    // ---------------------------------------------------------------------------------------

    private String obligatorio(String clave) {
        String valor = valores.get(clave);
        if (valor == null || valor.isBlank()) {
            // Solo decimos qué clave falta, nunca el valor de las otras.
            throw new IllegalStateException("Falta " + clave + " en el .env (copia .env.example y rellénalo).");
        }
        return valor;
    }

    private String opcional(String clave, String porDefecto) {
        String valor = valores.get(clave);
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }

    private int numero(String clave, int porDefecto) {
        String valor = opcional(clave, null);
        if (valor == null) {
            return porDefecto;
        }
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(clave + " tiene que ser un número y vale '" + valor + "'.");
        }
    }
}
