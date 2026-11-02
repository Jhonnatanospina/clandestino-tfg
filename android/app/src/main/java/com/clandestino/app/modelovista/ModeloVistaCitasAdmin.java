package com.clandestino.app.modelovista;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.clandestino.app.R;
import com.clandestino.app.datos.repositorio.RepositorioAgenda;
import com.clandestino.app.datos.repositorio.RepositorioClientes;
import com.clandestino.app.modelo.Cita;
import com.clandestino.app.modelo.Usuario;
import com.clandestino.app.utilidades.Resultado;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Citas del barbero. Lo usan D1 (solicitudes pendientes) y D2 (agenda de confirmadas), cada
 * pantalla con su propia instancia: cada una recuerda su filtro y no se pisan. El diálogo de
 * la cita a mano (D5) usa la de la agenda, así la cita nueva sale en cuanto se guarda.
 */
public class ModeloVistaCitasAdmin extends ModeloVistaBase {

    private final RepositorioAgenda repo;
    private final RepositorioClientes repoClientes;
    private final MutableLiveData<Resultado<List<Cita>>> citas = new MutableLiveData<>();
    private final MutableLiveData<Resultado<List<Usuario>>> clientes = new MutableLiveData<>();

    // El último filtro, para poder recargar después de cada acción.
    private String estado;
    private LocalDate desde;
    private LocalDate hasta;

    public ModeloVistaCitasAdmin(@NonNull Application aplicacion) {
        super(aplicacion);
        repo = new RepositorioAgenda(app().api());
        repoClientes = new RepositorioClientes(app().api());
    }

    public LiveData<Resultado<List<Cita>>> getCitas() {
        return citas;
    }

    public LiveData<Resultado<List<Usuario>>> getClientes() {
        return clientes;
    }

    /** @param desde y hasta pueden ir a null para no limitar por fecha */
    public void cargar(String estado, LocalDate desde, LocalDate hasta) {
        this.estado = estado;
        this.desde = desde;
        this.hasta = hasta;
        recargar();
    }

    public void recargar() {
        repo.citas(estado, desde, hasta, Resultado.en(citas));
    }

    public void confirmar(long id, LocalTime horaFinal) {
        repo.confirmar(id, horaFinal, accion(R.string.cita_confirmada, this::recargar));
    }

    public void rechazar(long id) {
        repo.rechazar(id, accion(R.string.solicitud_rechazada, this::recargar));
    }

    public void marcarAsistencia(long id, boolean hecha) {
        int mensaje = hecha ? R.string.marcada_hecha : R.string.marcada_no_vino;
        repo.marcarAsistencia(id, hecha, accion(mensaje, this::recargar));
    }

    public void cancelar(long id) {
        repo.cancelar(id, accion(R.string.cita_cancelada, this::recargar));
    }

    /** Para el desplegable de D5. Se piden cada vez que se abre, por si hay alguno nuevo. */
    public void cargarClientes() {
        repoClientes.aprobados(Resultado.en(clientes));
    }

    public void crearCita(long clienteId, LocalDate fecha, LocalTime hora) {
        repo.crearCita(clienteId, fecha, hora, accion(R.string.cita_guardada, this::recargar));
    }
}
