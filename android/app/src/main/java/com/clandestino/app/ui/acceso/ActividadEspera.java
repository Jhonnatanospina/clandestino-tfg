package com.clandestino.app.ui.acceso;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.clandestino.app.R;
import com.clandestino.app.modelo.EstadoUsuario;
import com.clandestino.app.modelo.Usuario;
import com.clandestino.app.modelovista.ModeloVistaAcceso;
import com.clandestino.app.ui.Pantallas;
import com.clandestino.app.utilidades.Resultado;

/**
 * A3 · El cliente aún no está aprobado, o el barbero lo ha bloqueado. No puede hacer nada más
 * que esperar y volver a comprobar; en cuanto la API diga "aprobado", pasa a su panel.
 */
public class ActividadEspera extends AppCompatActivity {

    private ModeloVistaAcceso modelo;
    private SwipeRefreshLayout deslizar;
    private ImageView icono;
    private TextView titulo;
    private TextView saludo;
    private TextView explicacion;
    private TextView chipEstado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.actividad_espera);

        deslizar = findViewById(R.id.deslizar);
        icono = findViewById(R.id.icono);
        titulo = findViewById(R.id.titulo);
        saludo = findViewById(R.id.saludo);
        explicacion = findViewById(R.id.explicacion);
        chipEstado = findViewById(R.id.chip_estado);

        deslizar.setColorSchemeResources(R.color.acento);
        deslizar.setOnRefreshListener(this::onRefrescar);
        findViewById(R.id.boton_recargar).setOnClickListener(v -> onRefrescar());
        findViewById(R.id.boton_comprobar).setOnClickListener(v -> onRefrescar());
        findViewById(R.id.boton_salir).setOnClickListener(v -> Pantallas.cerrarSesion(this));

        // Lo que llega en el Intent se pinta ya; si luego la API dice otra cosa, se repinta.
        boolean bloqueado = getIntent().getBooleanExtra(Pantallas.EXTRA_BLOQUEADO, false);
        pintarCuenta(getIntent().getStringExtra(Pantallas.EXTRA_USUARIO), bloqueado);

        modelo = new ViewModelProvider(this).get(ModeloVistaAcceso.class);
        modelo.getSesion().observe(this, this::pintar);
    }

    private void onRefrescar() {
        modelo.consultarEstado();
    }

    private void pintar(Resultado<Usuario> resultado) {
        deslizar.setRefreshing(resultado.estaCargando());
        if (resultado.esCorrecto()) {
            Usuario usuario = resultado.datos();
            if (usuario.estaAprobado()) {
                Pantallas.abrirSegunCuenta(this, usuario);
                return;
            }
            pintarCuenta(usuario.getUsuario(), usuario.getEstado() == EstadoUsuario.BLOQUEADO);
            if (resultado.avisarUnaVez()) {
                Pantallas.avisar(this, getString(R.string.sigue_igual));
            }
        } else {
            Pantallas.avisarSiFalla(this, resultado);
        }
    }

    private void pintarCuenta(String usuario, boolean bloqueado) {
        icono.setImageResource(bloqueado ? R.drawable.ic_candado : R.drawable.ic_reloj);
        titulo.setText(bloqueado ? R.string.espera_titulo_bloqueado : R.string.espera_titulo_pendiente);
        explicacion.setText(bloqueado ? R.string.espera_texto_bloqueado : R.string.espera_texto_pendiente);
        saludo.setText(getString(R.string.hola_usuario, usuario));
        EstadoUsuario estado = bloqueado ? EstadoUsuario.BLOQUEADO : EstadoUsuario.PENDIENTE;
        Pantallas.pintarChip(chipEstado, estado.etiqueta(), false);
    }
}
