package com.clandestino.app.ui.acceso;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.clandestino.app.R;
import com.clandestino.app.modelo.Usuario;
import com.clandestino.app.modelovista.ModeloVistaAcceso;
import com.clandestino.app.ui.Pantallas;
import com.clandestino.app.utilidades.Resultado;
import com.clandestino.app.utilidades.Validaciones;
import com.google.android.material.textfield.TextInputLayout;

import java.util.function.Predicate;

/**
 * A2 · Registro con el código que da el barbero. Cada campo se comprueba mientras se escribe;
 * al terminar, la API crea la cuenta pendiente y la app pasa a la pantalla de espera.
 */
public class ActividadRegistro extends AppCompatActivity {

    private ModeloVistaAcceso modelo;
    private TextInputLayout cajaCodigo;
    private TextInputLayout cajaUsuario;
    private TextInputLayout cajaTelefono;
    private TextInputLayout cajaContrasena;
    private View botonCrear;
    private View progreso;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.actividad_registro);

        cajaCodigo = findViewById(R.id.caja_codigo);
        cajaUsuario = findViewById(R.id.caja_usuario);
        cajaTelefono = findViewById(R.id.caja_telefono);
        cajaContrasena = findViewById(R.id.caja_contrasena);
        botonCrear = findViewById(R.id.boton_crear_cuenta);
        progreso = findViewById(R.id.progreso);

        findViewById(R.id.boton_volver).setOnClickListener(v -> finish());
        botonCrear.setOnClickListener(v -> onRegistrar());
        validarEnVivo();

        modelo = new ViewModelProvider(this).get(ModeloVistaAcceso.class);
        modelo.getSesion().observe(this, this::pintar);
    }

    /** Engancha cada campo con su regla y su mensaje de error. */
    private void validarEnVivo() {
        vigilar(cajaCodigo, Validaciones::codigoValido, R.string.error_codigo);
        vigilar(cajaUsuario, Validaciones::usuarioValido, R.string.error_usuario);
        vigilar(cajaTelefono, Validaciones::telefonoValido, R.string.error_telefono);
        vigilar(cajaContrasena, Validaciones::contrasenaValida, R.string.error_contrasena);
    }

    /**
     * Mientras el campo está vacío no se marca nada: un error en rojo antes de haber escrito
     * nada solo molesta. En cuanto hay texto, se marca o se quita el error en cada letra.
     */
    private void vigilar(TextInputLayout caja, Predicate<String> regla, @StringRes int error) {
        caja.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence texto, int inicio, int antes, int despues) {
            }

            @Override
            public void onTextChanged(CharSequence texto, int inicio, int antes, int despues) {
            }

            @Override
            public void afterTextChanged(Editable texto) {
                boolean bien = texto.length() == 0 || regla.test(texto.toString());
                caja.setError(bien ? null : getString(error));
            }
        });
    }

    private void onRegistrar() {
        String codigo = Pantallas.texto(cajaCodigo.getEditText());
        String usuario = Pantallas.texto(cajaUsuario.getEditText());
        String telefono = Pantallas.texto(cajaTelefono.getEditText());
        String contrasena = cajaContrasena.getEditText().getText().toString();

        // El & no corta: así se marcan todos los campos mal escritos a la vez y no uno por uno.
        boolean todoBien = comprobar(cajaCodigo, Validaciones.codigoValido(codigo), R.string.error_codigo)
                & comprobar(cajaUsuario, Validaciones.usuarioValido(usuario), R.string.error_usuario)
                & comprobar(cajaTelefono, Validaciones.telefonoValido(telefono), R.string.error_telefono)
                & comprobar(cajaContrasena, Validaciones.contrasenaValida(contrasena),
                R.string.error_contrasena);
        if (todoBien) {
            modelo.registrarse(codigo, usuario, telefono, contrasena);
        }
    }

    private boolean comprobar(TextInputLayout caja, boolean valido, @StringRes int error) {
        caja.setError(valido ? null : getString(error));
        return valido;
    }

    private void pintar(Resultado<Usuario> resultado) {
        boolean cargando = resultado.estaCargando();
        progreso.setVisibility(cargando ? View.VISIBLE : View.GONE);
        botonCrear.setEnabled(!cargando);

        if (resultado.esCorrecto()) {
            Pantallas.abrirSegunCuenta(this, resultado.datos());
        } else {
            // Código caducado o ya usado, usuario cogido... La API explica cuál ha sido.
            Pantallas.avisarSiFalla(this, resultado);
        }
    }
}
