package com.clandestino.api.servicio;

import com.clandestino.api.modelo.DiaDisponible;
import com.clandestino.api.modelo.EstadoDia;
import com.clandestino.api.modelo.ExcepcionDia;
import com.clandestino.api.modelo.HorarioSemanal;
import com.clandestino.api.modelo.Hueco;
import com.clandestino.api.modelo.TipoExcepcion;
import com.clandestino.api.modelo.Tramo;
import com.clandestino.api.repositorio.BaseDatos;
import com.clandestino.api.repositorio.RepositorioCitas;
import com.clandestino.api.repositorio.RepositorioHorarios;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Todo lo que tiene que ver con cuándo se trabaja: el horario de la semana, las excepciones
 * (vacaciones, días extra) y, a partir de ahí, qué días y qué horas puede pedir el cliente.
 *
 * Los huecos y el estado de los días no se guardan en ninguna tabla. Se calculan en cada
 * consulta a partir del horario y de las citas, así nunca se quedan desactualizados.
 */
public class ServicioHorarios {

    /** El cliente puede pedir cita de hoy a hoy + 13 días: dos semanas (RN-05). */
    public static final int DIAS_DE_HORIZONTE = 14;

    private static final int MAXIMO_FECHAS_POR_EXCEPCION = 31;
    private static final int MAXIMO_CUPO = 255;            // es un TINYINT UNSIGNED en la tabla
    private static final int MAXIMO_LARGO_NOTA = 80;

    private final BaseDatos baseDatos;
    private final RepositorioHorarios horarios;
    private final RepositorioCitas citas;
    private final Reloj reloj;

    public ServicioHorarios(BaseDatos baseDatos, RepositorioHorarios horarios, RepositorioCitas citas, Reloj reloj) {
        this.baseDatos = baseDatos;
        this.horarios = horarios;
        this.citas = citas;
        this.reloj = reloj;
    }

    /** Respuesta de GET /api/dias: el horizonte entero y los días pedidos con su estado. */
    public record Calendario(LocalDate horizonteDesde, LocalDate horizonteHasta, List<DiaDisponible> dias) {
    }

    /** Respuesta de GET /api/huecos: el estado del día y sus horas. */
    public record HuecosDelDia(LocalDate fecha, EstadoDia estado, List<Hueco> huecos) {
    }

    // ---------------------------------------------------------------------------------------
    // Horizonte
    // ---------------------------------------------------------------------------------------

    public LocalDate ultimoDiaDelHorizonte() {
        return reloj.hoy().plusDays(DIAS_DE_HORIZONTE - 1);
    }

    public boolean estaEnHorizonte(LocalDate fecha) {
        return !fecha.isBefore(reloj.hoy()) && !fecha.isAfter(ultimoDiaDelHorizonte());
    }

    public ErrorNegocio fueraDeHorizonte() {
        return new ErrorNegocio("fecha_fuera_de_horizonte", 400,
                "Solo se puede pedir cita para hoy y los próximos " + (DIAS_DE_HORIZONTE - 1) + " días.");
    }

    // ---------------------------------------------------------------------------------------
    // Disponibilidad (lo que ve el cliente)
    // ---------------------------------------------------------------------------------------

    /**
     * El tramo que se aplica a una fecha, o vacío si ese día no se trabaja (RN-01).
     * Recibe la conexión porque ServicioCitas lo llama dentro de su transacción al pedir cita.
     */
    public Optional<Tramo> tramoDe(Connection conexion, LocalDate fecha) throws SQLException {
        Optional<ExcepcionDia> excepcion = horarios.excepcion(conexion, fecha);
        return resolverTramo(fecha, excepcion.orElse(null), horarios.semanal(conexion));
    }

    /**
     * La regla de precedencia (RN-01), sin base de datos para poder probarla sola:
     * 1) si hay excepción ese día, manda ella (cerrado = sin tramo; abierto = sus horas);
     * 2) si no, el horario semanal de ese día de la semana, si existe y está activo;
     * 3) si tampoco hay, el día está cerrado.
     */
    static Optional<Tramo> resolverTramo(LocalDate fecha, ExcepcionDia excepcion, List<HorarioSemanal> semanal) {
        if (excepcion != null) {
            if (excepcion.tipo() == TipoExcepcion.CERRADO) {
                return Optional.empty();
            }
            return Optional.of(new Tramo(excepcion.horaInicio(), excepcion.horaFin(), excepcion.cupoEfectivo()));
        }
        int diaSemana = fecha.getDayOfWeek().getValue();     // 1 = lunes ... 7 = domingo
        for (HorarioSemanal horario : semanal) {
            if (horario.diaSemana() == diaSemana && horario.activo()) {
                return Optional.of(new Tramo(horario.horaInicio(), horario.horaFin(), horario.cupo()));
            }
        }
        return Optional.empty();
    }

