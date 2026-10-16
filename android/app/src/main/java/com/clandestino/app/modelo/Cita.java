package com.clandestino.app.modelo;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Una cita. Sirve para las dos vistas de la API: la del cliente (GET /api/mis-citas), que no
 * trae datos del cliente porque es él mismo, y la del barbero (GET /api/admin/citas), que trae
 * un objeto "cliente" con su @usuario y su teléfono. En la primera, esos dos campos quedan a null.
 */
public class Cita {

    private final long id;
    private final LocalDate fecha;
    private final LocalTime horaPedida;
    private final LocalTime horaFinal;
    private final EstadoCita estado;
    private final String clienteUsuario;
    private final String clienteTelefono;

    public Cita(long id, LocalDate fecha, LocalTime horaPedida, LocalTime horaFinal, EstadoCita estado,
                String clienteUsuario, String clienteTelefono) {
        this.id = id;
        this.fecha = fecha;
        this.horaPedida = horaPedida;
        this.horaFinal = horaFinal;
        this.estado = estado;
        this.clienteUsuario = clienteUsuario;
        this.clienteTelefono = clienteTelefono;
    }

    public static Cita desdeJson(JSONObject json) throws JSONException {
        JSONObject cliente = json.optJSONObject("cliente");
        return new Cita(
                json.getLong("id"),
                LecturaJson.fecha(json, "fecha"),
                LecturaJson.hora(json, "hora_pedida"),
                LecturaJson.horaONula(json, "hora_final"),
                EstadoCita.desdeApi(json.getString("estado")),
                cliente == null ? null : cliente.getString("usuario"),
                cliente == null ? null : cliente.getString("telefono"));
    }

    /** La hora a la que de verdad es la cita: la que fijó el barbero o, si no, la pedida. */
    public LocalTime horaEfectiva() {
        return horaFinal != null ? horaFinal : horaPedida;
    }

    /**
     * Una cita ha pasado cuando llega su hora de inicio (no la de fin). Es la misma regla que usa
     * la API: desde ese momento ya no se cancela y se puede marcar la asistencia.
     */
    public boolean yaHaPasado(LocalDateTime ahora) {
        return !ahora.isBefore(fecha.atTime(horaEfectiva()));
    }

    public long getId() {
        return id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public LocalTime getHoraPedida() {
        return horaPedida;
    }

    public LocalTime getHoraFinal() {
        return horaFinal;
    }

    public EstadoCita getEstado() {
        return estado;
    }

    public String getClienteUsuario() {
        return clienteUsuario;
    }

    public String getClienteTelefono() {
        return clienteTelefono;
    }
}
