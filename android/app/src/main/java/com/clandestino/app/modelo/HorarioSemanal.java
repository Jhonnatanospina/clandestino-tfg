package com.clandestino.app.modelo;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/**
 * El horario de un día de la semana (1 = lunes ... 7 = domingo, como en la API). Es inmutable:
 * para cambiar algo desde D3 se crea otro con los métodos con...().
 */
public class HorarioSemanal {

    /** Lo que se propone al encender un día que nunca ha tenido horario. */
    private static final LocalTime INICIO_POR_DEFECTO = LocalTime.of(14, 0);
    private static final LocalTime FIN_POR_DEFECTO = LocalTime.of(20, 0);
    private static final int CUPO_POR_DEFECTO = 2;

    private final int diaSemana;
    private final LocalTime inicio;
    private final LocalTime fin;
    private final int cupo;
    private final boolean activo;

    public HorarioSemanal(int diaSemana, LocalTime inicio, LocalTime fin, int cupo, boolean activo) {
        this.diaSemana = diaSemana;
        this.inicio = inicio;
        this.fin = fin;
        this.cupo = cupo;
        this.activo = activo;
    }

    public static HorarioSemanal desdeJson(JSONObject json) throws JSONException {
        return new HorarioSemanal(json.getInt("dia_semana"), LecturaJson.hora(json, "hora_inicio"),
                LecturaJson.hora(json, "hora_fin"), json.getInt("cupo"), json.getBoolean("activo"));
    }

    /**
     * La API solo devuelve los días que tienen fila en horarios_semanales. Aquí se rellenan los
     * que faltan, apagados, para que la plantilla tenga siempre sus siete filas de lunes a domingo.
     */
    public static List<HorarioSemanal> semanaCompleta(List<HorarioSemanal> guardados) {
        HorarioSemanal[] semana = new HorarioSemanal[7];
        for (HorarioSemanal horario : guardados) {
            semana[horario.diaSemana - 1] = horario;
        }
        for (int i = 0; i < semana.length; i++) {
            if (semana[i] == null) {
                semana[i] = new HorarioSemanal(i + 1, INICIO_POR_DEFECTO, FIN_POR_DEFECTO,
                        CUPO_POR_DEFECTO, false);
            }
        }
        return Arrays.asList(semana);
    }

    /** El cuerpo que espera PUT /api/admin/horarios para este día. */
    public JSONObject aJson() throws JSONException {
        return new JSONObject()
                .put("dia_semana", diaSemana)
                .put("hora_inicio", inicio.toString())
                .put("hora_fin", fin.toString())
                .put("cupo", cupo)
                .put("activo", activo);
    }

    public HorarioSemanal conInicio(LocalTime nuevoInicio) {
        return new HorarioSemanal(diaSemana, nuevoInicio, fin, cupo, activo);
    }

    public HorarioSemanal conFin(LocalTime nuevoFin) {
        return new HorarioSemanal(diaSemana, inicio, nuevoFin, cupo, activo);
    }

    public HorarioSemanal conCupo(int nuevoCupo) {
        return new HorarioSemanal(diaSemana, inicio, fin, nuevoCupo, activo);
    }

    public HorarioSemanal conActivo(boolean nuevoActivo) {
        return new HorarioSemanal(diaSemana, inicio, fin, cupo, nuevoActivo);
    }

    /** La base de datos exige que el tramo empiece antes de acabar (ck_horarios_tramo). */
    public boolean tramoValido() {
        return inicio.isBefore(fin);
    }

    public int getDiaSemana() {
        return diaSemana;
    }

    public LocalTime getInicio() {
        return inicio;
    }

    public LocalTime getFin() {
        return fin;
    }

    public int getCupo() {
        return cupo;
    }

    public boolean estaActivo() {
        return activo;
    }
}
