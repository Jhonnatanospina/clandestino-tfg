package com.clandestino.app.modelo;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;

/** Un día del calendario de C2 con su estado, tal como llega de GET /api/dias. */
public class Dia {

    private final LocalDate fecha;
    private final EstadoDia estado;

    public Dia(LocalDate fecha, EstadoDia estado) {
        this.fecha = fecha;
        this.estado = estado;
    }

    public static Dia desdeJson(JSONObject json) throws JSONException {
        return new Dia(LecturaJson.fecha(json, "fecha"), EstadoDia.desdeApi(json.getString("estado")));
    }

    /** Solo se puede pulsar un día libre; el resto se pinta, pero no responde. */
    public boolean estaLibre() {
        return estado == EstadoDia.LIBRE;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public EstadoDia getEstado() {
        return estado;
    }
}
