package com.clandestino.app.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.clandestino.app.AplicacionClandestino;
import com.clandestino.app.R;
import com.clandestino.app.datos.remoto.ErrorApi;
import com.clandestino.app.modelo.EstadoUsuario;
import com.clandestino.app.modelo.Usuario;
import com.clandestino.app.ui.acceso.ActividadEspera;
import com.clandestino.app.ui.acceso.ActividadInicioSesion;
import com.clandestino.app.ui.admin.ActividadAdmin;
import com.clandestino.app.ui.cliente.ActividadCliente;
import com.clandestino.app.utilidades.Resultado;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

/**
 * Lo que repiten todas las pantallas: a qué actividad se va según la cuenta, cómo se enseña un
 * error, el diálogo de "¿seguro?" y el chip de estado. Son métodos estáticos porque no guardan
 * nada; así cada pantalla no tiene su propia copia.
 */
public final class Pantallas {

    /** El @usuario viaja en el Intent para poner "Hola, @marta" sin esperar a la API. */
    public static final String EXTRA_USUARIO = "usuario";
    /** Solo para ActividadEspera: true si la cuenta está bloqueada y no pendiente. */
    public static final String EXTRA_BLOQUEADO = "bloqueado";

    private Pantallas() {
    }

    /**
     * Abre la pantalla que toca a esta cuenta: el panel del barbero, el del cliente o la espera.
     * Borra la pila de actividades para que el botón atrás no devuelva al login.
     */
    public static void abrirSegunCuenta(Activity origen, Usuario usuario) {
        Class<?> destino;
        if (usuario.esAdmin()) {
            destino = ActividadAdmin.class;
        } else if (usuario.estaAprobado()) {
            destino = ActividadCliente.class;
        } else {
            destino = ActividadEspera.class;
        }
        Intent intent = new Intent(origen, destino)
                .putExtra(EXTRA_USUARIO, usuario.getUsuario())
                .putExtra(EXTRA_BLOQUEADO, usuario.getEstado() == EstadoUsuario.BLOQUEADO)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        origen.startActivity(intent);
    }

    /**
     * Olvida el token y vuelve al login, también sin dejar nada detrás. El JWT seguirá siendo
     * válido hasta que caduque, pero ya no lo tiene nadie.
     */
    public static void cerrarSesion(Activity origen) {
        ((AplicacionClandestino) origen.getApplication()).sesion().borrar();
        Intent intent = new Intent(origen, ActividadInicioSesion.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        origen.startActivity(intent);
    }

    /**
     * Enseña un error de la API. Dos casos no se quedan en un aviso: si la sesión ya no vale se
     * vuelve al login, y si el barbero ha bloqueado al cliente con la app abierta se le lleva a
     * la pantalla de espera, que le explica qué pasa.
     */
    public static void mostrarError(Activity actividad, ErrorApi error) {
        if (error.sesionNoValida()) {
            cerrarSesion(actividad);
        } else if (error.cuentaNoAprobada()) {
            String usuario = actividad.getIntent().getStringExtra(EXTRA_USUARIO);
            Intent intent = new Intent(actividad, ActividadEspera.class)
                    .putExtra(EXTRA_USUARIO, usuario)
                    .putExtra(EXTRA_BLOQUEADO, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            actividad.startActivity(intent);
        } else {
            avisar(actividad, error.getMessage());
        }
    }

    /** Para las cargas de listas: si han fallado, se enseña el error una sola vez. */
    public static void avisarSiFalla(Activity actividad, Resultado<?> resultado) {
        if (resultado != null && resultado.haFallado() && resultado.avisarUnaVez()) {
            mostrarError(actividad, resultado.error());
        }
    }

    /**
     * Para las acciones (cancelar, confirmar...): el mensaje de éxito o el error, una sola vez.
     * Devuelve true si acaba de avisar de un éxito, por si la pantalla tiene que hacer algo más
     * (por ejemplo, ir a "Mis citas" después de pedir).
     */
    public static boolean avisarOperacion(Activity actividad, Resultado<String> resultado) {
        if (resultado == null || resultado.estaCargando() || !resultado.avisarUnaVez()) {
            return false;
        }
        if (resultado.haFallado()) {
            mostrarError(actividad, resultado.error());
            return false;
        }
        if (resultado.datos() != null) {
            avisar(actividad, resultado.datos());
        }
        return true;
    }

    /**
     * Snackbar en la parte de abajo. En los paneles con barra de navegación se ancla encima de
     * ella; si no, la taparía.
     */
    public static Snackbar avisar(Activity actividad, String texto) {
        View raiz = actividad.findViewById(android.R.id.content);
        Snackbar aviso = Snackbar.make(raiz, texto, Snackbar.LENGTH_LONG);
        View navegacion = actividad.findViewById(R.id.navegacion);
        if (navegacion != null) {
            aviso.setAnchorView(navegacion);
        }
        aviso.show();
        return aviso;
    }

    /** Lo escrito en un campo, sin espacios a los lados. No sirve para contraseñas. */
    public static String texto(TextView campo) {
        CharSequence escrito = campo.getText();
        return escrito == null ? "" : escrito.toString().trim();
    }

    /** Diálogo de "¿seguro?" para lo que no tiene vuelta atrás (cancelar, rechazar, bloquear...). */
    public static void confirmar(Context contexto, String titulo, String texto,
                                 @StringRes int botonSi, Runnable siAcepta) {
        new MaterialAlertDialogBuilder(contexto)
                .setTitle(titulo)
                .setMessage(texto)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(botonSi, (dialogo, boton) -> siAcepta.run())
                .show();
    }

    /**
     * Pinta un chip de estado. Los destacados (confirmada, hecha, aprobado...) van en rojo
     * suave y el resto en gris, como en los mockups.
     */
    public static void pintarChip(TextView chip, String texto, boolean destacado) {
        Context contexto = chip.getContext();
        int fondo = ContextCompat.getColor(contexto, destacado ? R.color.acento_suave : R.color.elevado);
        int tinta = ContextCompat.getColor(contexto, destacado ? R.color.acento_tinta : R.color.secundario);
        int punto = ContextCompat.getColor(contexto, destacado ? R.color.acento : R.color.silencio);
        chip.setText(texto);
        chip.setTextColor(tinta);
        chip.setBackgroundTintList(ColorStateList.valueOf(fondo));
        TextViewCompat.setCompoundDrawableTintList(chip, ColorStateList.valueOf(punto));
    }
}