    /** Estado de un día para el cliente, en el orden de RN-06. */
    static EstadoDia estadoDelDia(ExcepcionDia excepcion, Optional<Tramo> tramo, int citasActivas) {
        if (excepcion != null && excepcion.tipo() == TipoExcepcion.CERRADO) {
            return EstadoDia.VACACIONES;
        }
        if (tramo.isEmpty()) {
            return EstadoDia.CERRADO;
        }
        // Mayor o igual, no igual: si el barbero baja el cupo y ya había más citas, sigue completo (RN-07).
        return citasActivas >= tramo.get().cupo() ? EstadoDia.COMPLETO : EstadoDia.LIBRE;
    }

    /**
     * Estado de cada día entre desde y hasta (GET /api/dias). Si no vienen, el horizonte entero;
     * si se salen del horizonte, se recortan sin dar error. Todo con tres consultas, sea cual
     * sea el número de días: horario semanal, excepciones del rango y citas activas por fecha.
     */
    public Calendario diasDelRango(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw ErrorNegocio.datosInvalidos("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        LocalDate primero = reloj.hoy();
        LocalDate ultimo = ultimoDiaDelHorizonte();
        LocalDate inicio = (desde == null || desde.isBefore(primero)) ? primero : desde;
        LocalDate fin = (hasta == null || hasta.isAfter(ultimo)) ? ultimo : hasta;

        List<DiaDisponible> dias = new ArrayList<>();
        if (!inicio.isAfter(fin)) {
            baseDatos.consultar(conexion -> {
                List<HorarioSemanal> semanal = horarios.semanal(conexion);
                List<ExcepcionDia> excepciones = horarios.excepciones(conexion, inicio, fin);
                Map<LocalDate, Integer> activas = citas.contarActivasEntre(conexion, inicio, fin);

                for (LocalDate fecha = inicio; !fecha.isAfter(fin); fecha = fecha.plusDays(1)) {
                    ExcepcionDia excepcion = buscarExcepcion(excepciones, fecha);
                    Optional<Tramo> tramo = resolverTramo(fecha, excepcion, semanal);
                    EstadoDia estado = estadoDelDia(excepcion, tramo, activas.getOrDefault(fecha, 0));
                    dias.add(new DiaDisponible(fecha, estado));
                }
                return null;
            });
        }
        return new Calendario(primero, ultimo, dias);
    }

    /**
     * Las horas de un día y si están ocupadas (GET /api/huecos). Si la fecha es hoy, se quitan
     * las horas que ya han empezado (DP-02). No dice de quién es cada cita.
     */
    public HuecosDelDia huecos(LocalDate fecha) {
        if (!estaEnHorizonte(fecha)) {
            throw fueraDeHorizonte();
        }
        return baseDatos.consultar(conexion -> {
            ExcepcionDia excepcion = horarios.excepcion(conexion, fecha).orElse(null);
            Optional<Tramo> tramo = resolverTramo(fecha, excepcion, horarios.semanal(conexion));
            EstadoDia estado = estadoDelDia(excepcion, tramo, citas.contarActivas(conexion, fecha));

            List<Hueco> huecos = new ArrayList<>();
            if (tramo.isPresent()) {
                Set<LocalTime> ocupadas = citas.horasOcupadas(conexion, fecha);
                boolean esHoy = fecha.equals(reloj.hoy());
                LocalTime horaActual = reloj.ahora().toLocalTime();
                for (LocalTime hora : tramo.get().huecos()) {
                    if (esHoy && !hora.isAfter(horaActual)) {
                        continue;
                    }
                    huecos.add(new Hueco(hora, ocupadas.contains(hora)));
                }
            }
            return new HuecosDelDia(fecha, estado, huecos);
        });
    }

    private static ExcepcionDia buscarExcepcion(List<ExcepcionDia> excepciones, LocalDate fecha) {
        for (ExcepcionDia excepcion : excepciones) {
            if (excepcion.fecha().equals(fecha)) {
                return excepcion;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------
    // Gestión del barbero (D3)
    // ---------------------------------------------------------------------------------------

    public List<HorarioSemanal> horarios() {
        return baseDatos.consultar(horarios::semanal);
    }

    /**
     * Guarda uno o varios días de la semana en una transacción: si uno está mal, no se guarda
     * ninguno. Los días que no vienen en la lista no se tocan. Devuelve la semana completa.
     */
    public List<HorarioSemanal> guardarHorarios(List<HorarioSemanal> nuevos) {
        if (nuevos.isEmpty()) {
            throw ErrorNegocio.datosInvalidos("Falta al menos un horario.");
        }
        Set<Integer> diasVistos = new HashSet<>();
        for (HorarioSemanal horario : nuevos) {
            comprobarHorario(horario);
            if (!diasVistos.add(horario.diaSemana())) {
                throw ErrorNegocio.datosInvalidos("El día " + horario.diaSemana() + " está repetido.");
            }
        }
        return baseDatos.enTransaccion(conexion -> {
            for (HorarioSemanal horario : nuevos) {
                horarios.guardarSemanal(conexion, horario);
            }
            return horarios.semanal(conexion);
        });
    }

    private static void comprobarHorario(HorarioSemanal horario) {
        if (horario.diaSemana() < 1 || horario.diaSemana() > 7) {
            throw ErrorNegocio.datosInvalidos("dia_semana tiene que ir de 1 (lunes) a 7 (domingo).");
        }
        if (!horario.horaInicio().isBefore(horario.horaFin())) {
            throw ErrorNegocio.datosInvalidos("hora_inicio tiene que ser anterior a hora_fin.");
        }
        if (horario.cupo() < 0 || horario.cupo() > MAXIMO_CUPO) {
            throw ErrorNegocio.datosInvalidos("cupo tiene que estar entre 0 y " + MAXIMO_CUPO + ".");
        }
    }

    /** Excepciones de una fecha en adelante (por defecto, desde hoy). */
    public List<ExcepcionDia> excepciones(LocalDate desde) {
        LocalDate inicio = desde != null ? desde : reloj.hoy();
        return baseDatos.consultar(conexion -> horarios.excepciones(conexion, inicio, null));
    }

    /**
     * Crea la misma excepción para varias fechas (unas vacaciones de una semana, por ejemplo).
     * Si una fecha ya tenía excepción, la nueva la sustituye. No toca las citas que ya hubiera
     * esos días (RN-08): el barbero las gestiona desde la agenda.
     */
    public List<ExcepcionDia> crearExcepciones(List<LocalDate> fechas, ExcepcionDia plantilla) {
        comprobarFechas(fechas);
        comprobarExcepcion(plantilla);
        return baseDatos.enTransaccion(conexion -> {
            List<ExcepcionDia> creadas = new ArrayList<>();
            for (LocalDate fecha : fechas) {
                horarios.guardarExcepcion(conexion, plantilla.paraFecha(fecha));
                creadas.add(horarios.excepcion(conexion, fecha).orElseThrow());
            }
            return creadas;
        });
    }

    private void comprobarFechas(List<LocalDate> fechas) {
        if (fechas.isEmpty() || fechas.size() > MAXIMO_FECHAS_POR_EXCEPCION) {
            throw ErrorNegocio.datosInvalidos("Hay que mandar entre 1 y " + MAXIMO_FECHAS_POR_EXCEPCION + " fechas.");
        }
        if (new HashSet<>(fechas).size() != fechas.size()) {
            throw ErrorNegocio.datosInvalidos("Hay fechas repetidas.");
        }
        for (LocalDate fecha : fechas) {
            if (fecha.isBefore(reloj.hoy())) {
                throw ErrorNegocio.datosInvalidos("La fecha " + fecha + " ya ha pasado.");
            }
        }
    }

    private static void comprobarExcepcion(ExcepcionDia excepcion) {
        if (excepcion.nota() != null && excepcion.nota().length() > MAXIMO_LARGO_NOTA) {
            throw ErrorNegocio.datosInvalidos("La nota no puede pasar de " + MAXIMO_LARGO_NOTA + " caracteres.");
        }
        boolean llevaHorasOCupo = excepcion.horaInicio() != null || excepcion.horaFin() != null
                || excepcion.cupo() != null;
        if (excepcion.tipo() == TipoExcepcion.CERRADO) {
            if (llevaHorasOCupo) {
                throw ErrorNegocio.datosInvalidos("Un día cerrado no lleva horas ni cupo.");
            }
            return;
        }
        if (excepcion.horaInicio() == null || excepcion.horaFin() == null) {
            throw ErrorNegocio.datosInvalidos("Un día abierto necesita hora_inicio y hora_fin.");
        }
        if (!excepcion.horaInicio().isBefore(excepcion.horaFin())) {
            throw ErrorNegocio.datosInvalidos("hora_inicio tiene que ser anterior a hora_fin.");
        }
        if (excepcion.cupo() != null && (excepcion.cupo() < 0 || excepcion.cupo() > MAXIMO_CUPO)) {
            throw ErrorNegocio.datosInvalidos("cupo tiene que estar entre 0 y " + MAXIMO_CUPO + ".");
        }
    }

    public void borrarExcepcion(int id) {
        boolean borrada = baseDatos.consultar(conexion -> horarios.borrarExcepcion(conexion, id));
        if (!borrada) {
            throw ErrorNegocio.noEncontrado("Esa excepción no existe.");
        }
    }
}
