package com.clandestino.app.modelo;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalTime;

/**
 * Una hora del día que se puede pedir. La API no dice quién la tiene ocupada, solo si lo está:
 * el cliente no debe ver las citas de los demás.
 */
public class Hueco {

    private final LocalTime hora;
    private final boolean ocupado;

    public Hueco(LocalTime hora, boolean ocupado) {
        this.hora = hora;
        this.ocupado = ocupado;
    }

    public static Hueco desdeJson(JSONObject json) throws JSONException {
        return new Hueco(LecturaJson.hora(json, "hora"), json.getBoolean("ocupado"));
    }

    public LocalTime getHora() {
        return hora;
    }

    public boolean estaOcupado() {
        return ocupado;
    }
}
