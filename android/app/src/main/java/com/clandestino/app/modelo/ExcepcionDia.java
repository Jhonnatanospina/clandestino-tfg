package com.clandestino.app.modelo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Un día que se sale del horario semanal: o se cierra (vacaciones) o se abre aunque normalmente
 * no se trabaje (día extra, con su propio tramo y cupo).
 */
public class ExcepcionDia {

    private final long id;
    private final LocalDate fecha;
    private final boolean abierto;
    private final LocalTime inicio;
    private final LocalTime fin;
    private final Integer cupo;
    private final String nota;

    public ExcepcionDia(long id, LocalDate fecha, boolean abierto, LocalTime inicio, LocalTime fin,
                        Integer cupo, String nota) {
        this.id = id;
        this.fecha = fecha;
        this.abierto = abierto;
        this.inicio = inicio;
        this.fin = fin;
        this.cupo = cupo;
        this.nota = nota;
    }

    public static ExcepcionDia desdeJson(JSONObject json) throws JSONException {
        return new ExcepcionDia(
                json.getLong("id"),
                LecturaJson.fecha(json, "fecha"),
                "abierto".equals(json.getString("tipo")),
                LecturaJson.horaONula(json, "hora_inicio"),
                LecturaJson.horaONula(json, "hora_fin"),
                LecturaJson.enteroONulo(json, "cupo"),
                LecturaJson.textoONulo(json, "nota"));
    }

    /**
     * Cuerpo de POST /api/admin/excepciones para cerrar un rango de días seguidos. La API admite
     * hasta 31 fechas por petición; el diálogo ya no deja elegir más.
     */
    public static JSONObject cuerpoCierre(LocalDate desde, LocalDate hasta, String nota)
            throws JSONException {
        JSONArray fechas = new JSONArray();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            fechas.put(dia.toString());
        }
        JSONObject cuerpo = new JSONObject().put("fechas", fechas).put("tipo", "cerrado");
        return conNota(cuerpo, nota);
    }

    /** Cuerpo para abrir un solo día con su tramo y su cupo. */
    public static JSONObject cuerpoDiaExtra(LocalDate fecha, LocalTime inicio, LocalTime fin, int cupo,
                                            String nota) throws JSONException {
        JSONObject cuerpo = new JSONObject()
                .put("fechas", new JSONArray().put(fecha.toString()))
                .put("tipo", "abierto")
                .put("hora_inicio", inicio.toString())
                .put("hora_fin", fin.toString())
                .put("cupo", cupo);
        return conNota(cuerpo, nota);
    }

    private static JSONObject conNota(JSONObject cuerpo, String nota) throws JSONException {
        // Una nota vacía no se manda: en la base de datos queda NULL y no un texto en blanco.
        if (nota != null && !nota.trim().isEmpty()) {
            cuerpo.put("nota", nota.trim());
        }
        return cuerpo;
    }

    public long getId() {
        return id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public boolean esDiaExtra() {
        return abierto;
    }

    public LocalTime getInicio() {
        return inicio;
    }

    public LocalTime getFin() {
        return fin;
    }

    /** Puede ser null en un día extra: entonces la API aplica el cupo por defecto. */
    public Integer getCupo() {
        return cupo;
    }

    public String getNota() {
        return nota;
    }
}
