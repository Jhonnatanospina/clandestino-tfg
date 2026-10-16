package com.clandestino.app.modelo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

/** Lectura del JSON de la API y las reglas que llevan los propios modelos. */
public class PruebaModelos {

    @Test
    public void citaDelClienteSinDatosDeCliente() throws Exception {
        Cita cita = Cita.desdeJson(new JSONObject("{\"id\":7,\"fecha\":\"2027-02-02\","
                + "\"hora_pedida\":\"16:00\",\"hora_final\":null,\"estado\":\"pendiente\"}"));
        assertEquals(LocalDate.of(2027, 2, 2), cita.getFecha());
        assertNull(cita.getHoraFinal());
        assertEquals(LocalTime.of(16, 0), cita.horaEfectiva());
        assertNull(cita.getClienteUsuario());
    }

    @Test
    public void citaDelBarberoConClienteYHoraFinal() throws Exception {
        Cita cita = Cita.desdeJson(new JSONObject("{\"id\":8,\"fecha\":\"2027-02-02\","
                + "\"hora_pedida\":\"16:00\",\"hora_final\":\"16:30\",\"estado\":\"no_asistio\","
                + "\"cliente\":{\"usuario\":\"diego\",\"telefono\":\"+34611222333\"}}"));
        assertEquals(EstadoCita.NO_ASISTIO, cita.getEstado());
        assertEquals(LocalTime.of(16, 30), cita.horaEfectiva());
        assertEquals("diego", cita.getClienteUsuario());
    }

    @Test
    public void unaCitaPasaCuandoLlegaSuHoraDeInicio() {
        Cita cita = new Cita(1, LocalDate.of(2027, 2, 2), LocalTime.of(16, 0), null,
                EstadoCita.CONFIRMADA, null, null);
        assertFalse(cita.yaHaPasado(LocalDateTime.of(2027, 2, 2, 15, 59)));
        assertTrue(cita.yaHaPasado(LocalDateTime.of(2027, 2, 2, 16, 0)));
    }

    @Test
    public void soloPendienteYConfirmadaOcupanHueco() {
        assertTrue(EstadoCita.PENDIENTE.ocupaHueco());
        assertTrue(EstadoCita.CONFIRMADA.ocupaHueco());
        assertFalse(EstadoCita.CANCELADA.ocupaHueco());
        assertFalse(EstadoCita.HECHA.ocupaHueco());
    }

    @Test
    public void laNotaNullNoSeLeeComoTexto() throws Exception {
        Invitacion invitacion = Invitacion.desdeJson(new JSONObject("{\"id\":1,"
                + "\"codigo\":\"H8TQ5C3ZN6VA\",\"nota\":null,\"estado\":\"pendiente\","
                + "\"caducada\":true,\"caduca_en\":\"2027-01-24T10:00:00\",\"usada_por\":null}"));
        assertNull(invitacion.getNota());
        assertEquals("Caducada", invitacion.etiqueta());
    }

    @Test
    public void invitacionUsadaGanaACaducada() throws Exception {
        Invitacion invitacion = Invitacion.desdeJson(new JSONObject("{\"id\":2,"
                + "\"codigo\":\"K7M4X9PQR2WD\",\"nota\":\"Diego\",\"estado\":\"usada\","
                + "\"caducada\":true,\"caduca_en\":\"2027-01-24T10:00:00\","
                + "\"usada_por\":{\"usuario\":\"diego\"}}"));
        assertEquals("Usada", invitacion.etiqueta());
        assertEquals("diego", invitacion.getUsadaPor());
    }

    @Test
    public void laListaDeClientesNoTraeRol() throws Exception {
        Usuario usuario = Usuario.desdeJson(new JSONObject("{\"id\":3,\"usuario\":\"marta\","
                + "\"telefono\":\"+34600111222\",\"estado\":\"aprobado\"}"));
        assertFalse(usuario.esAdmin());
        assertTrue(usuario.estaAprobado());
    }

    @Test
    public void semanaCompletaRellenaLosDiasQueFaltanApagados() {
        HorarioSemanal miercoles = new HorarioSemanal(3, LocalTime.of(16, 0), LocalTime.of(22, 0),
                2, true);
        List<HorarioSemanal> semana = HorarioSemanal.semanaCompleta(
                Collections.singletonList(miercoles));
        assertEquals(7, semana.size());
        assertTrue(semana.get(2).estaActivo());
        assertFalse(semana.get(0).estaActivo());
        assertEquals(7, semana.get(6).getDiaSemana());
    }

    @Test
    public void tramoAlRevesNoEsValido() {
        HorarioSemanal lunes = new HorarioSemanal(1, LocalTime.of(14, 0), LocalTime.of(20, 0),
                2, true);
        assertTrue(lunes.tramoValido());
        assertFalse(lunes.conFin(LocalTime.of(14, 0)).tramoValido());
    }
}
