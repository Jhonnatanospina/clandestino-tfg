package com.clandestino.app.datos.repositorio;

import com.clandestino.app.datos.remoto.Callback;
import com.clandestino.app.datos.remoto.ClienteApi;
import com.clandestino.app.modelo.ExcepcionDia;
import com.clandestino.app.modelo.HorarioSemanal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** El horario de cada día de la semana y los días sueltos que se salen de él. */
public class RepositorioHorarios {

    private final ClienteApi api;

    public RepositorioHorarios(ClienteApi api) {
        this.api = api;
    }

    /** Devuelve siempre los siete días, aunque alguno no tenga fila en la base de datos. */
    public void semanal(Callback<List<HorarioSemanal>> callback) {
        api.enviar("GET", "/api/admin/horarios", null, RepositorioHorarios::leerSemana, callback);
    }

    /** Manda los siete días de golpe; la API lo guarda todo en una sola transacción. */
    public void guardarSemanal(List<HorarioSemanal> horarios, Callback<List<HorarioSemanal>> callback) {
        api.enviar("PUT", "/api/admin/horarios", () -> {
            JSONArray lista = new JSONArray();
            for (HorarioSemanal horario : horarios) {
                lista.put(horario.aJson());
            }
            return new JSONObject().put("horarios", lista);
        }, RepositorioHorarios::leerSemana, callback);
    }

    private static List<HorarioSemanal> leerSemana(JSONObject json) throws JSONException {
        List<HorarioSemanal> guardados =
                ClienteApi.lista("horarios", HorarioSemanal::desdeJson).convertir(json);
        return HorarioSemanal.semanaCompleta(guardados);
    }

    /** Sin "desde", la API devuelve las de hoy en adelante, que son las que interesan en D3. */
    public void excepciones(Callback<List<ExcepcionDia>> callback) {
        api.enviar("GET", "/api/admin/excepciones", null,
                ClienteApi.lista("excepciones", ExcepcionDia::desdeJson), callback);
    }

    /** Cierra los días de "desde" a "hasta", los dos incluidos. */
    public void cerrarDias(LocalDate desde, LocalDate hasta, String nota,
                           Callback<List<ExcepcionDia>> callback) {
        guardarExcepcion(() -> ExcepcionDia.cuerpoCierre(desde, hasta, nota), callback);
    }

    public void abrirDiaExtra(LocalDate fecha, LocalTime inicio, LocalTime fin, int cupo, String nota,
                              Callback<List<ExcepcionDia>> callback) {
        guardarExcepcion(() -> ExcepcionDia.cuerpoDiaExtra(fecha, inicio, fin, cupo, nota), callback);
    }

    private void guardarExcepcion(ClienteApi.Cuerpo cuerpo, Callback<List<ExcepcionDia>> callback) {
        api.enviar("POST", "/api/admin/excepciones", cuerpo,
                ClienteApi.lista("excepciones", ExcepcionDia::desdeJson), callback);
    }

    public void borrarExcepcion(long id, Callback<JSONObject> callback) {
        // Contesta 204 sin cuerpo: ClienteApi lo convierte en un JSONObject vacío.
        api.enviar("DELETE", "/api/admin/excepciones/" + id, null, json -> json, callback);
    }
}
