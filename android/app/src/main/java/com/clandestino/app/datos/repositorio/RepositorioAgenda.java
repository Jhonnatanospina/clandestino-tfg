package com.clandestino.app.datos.repositorio;

import com.clandestino.app.datos.remoto.Callback;
import com.clandestino.app.datos.remoto.ClienteApi;
import com.clandestino.app.modelo.Cita;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Las citas vistas por el barbero: solicitudes pendientes y agenda de confirmadas. */
public class RepositorioAgenda {

    private final ClienteApi api;

    public RepositorioAgenda(ClienteApi api) {
        this.api = api;
    }

    /**
     * @param desde null para no poner límite (D1 pide todas las pendientes)
     * @param hasta null para no poner límite
     */
    public void citas(String estado, LocalDate desde, LocalDate hasta, Callback<List<Cita>> callback) {
        StringBuilder ruta = new StringBuilder("/api/admin/citas?estado=").append(estado);
        if (desde != null) {
            ruta.append("&desde=").append(desde);
        }
        if (hasta != null) {
            ruta.append("&hasta=").append(hasta);
        }
        api.enviar("GET", ruta.toString(), null, ClienteApi.lista("citas", Cita::desdeJson), callback);
    }

    public void confirmar(long id, LocalTime horaFinal, Callback<Cita> callback) {
        api.enviar("POST", "/api/admin/citas/" + id + "/confirmar",
                () -> new JSONObject().put("hora_final", horaFinal.toString()),
                Cita::desdeJson, callback);
    }

    public void rechazar(long id, Callback<Cita> callback) {
        api.enviar("POST", "/api/admin/citas/" + id + "/rechazar", null, Cita::desdeJson, callback);
    }

    public void cancelar(long id, Callback<Cita> callback) {
        api.enviar("POST", "/api/admin/citas/" + id + "/cancelar", null, Cita::desdeJson, callback);
    }

    /** @param hecha true si vino ("hecha") y false si no se presentó ("no_asistio") */
    public void marcarAsistencia(long id, boolean hecha, Callback<Cita> callback) {
        api.enviar("POST", "/api/admin/citas/" + id + "/asistencia",
                () -> new JSONObject().put("resultado", hecha ? "hecha" : "no_asistio"),
                Cita::desdeJson, callback);
    }
}
