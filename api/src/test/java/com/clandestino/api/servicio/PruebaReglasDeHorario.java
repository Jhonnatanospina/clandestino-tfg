package com.clandestino.api.servicio;

import com.clandestino.api.modelo.EstadoDia;
import com.clandestino.api.modelo.ExcepcionDia;
import com.clandestino.api.modelo.HorarioSemanal;
import com.clandestino.api.modelo.TipoExcepcion;
import com.clandestino.api.modelo.Tramo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RN-01 (qué horario manda cada día) y RN-06 (estado del día), sin base de datos. */
class PruebaReglasDeHorario {

    private static final LocalDate MARTES = LocalDate.of(2026, 10, 6);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 7);

    private static final List<HorarioSemanal> SEMANA = List.of(
            new HorarioSemanal(1, 2, LocalTime.of(16, 0), LocalTime.of(20, 0), 2, true),
            new HorarioSemanal(2, 3, LocalTime.of(16, 0), LocalTime.of(20, 0), 2, false));

    @Test
    void sinExcepcionMandaElHorarioDeEseDiaDeLaSemana() {
        assertEquals(Optional.of(new Tramo(LocalTime.of(16, 0), LocalTime.of(20, 0), 2)),
                ServicioHorarios.resolverTramo(MARTES, null, SEMANA));
    }

    @Test
    void unHorarioDesactivadoEsComoSiNoHubiera() {
        assertTrue(ServicioHorarios.resolverTramo(MIERCOLES, null, SEMANA).isEmpty());
    }

    @Test
    void unaExcepcionCerradoGanaAlHorarioSemanal() {
        ExcepcionDia vacaciones = new ExcepcionDia(1, MARTES, TipoExcepcion.CERRADO, null, null, null, "Vacaciones");
        assertTrue(ServicioHorarios.resolverTramo(MARTES, vacaciones, SEMANA).isEmpty());
    }

    @Test
    void unaExcepcionAbiertoPoneSusHorasYSinCupoUsaDos() {
        ExcepcionDia extra = new ExcepcionDia(1, MIERCOLES, TipoExcepcion.ABIERTO,
                LocalTime.of(10, 0), LocalTime.of(13, 0), null, null);
        assertEquals(Optional.of(new Tramo(LocalTime.of(10, 0), LocalTime.of(13, 0), 2)),
                ServicioHorarios.resolverTramo(MIERCOLES, extra, SEMANA));
    }

    @Test
    void estadoDeCadaDia() {
        Optional<Tramo> tramo = Optional.of(new Tramo(LocalTime.of(16, 0), LocalTime.of(20, 0), 2));
        ExcepcionDia vacaciones = new ExcepcionDia(1, MARTES, TipoExcepcion.CERRADO, null, null, null, null);

        assertEquals(EstadoDia.VACACIONES, ServicioHorarios.estadoDelDia(vacaciones, Optional.empty(), 0));
        assertEquals(EstadoDia.CERRADO, ServicioHorarios.estadoDelDia(null, Optional.empty(), 0));
        assertEquals(EstadoDia.LIBRE, ServicioHorarios.estadoDelDia(null, tramo, 1));
        assertEquals(EstadoDia.COMPLETO, ServicioHorarios.estadoDelDia(null, tramo, 2));
        // RN-07: si el barbero baja el cupo y ya había más citas, el día sigue completo.
        assertEquals(EstadoDia.COMPLETO, ServicioHorarios.estadoDelDia(null, tramo, 3));
    }
}
