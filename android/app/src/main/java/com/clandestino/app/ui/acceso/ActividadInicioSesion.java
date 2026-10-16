package com.clandestino.app.ui.acceso;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.clandestino.app.R;
import com.clandestino.app.modelo.Usuario;
import com.clandestino.app.modelovista.ModeloVistaAcceso;
import com.clandestino.app.ui.Pantallas;
import com.clandestino.app.utilidades.Resultado;

/**
 * A1 · Iniciar sesión. Es la actividad de arranque: si ya hay una sesión guardada no enseña
 * el formulario, pregunta a la API de quién es el token y salta directamente a su panel.
 */
public class ActividadInicioSesion extends AppCompatActivity {

    private ModeloVistaAcceso modelo;
    private View formulario;
    private View progreso;
    private View botonEntrar;
    private EditText campoUsuario;
    private EditText campoContrasena;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.actividad_inicio_sesion);

        formulario = findViewById(R.id.formulario);
        progreso = findViewById(R.id.progreso);
        botonEntrar = findViewById(R.id.boton_entrar);
        campoUsuario = findViewById(R.id.campo_usuario);
        campoContrasena = findViewById(R.id.campo_contrasena);

        botonEntrar.setOnClickListener(v -> onEntrar());
        // "Hecho" en el teclado hace lo mismo que el botón.
        campoContrasena.setOnEditorActionListener((v, accion, evento) -> {
            if (accion == EditorInfo.IME_ACTION_DONE) {
                onEntrar();
                return true;
            }
            return false;
        });
        findViewById(R.id.boton_crear_cuenta).setOnClickListener(
                v -> startActivity(new Intent(this, ActividadRegistro.class)));

        modelo = new ViewModelProvider(this).get(ModeloVistaAcceso.class);
        modelo.getSesion().observe(this, this::pintar);

        // Solo la primera vez: al girar el móvil el modelo de vista ya tiene la respuesta.
        if (savedInstanceState == null && modelo.haySesion()) {
            formulario.setVisibility(View.INVISIBLE);
            modelo.consultarEstado();
        }
    }

    private void onEntrar() {
        String usuario = Pantallas.texto(campoUsuario);
        String contrasena = campoContrasena.getText().toString();
        if (usuario.isEmpty() || contrasena.isEmpty()) {
            Pantallas.avisar(this, getString(R.string.falta_usuario_o_contrasena));
            return;
        }
        modelo.iniciarSesion(usuario, contrasena);
    }

    private void pintar(Resultado<Usuario> resultado) {
        boolean cargando = resultado.estaCargando();
        progreso.setVisibility(cargando ? View.VISIBLE : View.GONE);
        botonEntrar.setEnabled(!cargando);

        if (resultado.esCorrecto()) {
            Pantallas.abrirSegunCuenta(this, resultado.datos());
            finish();
        } else if (resultado.haFallado()) {
            // Si fallaba la sesión guardada (sin red, por ejemplo) se enseña el formulario.
            formulario.setVisibility(View.VISIBLE);
            Pantallas.avisarSiFalla(this, resultado);
        }
    }
}
