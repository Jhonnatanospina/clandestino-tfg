package com.clandestino.app.datos.repositorio;

import com.clandestino.app.datos.remoto.Callback;
import com.clandestino.app.datos.remoto.ClienteApi;
import com.clandestino.app.modelo.Cita;
import com.clandestino.app.modelo.Dia;
import com.clandestino.app.modelo.Hueco;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Las citas vistas por el cliente: el calendario, las horas de un día, pedir y cancelar. */
public class RepositorioCitas {

    private final ClienteApi api;

    public RepositorioCitas(ClienteApi api) {
        this.api = api;
    }

    /** Los 14 días del horizonte (RN-05). La API ya sabe cuáles son: no hace falta mandar fechas. */
    public void dias(Callback<List<Dia>> callback) {
        api.enviar("GET", "/api/dias", null, ClienteApi.lista("dias", Dia::desdeJson), callback);
    }

    public void huecos(LocalDate fecha, Callback<List<Hueco>> callback) {
        api.enviar("GET", "/api/huecos?fecha=" + fecha, null,
                ClienteApi.lista("huecos", Hueco::desdeJson), callback);
    }

    public void pedir(LocalDate fecha, LocalTime hora, Callback<Cita> callback) {
        api.enviar("POST", "/api/citas",
                () -> new JSONObject().put("fecha", fecha.toString()).put("hora_pedida", hora.toString()),
                Cita::desdeJson, callback);
    }

    public void misCitas(Callback<List<Cita>> callback) {
        api.enviar("GET", "/api/mis-citas", null, ClienteApi.lista("citas", Cita::desdeJson), callback);
    }

    public void cancelar(long citaId, Callback<Cita> callback) {
        api.enviar("POST", "/api/citas/" + citaId + "/cancelar", null, Cita::desdeJson, callback);
    }
}
