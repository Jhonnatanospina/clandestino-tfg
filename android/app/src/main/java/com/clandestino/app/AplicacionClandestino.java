package com.clandestino.app;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.clandestino.app.datos.remoto.ClienteApi;
import com.clandestino.app.utilidades.SesionAlmacen;

/**
 * Se crea antes que cualquier pantalla y vive lo que vive el proceso. La usamos para dos cosas:
 * guardar las dos piezas que comparte toda la app (la sesión y el cliente HTTP) y aplicar el
 * tema claro u oscuro antes de que se dibuje la primera pantalla.
 *
 * No usamos ninguna librería de inyección de dependencias: los modelos de vista piden estas
 * piezas a la aplicación y crean sus repositorios con ellas.
 */
public class AplicacionClandestino extends Application {

    private static final String PREFERENCIAS = "ajustes";
    private static final String CLAVE_TEMA_OSCURO = "tema_oscuro";

    private SesionAlmacen sesion;
    private ClienteApi api;

    @Override
    public void onCreate() {
        super.onCreate();
        sesion = new SesionAlmacen(this);
        api = new ClienteApi(BuildConfig.URL_API, sesion);
        aplicarTema(temaOscuro());
    }

    public SesionAlmacen sesion() {
        return sesion;
    }

    public ClienteApi api() {
        return api;
    }

    // El tema es una preferencia del móvil, no de la cuenta: no viaja a la API.
    public boolean temaOscuro() {
        return preferencias().getBoolean(CLAVE_TEMA_OSCURO, false);
    }

    public void guardarTemaOscuro(boolean oscuro) {
        preferencias().edit().putBoolean(CLAVE_TEMA_OSCURO, oscuro).apply();
        aplicarTema(oscuro);
    }

    private void aplicarTema(boolean oscuro) {
        AppCompatDelegate.setDefaultNightMode(oscuro
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private SharedPreferences preferencias() {
        return getSharedPreferences(PREFERENCIAS, MODE_PRIVATE);
    }
}
