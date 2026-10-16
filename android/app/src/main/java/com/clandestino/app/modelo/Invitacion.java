package com.clandestino.app.modelo;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;

/**
 * Un código de invitación de D4. «Caducada» no es un estado guardado (RN-22): la API lo calcula
 * y lo manda aparte, en el campo "caducada".
 */
public class Invitacion {

    private final long id;
    private final String codigo;
    private final String nota;
    private final boolean usada;
    private final boolean caducada;
    private final LocalDateTime caducaEn;
    private final String usadaPor;

    public Invitacion(long id, String codigo, String nota, boolean usada, boolean caducada,
                      LocalDateTime caducaEn, String usadaPor) {
        this.id = id;
        this.codigo = codigo;
        this.nota = nota;
        this.usada = usada;
        this.caducada = caducada;
        this.caducaEn = caducaEn;
        this.usadaPor = usadaPor;
    }

    public static Invitacion desdeJson(JSONObject json) throws JSONException {
        JSONObject usuario = json.optJSONObject("usada_por");
        return new Invitacion(
                json.getLong("id"),
                json.getString("codigo"),
                LecturaJson.textoONulo(json, "nota"),
                "usada".equals(json.getString("estado")),
                json.getBoolean("caducada"),
                LocalDateTime.parse(json.getString("caduca_en")),
                usuario == null ? null : usuario.getString("usuario"));
    }

    /** Texto del chip: Pendiente, Usada o Caducada. */
    public String etiqueta() {
        if (usada) {
            return "Usada";
        }
        return caducada ? "Caducada" : "Pendiente";
    }

    public boolean estaUsada() {
        return usada;
    }

    public boolean estaCaducada() {
        return caducada;
    }

    public long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNota() {
        return nota;
    }

    public LocalDateTime getCaducaEn() {
        return caducaEn;
    }

    public String getUsadaPor() {
        return usadaPor;
    }
}
