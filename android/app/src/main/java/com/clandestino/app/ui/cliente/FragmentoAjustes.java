package com.clandestino.app.ui.cliente;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.clandestino.app.BuildConfig;
import com.clandestino.app.R;
import com.clandestino.app.modelo.Usuario;
import com.clandestino.app.modelovista.ModeloVistaAjustes;
import com.clandestino.app.ui.FragmentoPanel;
import com.clandestino.app.ui.Pantallas;
import com.clandestino.app.utilidades.Formatos;
import com.clandestino.app.utilidades.Resultado;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.Locale;

/** C3 · Ajustes: los datos de la cuenta, el tema claro u oscuro y cerrar sesión. */
public class FragmentoAjustes extends FragmentoPanel {

    private ModeloVistaAjustes modelo;
    private TextView avatar;
    private TextView usuario;
    private TextView telefono;
    private TextView chipEstado;

    public FragmentoAjustes() {
        super(R.layout.fragmento_ajustes);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estadoGuardado) {
        modelo = new ViewModelProvider(this).get(ModeloVistaAjustes.class);
        ponerCabecera(vista, saludo(), R.string.ajustes_titulo);

        avatar = vista.findViewById(R.id.avatar);
        usuario = vista.findViewById(R.id.usuario);
        telefono = vista.findViewById(R.id.telefono);
        chipEstado = vista.findViewById(R.id.chip_estado);
        // Mientras llega el perfil ya se puede enseñar el @usuario que vino en el Intent.
        pintarNombre(panel().usuario());

        MaterialButtonToggleGroup grupoTema = vista.findViewById(R.id.grupo_tema);
        modelo.getTemaOscuro().observe(getViewLifecycleOwner(), oscuro ->
                grupoTema.check(oscuro ? R.id.boton_oscuro : R.id.boton_claro));
        grupoTema.addOnButtonCheckedListener((grupo, boton, marcado) -> {
            if (marcado) {
                modelo.cambiarTema(boton == R.id.boton_oscuro);
            }
        });

        vista.findViewById(R.id.boton_cerrar_sesion).setOnClickListener(v -> preguntarCerrarSesion());
        ((TextView) vista.findViewById(R.id.version))
                .setText(getString(R.string.version, BuildConfig.VERSION_NAME));

        modelo.getPerfil().observe(getViewLifecycleOwner(), this::pintarPerfil);
        if (modelo.getPerfil().getValue() == null) {
            modelo.cargarPerfil();
        }
    }

    private void pintarPerfil(Resultado<Usuario> resultado) {
        Usuario cuenta = resultado.datos();
        if (cuenta != null) {
            pintarNombre(cuenta.getUsuario());
            telefono.setText(Formatos.telefono(cuenta.getTelefono()));
            Pantallas.pintarChip(chipEstado, cuenta.getEstado().etiqueta(), cuenta.estaAprobado());
            chipEstado.setVisibility(View.VISIBLE);
        }
        Pantallas.avisarSiFalla(requireActivity(), resultado);
    }

    private void pintarNombre(String nombre) {
        if (nombre == null || nombre.isEmpty()) {
            return;
        }
        usuario.setText(getString(R.string.arroba_usuario, nombre));
        avatar.setText(nombre.substring(0, 1).toUpperCase(Locale.ROOT));
    }
}
