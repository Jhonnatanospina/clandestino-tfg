package com.clandestino.api.http;

import com.clandestino.api.modelo.Cita;
import com.clandestino.api.modelo.DiaDisponible;
import com.clandestino.api.modelo.Hueco;
import com.clandestino.api.modelo.Usuario;
import com.clandestino.api.servicio.ErrorNegocio;
import com.clandestino.api.servicio.ServicioCitas;
import com.clandestino.api.servicio.ServicioHorarios;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Rutas del cliente aprobado: ver días y horas libres (UC03), pedir cita (UC04) y ver o
 * cancelar sus citas (UC05). El Enrutador ya ha comprobado que es un cliente aprobado.
 */
public class RutasCliente {

    private final ServicioHorarios servicioHorarios;
    private final ServicioCitas servicioCitas;

    public RutasCliente(ServicioHorarios servicioHorarios, ServicioCitas servicioCitas) {
        this.servicioHorarios = servicioHorarios;
        this.servicioCitas = servicioCitas;
    }

    public void registrar(Enrutador enrutador) {
        enrutador.registrar("GET", "/api/dias", Acceso.CLIENTE, this::dias);
        enrutador.registrar("GET", "/api/huecos", Acceso.CLIENTE, this::huecos);
        enrutador.registrar("POST", "/api/citas", Acceso.CLIENTE, this::pedirCita);
        enrutador.registrar("GET", "/api/mis-citas", Acceso.CLIENTE, this::misCitas);
        enrutador.registrar("POST", "/api/citas/{id}/cancelar", Acceso.CLIENTE, this::cancelar);
    }

    /** GET /api/dias?desde&hasta: el estado de cada día del horizonte. */
    private void dias(HttpExchange intercambio, Usuario usuario) throws IOException {
        LocalDate desde = fechaOpcional(intercambio, "desde");
        LocalDate hasta = fechaOpcional(intercambio, "hasta");
        ServicioHorarios.Calendario calendario = servicioHorarios.diasDelRango(desde, hasta);

        JSONArray dias = new JSONArray();
        for (DiaDisponible dia : calendario.dias()) {
            dias.put(new JSONObject()
                    .put("fecha", ConversorJson.fecha(dia.fecha()))
                    .put("estado", dia.estado().enJson()));
        }
        JSONObject horizonte = new JSONObject()
                .put("desde", ConversorJson.fecha(calendario.horizonteDesde()))
                .put("hasta", ConversorJson.fecha(calendario.horizonteHasta()));
        ConversorJson.responder(intercambio, 200, new JSONObject().put("horizonte", horizonte).put("dias", dias));
    }

    /** GET /api/huecos?fecha: las horas de un día y si están cogidas. */
    private void huecos(HttpExchange intercambio, Usuario usuario) throws IOException {
        String texto = Enrutador.parametro(intercambio, "fecha");
        if (texto == null) {
            throw ErrorNegocio.datosInvalidos("Falta el parámetro 'fecha'.");
        }
        ServicioHorarios.HuecosDelDia dia = servicioHorarios.huecos(ConversorJson.fechaDeTexto(texto, "fecha"));

        JSONArray huecos = new JSONArray();
        for (Hueco hueco : dia.huecos()) {
            huecos.put(new JSONObject()
                    .put("hora", ConversorJson.hora(hueco.hora()))
                    .put("ocupado", hueco.ocupado()));
        }
        ConversorJson.responder(intercambio, 200, new JSONObject()
                .put("fecha", ConversorJson.fecha(dia.fecha()))
                .put("estado", dia.estado().enJson())
                .put("huecos", huecos));
    }

    /** POST /api/citas: { "fecha": "2026-10-06", "hora_pedida": "17:00" }. La cita nace pendiente. */
    private void pedirCita(HttpExchange intercambio, Usuario usuario) throws IOException {
        JSONObject cuerpo = ConversorJson.leerCuerpo(intercambio);
        LocalDate fecha = ConversorJson.fecha(cuerpo, "fecha");
        LocalTime hora = ConversorJson.hora(cuerpo, "hora_pedida");

        Cita cita = servicioCitas.pedir(usuario.id(), fecha, hora);
        ConversorJson.responder(intercambio, 201, ConversorJson.citaParaCliente(cita));
    }

    /** GET /api/mis-citas: las del último mes y las que están por venir. */
    private void misCitas(HttpExchange intercambio, Usuario usuario) throws IOException {
        JSONArray citas = new JSONArray();
        for (Cita cita : servicioCitas.misCitas(usuario.id())) {
            citas.put(ConversorJson.citaParaCliente(cita));
        }
        ConversorJson.responder(intercambio, 200, new JSONObject().put("citas", citas));
    }

    /** POST /api/citas/{id}/cancelar, sin cuerpo. */
    private void cancelar(HttpExchange intercambio, Usuario usuario) throws IOException {
        long citaId = Enrutador.idDeRuta(intercambio);
        Cita cita = servicioCitas.cancelarPorCliente(usuario.id(), citaId);
        ConversorJson.responder(intercambio, 200, ConversorJson.citaParaCliente(cita));
    }

    static LocalDate fechaOpcional(HttpExchange intercambio, String nombre) {
        String texto = Enrutador.parametro(intercambio, nombre);
        return texto == null ? null : ConversorJson.fechaDeTexto(texto, nombre);
    }
}
