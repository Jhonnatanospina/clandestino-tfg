package com.clandestino.app.ui;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.IdRes;
import androidx.annotation.MenuRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.clandestino.app.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Lo común a los dos paneles, el del cliente y el del barbero: una barra de navegación abajo y
 * un fragmento por pestaña. Cada panel solo dice qué menú lleva y qué fragmento va en cada
 * pestaña.
 *
 * Los fragmentos se añaden una vez y luego se esconden y se enseñan (hide/show) en lugar de
 * sustituirse (replace). Así, al volver a una pestaña, sigue como se dejó: la lista en el mismo
 * sitio y el día elegido en el calendario.
 */
public abstract class ActividadPanel extends AppCompatActivity {

    // Los mensajes del ciclo de vida salen en Logcat con esta etiqueta. Al girar el móvil se ve
    // cómo la actividad se destruye y se vuelve a crear (onPause, onStop, onDestroy, onCreate...).
    private static final String ETIQUETA_LOG = "CicloDeVida";

    private BottomNavigationView navegacion;

    /** El menú de la barra de navegación, con una entrada por pestaña. */
    @MenuRes
    protected abstract int menu();

    /** El fragmento nuevo para la pestaña pulsada (solo se crea la primera vez). */
    protected abstract Fragment crearFragmento(@IdRes int pestana);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(ETIQUETA_LOG, getClass().getSimpleName() + ".onCreate (estado guardado: "
                + (savedInstanceState != null) + ")");
        setContentView(R.layout.actividad_panel);

        navegacion = findViewById(R.id.navegacion);
        navegacion.inflateMenu(menu());
        navegacion.setOnItemSelectedListener(item -> {
            mostrar(item.getItemId());
            return true;
        });

        // Tras un giro, el FragmentManager ya recuerda qué fragmentos había y cuál se veía.
        if (savedInstanceState == null) {
            mostrar(navegacion.getSelectedItemId());
        }
    }

    /** Enseña el fragmento de la pestaña y esconde los demás. */
    protected void mostrar(@IdRes int pestana) {
        FragmentManager gestor = getSupportFragmentManager();
        String etiqueta = getResources().getResourceEntryName(pestana);
        FragmentTransaction transaccion = gestor.beginTransaction();
        for (Fragment fragmento : gestor.getFragments()) {
            transaccion.hide(fragmento);
        }
        Fragment destino = gestor.findFragmentByTag(etiqueta);
        if (destino == null) {
            transaccion.add(R.id.contenedor, crearFragmento(pestana), etiqueta);
        } else {
            transaccion.show(destino);
        }
        transaccion.commit();
    }

    /** Cambia de pestaña desde un fragmento (por ejemplo, a "Mis citas" después de pedir). */
    public void irAPestana(@IdRes int pestana) {
        navegacion.setSelectedItemId(pestana);
    }

    /** El @usuario que llegó en el Intent, para los saludos de las cabeceras. */
    public String usuario() {
        return getIntent().getStringExtra(Pantallas.EXTRA_USUARIO);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(ETIQUETA_LOG, getClass().getSimpleName() + ".onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(ETIQUETA_LOG, getClass().getSimpleName() + ".onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(ETIQUETA_LOG, getClass().getSimpleName() + ".onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(ETIQUETA_LOG, getClass().getSimpleName() + ".onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(ETIQUETA_LOG, getClass().getSimpleName() + ".onDestroy");
    }
}
