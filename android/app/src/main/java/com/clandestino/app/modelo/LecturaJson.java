package com.clandestino.app.modelo;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Lectura de los campos que la API puede mandar a null (hora_final, nota, cupo...).
 *
 * OJO: no vale json.optString("nota"). Si el campo es null, org.json devuelve el texto "null"
 * y acabaría pintado en la pantalla. Hay que preguntar antes con isNull().
 */
final class LecturaJson {

    private LecturaJson() {
    }

    static String textoONulo(JSONObject json, String campo) throws JSONException {
        return json.isNull(campo) ? null : json.getString(campo);
    }

    static Integer enteroONulo(JSONObject json, String campo) throws JSONException {
        return json.isNull(campo) ? null : json.getInt(campo);
    }

    static LocalTime horaONula(JSONObject json, String campo) throws JSONException {
        return json.isNull(campo) ? null : LocalTime.parse(json.getString(campo));
    }

    static LocalTime hora(JSONObject json, String campo) throws JSONException {
        return LocalTime.parse(json.getString(campo));
    }

    static LocalDate fecha(JSONObject json, String campo) throws JSONException {
        return LocalDate.parse(json.getString(campo));
    }
}
