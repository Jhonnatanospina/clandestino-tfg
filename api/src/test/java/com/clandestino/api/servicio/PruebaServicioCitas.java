package com.clandestino.api.servicio;

import com.clandestino.api.BaseDePruebas;
import com.clandestino.api.RelojMovible;
import com.clandestino.api.modelo.Cita;
import com.clandestino.api.modelo.EstadoCita;
import com.clandestino.api.modelo.ExcepcionDia;
import com.clandestino.api.modelo.Hueco;
import com.clandestino.api.modelo.TipoExcepcion;
import com.clandestino.api.repositorio.BaseDatos;
import com.clandestino.api.repositorio.RepositorioCitasJdbc;
import com.clandestino.api.repositorio.RepositorioHorariosJdbc;
import com.clandestino.api.repositorio.RepositorioUsuariosJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pedir, confirmar, cancelar y cerrar citas contra MySQL de verdad.
 *
 * El "hoy" de estos tests es el lunes 5 de octubre de 2026 a las 10:00. Hay horario el lunes
 * de 09:00 a 14:00 y el martes de 16:00 a 20:00, los dos con cupo 2. El miércoles no se trabaja.
 */
class PruebaServicioCitas {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate MARTES = LocalDate.of(2026, 10, 6);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 7);

    private BaseDatos baseDatos;
    private RelojMovible reloj;
    private ServicioCitas servicioCitas;
    private ServicioHorarios servicioHorarios;
    private long marcos;
    private long lucia;
    private long pablo;

    @BeforeEach
    void prepararBase() {
        baseDatos = BaseDePruebas.conectar();
        BaseDePruebas.vaciar(baseDatos);
        BaseDePruebas.crearHorario(baseDatos, 1, LocalTime.of(9, 0), LocalTime.of(14, 0), 2);
        BaseDePruebas.crearHorario(baseDatos, 2, LocalTime.of(16, 0), LocalTime.of(20, 0), 2);
        marcos = BaseDePruebas.crearCliente(baseDatos, "marcos", "+34611000001", "aprobado");
        lucia = BaseDePruebas.crearCliente(baseDatos, "lucia", "+34611000002", "aprobado");
        pablo = BaseDePruebas.crearCliente(baseDatos, "pablo", "+34611000003", "aprobado");

        reloj = new RelojMovible(LocalDateTime.of(2026, 10, 5, 10, 0));
        RepositorioCitasJdbc citas = new RepositorioCitasJdbc();
        servicioHorarios = new ServicioHorarios(baseDatos, new RepositorioHorariosJdbc(), citas, reloj.comoReloj());
        servicioCitas = new ServicioCitas(baseDatos, citas, new RepositorioUsuariosJdbc(), servicioHorarios,
                reloj.comoReloj());
    }

    // ---------------------------------------------------------------------------------------
    // Pedir (RN-09, RN-10)
    // ---------------------------------------------------------------------------------------

    @Test
    void unaCitaNaceComoPendienteYConLaHoraPedida() {
        Cita cita = servicioCitas.pedir(marcos, MARTES, LocalTime.of(17, 0));

        assertEquals(EstadoCita.PENDIENTE, cita.estado());
        assertEquals(LocalTime.of(17, 0), cita.horaPedida());
        assertNull(cita.horaFinal());
        assertNull(cita.precio());
        assertNull(cita.decididaEn());
        assertEquals(LocalDateTime.of(2026, 10, 5, 10, 0), cita.creadaEn());
        assertEquals("marcos", cita.clienteUsuario());
    }

    @Test
    void fueraDelHorizonteDeDosSemanasNoSePuede() {
        falla("fecha_fuera_de_horizonte", () -> servicioCitas.pedir(marcos, LUNES.minusDays(1), LocalTime.of(10, 0)));
        falla("fecha_fuera_de_horizonte", () -> servicioCitas.pedir(marcos, LUNES.plusDays(14), LocalTime.of(10, 0)));
        // El último día del horizonte (hoy + 13) sí vale... si se trabaja. El 18 es domingo: cerrado.
        falla("dia_cerrado", () -> servicioCitas.pedir(marcos, LUNES.plusDays(13), LocalTime.of(10, 0)));
    }

    @Test
    void unDiaSinHorarioOConVacacionesEstaCerrado() {
        falla("dia_cerrado", () -> servicioCitas.pedir(marcos, MIERCOLES, LocalTime.of(17, 0)));

        servicioHorarios.crearExcepciones(List.of(MARTES),
                new ExcepcionDia(null, null, TipoExcepcion.CERRADO, null, null, null, "Vacaciones"));
        falla("dia_cerrado", () -> servicioCitas.pedir(marcos, MARTES, LocalTime.of(17, 0)));
    }

    @Test
    void laHoraTieneQueCaberEnElTramoYEstarEnLaRejilla() {
        falla("hora_fuera_de_tramo", () -> servicioCitas.pedir(marcos, MARTES, LocalTime.of(15, 0)));
        // A las 19:30 la cita acabaría a las 20:30, después del cierre.
        falla("hora_fuera_de_tramo", () -> servicioCitas.pedir(marcos, MARTES, LocalTime.of(19, 30)));
        falla("hora_invalida", () -> servicioCitas.pedir(marcos, MARTES, LocalTime.of(16, 30)));
    }

    @Test
    void hoyNoSePuedePedirUnaHoraQueYaHaLlegado() {
        falla("hora_pasada", () -> servicioCitas.pedir(marcos, LUNES, LocalTime.of(9, 0)));
        falla("hora_pasada", () -> servicioCitas.pedir(marcos, LUNES, LocalTime.of(10, 0)));
        assertEquals(EstadoCita.PENDIENTE, servicioCitas.pedir(marcos, LUNES, LocalTime.of(11, 0)).estado());
    }

    @Test
    void conElCupoLlenoNoSePuedeNiAOtraHora() {
        servicioCitas.pedir(marcos, MARTES, LocalTime.of(16, 0));
        servicioCitas.pedir(lucia, MARTES, LocalTime.of(17, 0));
        falla("dia_lleno", () -> servicioCitas.pedir(pablo, MARTES, LocalTime.of(18, 0)));
    }

    @Test
    void diaLlenoSeCompruebaAntesQueHuecoOcupado() {
        servicioCitas.pedir(marcos, MARTES, LocalTime.of(16, 0));
        servicioCitas.pedir(lucia, MARTES, LocalTime.of(17, 0));
        // Las 17:00 están cogidas, pero lo primero que falla es el cupo (RN-09).
        falla("dia_lleno", () -> servicioCitas.pedir(pablo, MARTES, LocalTime.of(17, 0)));
    }

    @Test
    void dosCitasActivasNoPuedenCompartirHora() {
        servicioCitas.pedir(marcos, MARTES, LocalTime.of(17, 0));
        falla("hueco_ocupado", () -> servicioCitas.pedir(lucia, MARTES, LocalTime.of(17, 0)));
    }

    @Test
    void unMismoClientePuedeTenerDosCitasElMismoDia() {
        servicioCitas.pedir(marcos, MARTES, LocalTime.of(16, 0));
        assertEquals(EstadoCita.PENDIENTE, servicioCitas.pedir(marcos, MARTES, LocalTime.of(18, 0)).estado());
    }

    // ---------------------------------------------------------------------------------------
    // Cancelar (RN-15, RN-16)
    // ---------------------------------------------------------------------------------------

    @Test
    void alCancelarSeLiberanElHuecoYElCupo() {
        Cita cita = servicioCitas.pedir(marcos, MARTES, LocalTime.of(17, 0));
        servicioCitas.pedir(lucia, MARTES, LocalTime.of(18, 0));

        assertEquals(EstadoCita.CANCELADA, servicioCitas.cancelarPorCliente(marcos, cita.id()).estado());

        // El índice único ignora las canceladas: la misma hora se puede volver a pedir.
        assertEquals(EstadoCita.PENDIENTE, servicioCitas.pedir(pablo, MARTES, LocalTime.of(17, 0)).estado());
    }

    @Test
    void unClienteNoPuedeCancelarLaCitaDeOtro() {
        Cita cita = servicioCitas.pedir(marcos, MARTES, LocalTime.of(17, 0));
        falla("no_encontrado", () -> servicioCitas.cancelarPorCliente(lucia, cita.id()));
        falla("no_encontrado", () -> servicioCitas.cancelarPorCliente(marcos, 999_999));
    }

    @Test
    void noSePuedeCancelarDosVecesNiUnaCitaPasada() {
        Cita cita = servicioCitas.pedir(marcos, MARTES, LocalTime.of(17, 0));
        servicioCitas.cancelarPorCliente(marcos, cita.id());
        falla("estado_invalido", () -> servicioCitas.cancelarPorCliente(marcos, cita.id()));

        Cita otra = servicioCitas.pedir(marcos, MARTES, LocalTime.of(18, 0));
        reloj.ponerEn(LocalDateTime.of(2026, 10, 6, 18, 0));
        falla("cita_pasada", () -> servicioCitas.cancelarPorCliente(marcos, otra.id()));
    }

    @Test
    void elBarberoSoloCancelaConfirmadas() {
        Cita cita = servicioCitas.pedir(marcos, MARTES, LocalTime.of(17, 0));
        falla("estado_invalido", () -> servicioCitas.cancelarPorAdmin(cita.id()));

        servicioCitas.confirmar(cita.id(), null);
        assertEquals(EstadoCita.CANCELADA, servicioCitas.cancelarPorAdmin(cita.id()).estado());
    }

    // ---------------------------------------------------------------------------------------
    // Confirmar y rechazar (RN-12, RN-13)
    // ---------------------------------------------------------------------------------------

    @Test
    void confirmarSinHoraFinalDejaLaPedida() {
        Cita cita = servicioCitas.pedir(marcos, MARTES, LocalTime.of(17, 0));
        Cita confirmada = servicioCitas.confirmar(cita.id(), null);

        assertEquals(EstadoCita.CONFIRMADA, confirmada.estado());
        assertEquals(LocalTime.of(17, 0), confirmada.horaFinal());
        assertEquals(LocalDateTime.of(2026, 10, 5, 10, 0), confirmada.decididaEn());
    }

    @Test
    void confirmarAOtraHoraLiberaLaPedida() {
        Cita cita = servicioCitas.pedir(marcos, MARTES, LocalTime.of(17, 0));
        servicioCitas.confirmar(cita.id(), LocalTime.of(18, 30));

        List<Hueco> huecos = servicioHorarios.huecos(MARTES).huecos();
        assertTrue(huecos.contains(new Hueco(LocalTime.of(17, 0), false)), huecos.toString());
    }

    @Test
    void laHoraFinalTieneQueSerEnPuntoOYMediaEntre8y22() {
        Cita cita = servicioCitas.pedir(marcos, MARTES, LocalTime.of(17, 0));
        falla("datos_invalidos", () -> servicioCitas.confirmar(cita.id(), LocalTime.of(17, 15)));
        falla("datos_invalidos", () -> servicioCitas.confirmar(cita.id(), LocalTime.of(7, 30)));
        falla("datos_invalidos", () -> servicioCitas.confirmar(cita.id(), LocalTime.of(22, 30)));
        // Fuera del tramo pero dentro de 08:00-22:00 sí vale (DP-04).
        assertEquals(LocalTime.of(21, 0), servicioCitas.confirmar(cita.id(), LocalTime.of(21, 0)).horaFinal());
    }

    @Test
    void noSePuedeConfirmarAUnaHoraQueYaTieneOtraCitaActiva() {
        servicioCitas.pedir(marcos, MARTES, LocalTime.of(16, 0));
        Cita otra = servicioCitas.pedir(lucia, MARTES, LocalTime.of(17, 0));
        falla("hueco_ocupado", () -> servicioCitas.confirmar(otra.id(), LocalTime.of(16, 0)));
        // Al fallar no se ha guardado nada: sigue pendiente a su hora.
        assertEquals(EstadoCita.PENDIENTE, servicioCitas.listar(List.of(EstadoCita.PENDIENTE), MARTES, MARTES)
                .stream().filter(c -> c.id() == otra.id()).findFirst().orElseThrow().estado());
    }

    @Test
    void soloSeConfirmanORechazanPendientes() {
        Cita cita = servicioCitas.pedir(marcos, MARTES, LocalTime.of(17, 0));
        assertEquals(EstadoCita.RECHAZADA, servicioCitas.rechazar(cita.id()).estado());
        falla("estado_invalido", () -> servicioCitas.confirmar(cita.id(), null));
        falla("estado_invalido", () -> servicioCitas.rechazar(cita.id()));
    }

    @Test
    void unaSolicitudQueYaPasoSoloSePuedeRechazar() {
        Cita cita = servicioCitas.pedir(marcos, MARTES, LocalTime.of(17, 0));
        reloj.ponerEn(LocalDateTime.of(2026, 10, 6, 17, 0));
        falla("cita_pasada", () -> servicioCitas.confirmar(cita.id(), null));
        assertEquals(EstadoCita.RECHAZADA, servicioCitas.rechazar(cita.id()).estado());
    }

    // ---------------------------------------------------------------------------------------
    // Asistencia (RN-17, RN-18)
    // ---------------------------------------------------------------------------------------

    @Test
    void siVinoSeGuardanElPrecioDelClienteYLaPropina() {
        Cita cita = servicioCitas.pedir(marcos, MARTES, LocalTime.of(17, 0));
        servicioCitas.confirmar(cita.id(), null);

        falla("cita_futura", () -> servicioCitas.marcarAsistencia(cita.id(), true, null));

        reloj.ponerEn(LocalDateTime.of(2026, 10, 6, 19, 0));
        Cita hecha = servicioCitas.marcarAsistencia(cita.id(), true, new BigDecimal("2.50"));
        assertEquals(EstadoCita.HECHA, hecha.estado());
        assertEquals(0, new BigDecimal("15.00").compareTo(hecha.precio()));
        assertEquals(0, new BigDecimal("2.50").compareTo(hecha.propina()));

        falla("estado_invalido", () -> servicioCitas.marcarAsistencia(cita.id(), false, null));
    }

    @Test
    void siNoVinoNoHayPrecioNiPropina() {
        Cita cita = servicioCitas.pedir(marcos, MARTES, LocalTime.of(17, 0));
        servicioCitas.confirmar(cita.id(), null);
        reloj.ponerEn(LocalDateTime.of(2026, 10, 6, 19, 0));

        Cita noVino = servicioCitas.marcarAsistencia(cita.id(), false, new BigDecimal("5"));
        assertEquals(EstadoCita.NO_ASISTIO, noVino.estado());
        assertNull(noVino.precio());
        assertNull(noVino.propina());
    }

    @Test
    void unaPropinaNegativaSeRechaza() {
        falla("datos_invalidos", () -> servicioCitas.marcarAsistencia(1, true, new BigDecimal("-1")));
    }

    // ---------------------------------------------------------------------------------------
    // Lo que ve el cliente
    // ---------------------------------------------------------------------------------------

    @Test
    void misCitasSoloDevuelveLasDelCliente() {
        servicioCitas.pedir(marcos, MARTES, LocalTime.of(16, 0));
        servicioCitas.pedir(lucia, MARTES, LocalTime.of(17, 0));

        List<Cita> deMarcos = servicioCitas.misCitas(marcos);
        assertEquals(1, deMarcos.size());
        assertEquals(marcos, deMarcos.get(0).clienteId());
    }

    @Test
    void losHuecosDeHoyNoEnsenanLasHorasQueYaHanEmpezado() {
        List<Hueco> huecos = servicioHorarios.huecos(LUNES).huecos();
        // Son las 10:00: quedan 11, 12 y 13.
        assertEquals(List.of(LocalTime.of(11, 0), LocalTime.of(12, 0), LocalTime.of(13, 0)),
                huecos.stream().map(Hueco::hora).toList());
        assertFalse(huecos.stream().anyMatch(Hueco::ocupado));
    }

    private static void falla(String codigoEsperado, Executable accion) {
        ErrorNegocio error = assertThrows(ErrorNegocio.class, accion);
        assertEquals(codigoEsperado, error.getCodigo(), error.getMessage());
    }
}
