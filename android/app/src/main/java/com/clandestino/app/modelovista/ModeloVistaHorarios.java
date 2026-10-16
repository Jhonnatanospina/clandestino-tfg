package com.clandestino.app.modelovista;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.clandestino.app.R;
import com.clandestino.app.datos.repositorio.RepositorioHorarios;
import com.clandestino.app.modelo.ExcepcionDia;
import com.clandestino.app.modelo.HorarioSemanal;
import com.clandestino.app.utilidades.Resultado;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * D3: la plantilla semanal y los días especiales. Los cambios de la plantilla se guardan aquí
 * mientras el barbero edita, así sobreviven a un giro de pantalla, y solo viajan a la API
 * cuando pulsa Guardar.
 */
public class ModeloVistaHorarios extends ModeloVistaBase {

    private final RepositorioHorarios repo;
    private final MutableLiveData<Resultado<List<HorarioSemanal>>> horarios = new MutableLiveData<>();
    private final MutableLiveData<Resultado<List<ExcepcionDia>>> excepciones = new MutableLiveData<>();

    public ModeloVistaHorarios(@NonNull Application aplicacion) {
        super(aplicacion);
        repo = new RepositorioHorarios(app().api());
    }

    public LiveData<Resultado<List<HorarioSemanal>>> getHorarios() {
        return horarios;
    }

    public LiveData<Resultado<List<ExcepcionDia>>> getExcepciones() {
        return excepciones;
    }

    public void cargar() {
        cargarSemana();
        cargarExcepciones();
    }

    private void cargarExcepciones() {
        repo.excepciones(Resultado.en(excepciones));
    }

    /** Cambia un día de la plantilla en memoria. Todavía no se manda nada a la API. */
    public void cambiarDia(HorarioSemanal cambiado) {
        Resultado<List<HorarioSemanal>> actual = horarios.getValue();
        if (actual == null || actual.datos() == null) {
            return;
        }
        List<HorarioSemanal> semana = new ArrayList<>(actual.datos());
        semana.set(cambiado.getDiaSemana() - 1, cambiado);
        horarios.setValue(Resultado.exito(semana));
    }

    /** La lista de los siete días tal como está ahora en pantalla, o null si aún no ha cargado. */
    public List<HorarioSemanal> semanaEnPantalla() {
        Resultado<List<HorarioSemanal>> actual = horarios.getValue();
        return actual == null ? null : actual.datos();
    }

    public void guardarSemana() {
        List<HorarioSemanal> semana = semanaEnPantalla();
        if (semana == null) {
            return;
        }
        repo.guardarSemanal(semana, accion(R.string.horario_guardado, this::cargarSemana));
    }

    private void cargarSemana() {
        repo.semanal(Resultado.en(horarios));
    }

    public void cerrarDias(LocalDate desde, LocalDate hasta, String nota) {
        repo.cerrarDias(desde, hasta, nota, accion(R.string.dia_especial_guardado, this::cargarExcepciones));
    }

    public void abrirDiaExtra(LocalDate fecha, LocalTime inicio, LocalTime fin, int cupo, String nota) {
        repo.abrirDiaExtra(fecha, inicio, fin, cupo, nota,
                accion(R.string.dia_especial_guardado, this::cargarExcepciones));
    }

    public void borrarExcepcion(long id) {
        repo.borrarExcepcion(id, accion(R.string.dia_especial_borrado, this::cargarExcepciones));
    }
}
