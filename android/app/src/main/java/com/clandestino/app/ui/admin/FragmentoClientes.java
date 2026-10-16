package com.clandestino.app.ui.admin;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.clandestino.app.R;
import com.clandestino.app.modelo.Invitacion;
import com.clandestino.app.modelo.Usuario;
import com.clandestino.app.modelovista.ModeloVistaClientes;
import com.clandestino.app.ui.FragmentoPanel;
import com.clandestino.app.ui.Pantallas;
import com.clandestino.app.utilidades.Formatos;
import com.clandestino.app.utilidades.Resultado;

import java.util.List;

/**
 * D4 · Clientes. El barbero genera aquí los códigos de invitación y aprueba o bloquea a los
 * clientes que se registran con ellos. También es donde está su botón de cerrar sesión.
 */
public class FragmentoClientes extends FragmentoPanel {

    private ModeloVistaClientes modelo;
    private SwipeRefreshLayout deslizar;
    private TextView campoNota;
    private View botonGenerar;
    private View cajaCodigo;
    private TextView codigo;
    private TextView tituloClientes;
    private TextView sinInvitaciones;
    private TextView sinClientes;
    private AdaptadorInvitaciones adaptadorInvitaciones;
    private AdaptadorClientes adaptadorClientes;
    private boolean cargandoClientes;
    private boolean cargandoInvitaciones;
    // Para vaciar la nota solo cuando la operación que acaba es la de generar.
    private boolean generando;

    public FragmentoClientes() {
        super(R.layout.fragmento_clientes);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle estadoGuardado) {
        modelo = new ViewModelProvider(this).get(ModeloVistaClientes.class);
        ponerCabecera(vista, getString(R.string.clientes_ceja), R.string.clientes_titulo);
        mostrarBoton(vista, R.id.boton_refrescar, v -> modelo.cargar());
        mostrarBoton(vista, R.id.boton_salir, v -> preguntarCerrarSesion());
        deslizar = vista.findViewById(R.id.deslizar);
        deslizar.setColorSchemeResources(R.color.acento);
        deslizar.setOnRefreshListener(modelo::cargar);

        prepararInvitar(vista);
        prepararListas(vista);

        modelo.getCodigoNuevo().observe(getViewLifecycleOwner(), this::pintarCodigo);
        modelo.getInvitaciones().observe(getViewLifecycleOwner(), this::pintarInvitaciones);
        modelo.getClientes().observe(getViewLifecycleOwner(), this::pintarClientes);
        modelo.getOperacion().observe(getViewLifecycleOwner(), this::pintarOperacion);
        if (modelo.getClientes().getValue() == null) {
            modelo.cargar();
        }
    }

    /** Los clientes nuevos se registran mientras tanto: se recarga al volver a la pestaña. */
    @Override
    public void onHiddenChanged(boolean escondido) {
        super.onHiddenChanged(escondido);
        if (!escondido && modelo != null) {
            modelo.cargar();
        }
    }

    private void prepararInvitar(View vista) {
        campoNota = vista.findViewById(R.id.campo_nota);
        botonGenerar = vista.findViewById(R.id.boton_generar);
        cajaCodigo = vista.findViewById(R.id.caja_codigo);
        codigo = vista.findViewById(R.id.codigo);
        botonGenerar.setOnClickListener(v -> generar());
        campoNota.setOnEditorActionListener((campo, accion, evento) -> {
            if (accion == EditorInfo.IME_ACTION_DONE) {
                generar();
            }
            return false;
        });
        vista.findViewById(R.id.boton_copiar).setOnClickListener(v -> copiarCodigo());
    }

    private void prepararListas(View vista) {
        tituloClientes = vista.findViewById(R.id.titulo_clientes);
        sinInvitaciones = vista.findViewById(R.id.sin_invitaciones);
        sinClientes = vista.findViewById(R.id.sin_clientes);
        adaptadorInvitaciones = new AdaptadorInvitaciones();
        ((RecyclerView) vista.findViewById(R.id.invitaciones)).setAdapter(adaptadorInvitaciones);
        adaptadorClientes = new AdaptadorClientes(new AdaptadorClientes.Acciones() {
            @Override
            public void aprobar(Usuario cliente) {
                modelo.aprobar(cliente.getId());
            }

            @Override
            public void bloquear(Usuario cliente) {
                preguntarBloquear(cliente);
            }
        });
        ((RecyclerView) vista.findViewById(R.id.clientes)).setAdapter(adaptadorClientes);
    }

    private void generar() {
        String nota = Pantallas.texto(campoNota);
        // Se baja el teclado y se quita el cursor para que el código nuevo se vea entero.
        WindowCompat.getInsetsController(requireActivity().getWindow(), campoNota)
                .hide(WindowInsetsCompat.Type.ime());
        campoNota.clearFocus();
        generando = true;
        modelo.generarInvitacion(nota.isEmpty() ? null : nota);
    }

    private void pintarCodigo(Invitacion invitacion) {
        cajaCodigo.setVisibility(invitacion == null ? View.GONE : View.VISIBLE);
        if (invitacion != null) {
            codigo.setText(invitacion.getCodigo());
        }
    }

    private void copiarCodigo() {
        ClipboardManager portapapeles = (ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        portapapeles.setPrimaryClip(ClipData.newPlainText(getString(R.string.copiar),
                codigo.getText()));
        // OJO: desde Android 13 el propio sistema enseña que se ha copiado. Avisar también
        // aquí sería decirlo dos veces.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Pantallas.avisar(requireActivity(), getString(R.string.codigo_copiado));
        }
    }

    private void pintarInvitaciones(Resultado<List<Invitacion>> resultado) {
        cargandoInvitaciones = resultado.estaCargando();
        pintarCargando();
        List<Invitacion> invitaciones = resultado.datos();
        if (invitaciones != null) {
            adaptadorInvitaciones.mostrar(invitaciones, Formatos.hoy());
            sinInvitaciones.setVisibility(invitaciones.isEmpty() ? View.VISIBLE : View.GONE);
        }
        Pantallas.avisarSiFalla(requireActivity(), resultado);
    }

    private void pintarClientes(Resultado<List<Usuario>> resultado) {
        cargandoClientes = resultado.estaCargando();
        pintarCargando();
        List<Usuario> clientes = resultado.datos();
        if (clientes != null) {
            adaptadorClientes.mostrar(clientes);
            sinClientes.setVisibility(clientes.isEmpty() ? View.VISIBLE : View.GONE);
            tituloClientes.setText(getString(R.string.clientes_numero, clientes.size()));
        }
        Pantallas.avisarSiFalla(requireActivity(), resultado);
    }

    private void pintarCargando() {
        deslizar.setRefreshing(cargandoClientes || cargandoInvitaciones);
    }

    private void pintarOperacion(Resultado<String> resultado) {
        botonGenerar.setEnabled(resultado == null || !resultado.estaCargando());
        boolean salioBien = Pantallas.avisarOperacion(requireActivity(), resultado);
        if (resultado != null && !resultado.estaCargando() && generando) {
            generando = false;
            if (salioBien) {
                campoNota.setText(null);
            }
        }
    }

    private void preguntarBloquear(Usuario cliente) {
        Pantallas.confirmar(requireContext(),
                getString(R.string.bloquear_pregunta, cliente.getUsuario()),
                getString(R.string.bloquear_texto), R.string.bloquear,
                () -> modelo.bloquear(cliente.getId()));
    }
}
