package com.clandestino.app.utilidades;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Guarda el token de sesión (el JWT) entre una apertura de la app y la siguiente.
 *
 * Quien tenga el token puede hacerse pasar por el usuario durante 7 días, así que no lo dejamos
 * en claro en SharedPreferences: lo ciframos con AES-GCM y una clave que vive en el Android
 * Keystore. Esa clave no se puede sacar del móvil, ni siquiera desde la propia app; solo se le
 * pide que cifre o descifre. En las preferencias quedan el IV y el texto cifrado.
 *
 * No usamos EncryptedSharedPreferences porque Google la ha marcado como obsoleta.
 */
public class SesionAlmacen {

    private static final String TAG = "SesionAlmacen";
    private static final String ALMACEN_ANDROID = "AndroidKeyStore";
    private static final String ALIAS_CLAVE = "clandestino_sesion";
    private static final String TRANSFORMACION = "AES/GCM/NoPadding";
    // 128 bits es el tamaño de la etiqueta de autenticación que recomienda GCM.
    private static final int BITS_ETIQUETA = 128;

    private static final String PREFERENCIAS = "sesion";
    private static final String CLAVE_IV = "iv";
    private static final String CLAVE_TOKEN = "token";

    private final SharedPreferences preferencias;
    // Descifrar en cada petición sería gastar para nada: tras la primera lectura queda en memoria.
    private String tokenEnMemoria;

    public SesionAlmacen(Context contexto) {
        preferencias = contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE);
    }

    // Los métodos son synchronized porque ClienteApi lee el token desde los hilos de las
    // peticiones mientras la pantalla puede estar guardándolo o borrándolo.

    public synchronized void guardarToken(String token) {
        tokenEnMemoria = token;
        try {
            Cipher cifrador = Cipher.getInstance(TRANSFORMACION);
            // OJO: el IV no lo elegimos nosotros. El Keystore genera uno nuevo y aleatorio en cada
            // cifrado (y rechaza uno impuesto); lo recogemos con getIV() para poder descifrar.
            cifrador.init(Cipher.ENCRYPT_MODE, claveDelKeystore());
            byte[] cifrado = cifrador.doFinal(token.getBytes(StandardCharsets.UTF_8));
            preferencias.edit()
                    .putString(CLAVE_IV, Base64.getEncoder().encodeToString(cifrador.getIV()))
                    .putString(CLAVE_TOKEN, Base64.getEncoder().encodeToString(cifrado))
                    .apply();
        } catch (GeneralSecurityException | IOException e) {
            // Sin Keystore la sesión sirve mientras la app esté abierta, pero no se guarda.
            Log.w(TAG, "No se ha podido cifrar el token; la sesión no se recordará", e);
        }
    }

    /** Devuelve el token guardado, o null si no hay sesión. */
    public synchronized String leerToken() {
        if (tokenEnMemoria != null) {
            return tokenEnMemoria;
        }
        String iv = preferencias.getString(CLAVE_IV, null);
        String cifrado = preferencias.getString(CLAVE_TOKEN, null);
        if (iv == null || cifrado == null) {
            return null;
        }
        try {
            Cipher cifrador = Cipher.getInstance(TRANSFORMACION);
            cifrador.init(Cipher.DECRYPT_MODE, claveDelKeystore(),
                    new GCMParameterSpec(BITS_ETIQUETA, Base64.getDecoder().decode(iv)));
            tokenEnMemoria = new String(cifrador.doFinal(Base64.getDecoder().decode(cifrado)),
                    StandardCharsets.UTF_8);
            return tokenEnMemoria;
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            // Pasa si se ha borrado la clave del Keystore (por ejemplo, al restaurar una copia
            // en otro móvil). Lo guardado ya no sirve: se borra y toca volver a entrar.
            Log.w(TAG, "No se ha podido descifrar el token guardado", e);
            borrar();
            return null;
        }
    }

    public synchronized void borrar() {
        tokenEnMemoria = null;
        preferencias.edit().clear().apply();
    }

    /** La clave AES del Keystore. La primera vez no existe y se crea. */
    private SecretKey claveDelKeystore() throws GeneralSecurityException, IOException {
        KeyStore almacen = KeyStore.getInstance(ALMACEN_ANDROID);
        almacen.load(null);
        if (almacen.containsAlias(ALIAS_CLAVE)) {
            return (SecretKey) almacen.getKey(ALIAS_CLAVE, null);
        }
        KeyGenerator generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ALMACEN_ANDROID);
        generador.init(new KeyGenParameterSpec.Builder(ALIAS_CLAVE,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generador.generateKey();
    }
}
