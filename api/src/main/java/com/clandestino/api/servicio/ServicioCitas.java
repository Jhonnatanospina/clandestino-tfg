package com.clandestino.api.servicio;

import com.clandestino.api.modelo.Cita;
import com.clandestino.api.modelo.EstadoCita;
import com.clandestino.api.modelo.Tramo;
import com.clandestino.api.modelo.Usuario;
import com.clandestino.api.repositorio.BaseDatos;
import com.clandestino.api.repositorio.RepositorioCitas;
import com.clandestino.api.repositorio.RepositorioUsuarios;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las citas: pedirlas, cancelarlas y todo lo que hace el barbero con ellas (confirmar,
 * rechazar, cancelar y marcar si el cliente vino).
 *
 * La parte delicada es pedir(): es la única en la que dos clientes pueden pisarse. Está
 * explicada en ese método.
 */
public class ServicioCitas {

    /** Mis citas enseña lo de los últimos 30 días y todo lo que está por venir. */
    private static final int DIAS_DE_HISTORIAL = 30;

    // Límites de la hora que puede fijar el barbero al confirmar (DP-04).
    private static final LocalTime PRIMERA_HORA_FINAL = LocalTime.of(8, 0);
    private static final LocalTime ULTIMA_HORA_FINAL = LocalTime.of(22, 0);

    // DECIMAL(6,2): como mucho 9999,99.
    private static final BigDecimal MAXIMA_PROPINA = new BigDecimal("9999.99");

    private final BaseDatos baseDatos;
    private final RepositorioCitas citas;
    private final RepositorioUsuarios usuarios;
    private final ServicioHorarios servicioHorarios;
    private final Reloj reloj;

    /**
     * Un objeto "cerrojo" por fecha. Las peticiones del mismo día se ponen en fila sobre él;
     * las de días distintos no se esperan entre sí. ConcurrentHashMap porque varios hilos piden
     * cerrojos a la vez.
     */
    private final Map<LocalDate, Object> cerrojos = new ConcurrentHashMap<>();

    /** Solo existe para el test que enseña qué pasa SIN el synchronized. En la API siempre es true. */
    private final boolean usarCerrojo;

    public ServicioCitas(BaseDatos baseDatos, RepositorioCitas citas, RepositorioUsuarios usuarios,
                         ServicioHorarios servicioHorarios, Reloj reloj) {
        this(baseDatos, citas, usuarios, servicioHorarios, reloj, true);
    }

    ServicioCitas(BaseDatos baseDatos, RepositorioCitas citas, RepositorioUsuarios usuarios,
                  ServicioHorarios servicioHorarios, Reloj reloj, boolean usarCerrojo) {
        this.baseDatos = baseDatos;
        this.citas = citas;
        this.usuarios = usuarios;
        this.servicioHorarios = servicioHorarios;
        this.reloj = reloj;
        this.usarCerrojo = usarCerrojo;
    }

    // ---------------------------------------------------------------------------------------
    // Cliente
    // ---------------------------------------------------------------------------------------

    /**
     * Pide una cita (UC04). Las comprobaciones van en el orden de RN-09 y se para en la primera
     * que falla.
     *
     * El problema de concurrencia: el cupo es "como mucho N citas activas al día". Comprobarlo
     * son dos pasos, contar las citas del día y, si hay sitio, insertar. Si dos clientes piden
     * a la vez el último sitio del día (a horas distintas), los dos pueden contar antes de que
     * el otro inserte, los dos ven sitio y entran los dos: el día acaba con más citas que cupo.
     * Ningún índice de la base de datos puede impedirlo, porque no se trata de una fila repetida.
     *
     * La solución: los pasos de contar e insertar van dentro de un synchronized sobre el cerrojo
     * de esa fecha y en una sola transacción, y el commit se hace antes de salir del synchronized.
     * Así, el segundo cliente no empieza a contar hasta que el primero ha guardado su cita.
     * Lo demuestran los tests de PruebaConcurrenciaCupo.
     *
     * Limitación: el cerrojo vive en la memoria de este proceso. Si hubiera dos copias de la API
     * a la vez no serviría (haría falta un SELECT ... FOR UPDATE o un bloqueo en la base de
     * datos), pero el proyecto tiene una sola.
     */
    public Cita pedir(long clienteId, LocalDate fecha, LocalTime hora) {
        // Paso 6, antes del cerrojo: así el mapa de cerrojos solo puede tener fechas del
        // horizonte (14 como mucho) y no crece con fechas inventadas.
        if (!servicioHorarios.estaEnHorizonte(fecha)) {
            throw servicioHorarios.fueraDeHorizonte();
        }
        olvidarCerrojosViejos();

        if (!usarCerrojo) {
            return reservar(clienteId, fecha, hora);
        }
        synchronized (cerrojos.computeIfAbsent(fecha, f -> new Object())) {
            return reservar(clienteId, fecha, hora);
        }
    }

    /** Pasos 7 a 13 de RN-09, en una sola transacción. */
    private Cita reservar(long clienteId, LocalDate fecha, LocalTime hora) {
        return baseDatos.enTransaccion(conexion -> {
            Tramo tramo = servicioHorarios.tramoDe(conexion, fecha)
                    .orElseThrow(() -> new ErrorNegocio("dia_cerrado", 409, "Ese día no se trabaja."));

            if (!tramo.cabe(hora)) {
                throw new ErrorNegocio("hora_fuera_de_tramo", 400, "Esa hora está fuera del horario de ese día.");
            }
            if (!tramo.estaAlineada(hora)) {
                throw new ErrorNegocio("hora_invalida", 400, "Las citas empiezan cada hora desde la apertura.");
            }
            LocalDateTime ahora = reloj.ahora();
            if (fecha.equals(ahora.toLocalDate()) && !hora.isAfter(ahora.toLocalTime())) {
                throw new ErrorNegocio("hora_pasada", 400, "Esa hora ya ha pasado.");
            }
            // El cupo cuenta las pendientes y las confirmadas de todos los clientes.
            if (citas.contarActivas(conexion, fecha) >= tramo.cupo()) {
                throw new ErrorNegocio("dia_lleno", 409, "Ese día ya está completo.");
            }
            if (citas.horasOcupadas(conexion, fecha).contains(hora)) {
                throw huecoOcupado();
            }

            Cita nueva = new Cita(0, clienteId, null, null, fecha, hora, null,
                    EstadoCita.PENDIENTE, null, null, ahora, null);
            long id = insertarOChocar(conexion, nueva);
            return citas.buscarPorId(conexion, id).orElseThrow();
        });
    }

    /**
     * La red de seguridad: aunque el cerrojo ya evita que dos peticiones del mismo día pasen a
     * la vez, si alguna vez dos citas activas acabaran en la misma fecha y hora, el índice único
     * uq_citas_hueco_activo lo impide y MySQL devuelve el error 1062.
     */
    private long insertarOChocar(Connection conexion, Cita cita) throws SQLException {
        try {
            return citas.insertar(conexion, cita);
        } catch (SQLException e) {
            if (BaseDatos.esDuplicado(e)) {
                throw huecoOcupado();
            }
            throw e;
        }
    }

    /** Las fechas pasadas ya no se pueden pedir, así que sus cerrojos sobran. */
    private void olvidarCerrojosViejos() {
        LocalDate hoy = reloj.hoy();
        cerrojos.keySet().removeIf(fecha -> fecha.isBefore(hoy));
    }

    public List<Cita> misCitas(long clienteId) {
        LocalDate desde = reloj.hoy().minusDays(DIAS_DE_HISTORIAL);
        return baseDatos.consultar(conexion -> citas.listarPorCliente(conexion, clienteId, desde));
    }

    /**
     * El cliente cancela una cita suya (RN-15). Si la cita no existe o es de otro cliente, la
     * respuesta es la misma ("no encontrada"): así no se puede averiguar qué ids existen.
     */
    public Cita cancelarPorCliente(long clienteId, long citaId) {
        return baseDatos.enTransaccion(conexion -> {
            Cita cita = buscar(conexion, citaId);
            if (cita.clienteId() != clienteId) {
                throw ErrorNegocio.noEncontrado("Esa cita no existe.");
            }
            if (!cita.estado().esActiva()) {
                throw ErrorNegocio.estadoInvalido("Esa cita ya no se puede cancelar.");
            }
            comprobarQueNoHaPasado(cita);
            return guardar(conexion, cita, cita.conEstado(EstadoCita.CANCELADA, null));
        });
    }

    // ---------------------------------------------------------------------------------------
    // Barbero
    // ---------------------------------------------------------------------------------------

    public List<Cita> listar(List<EstadoCita> estados, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw ErrorNegocio.datosInvalidos("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        return baseDatos.consultar(conexion -> citas.listar(conexion, estados, desde, hasta));
    }

    /**
     * Acepta una solicitud (RN-12). Sin hora final se queda la que pidió el cliente. No vuelve a
     * mirar el cupo (RN-13): la solicitud ya ocupaba su sitio mientras estaba pendiente.
     */
    public Cita confirmar(long citaId, LocalTime horaFinal) {
        if (horaFinal != null) {
            comprobarHoraFinal(horaFinal);
        }
        return baseDatos.enTransaccion(conexion -> {
            Cita cita = buscar(conexion, citaId);
            if (cita.estado() != EstadoCita.PENDIENTE) {
                throw ErrorNegocio.estadoInvalido("Solo se pueden confirmar las solicitudes pendientes.");
            }
            LocalTime hora = horaFinal != null ? horaFinal : cita.horaPedida();
            if (!LocalDateTime.of(cita.fecha(), hora).isAfter(reloj.ahora())) {
                // DP-06: una solicitud cuya hora ya pasó solo se puede rechazar.
                throw citaPasada();
            }
            Cita confirmada = cita.confirmadaA(hora, reloj.ahora());
            try {
                return guardar(conexion, cita, confirmada);
            } catch (SQLException e) {
                // Si otra cita activa ya está a esa hora, salta el índice único.
                if (BaseDatos.esDuplicado(e)) {
                    throw huecoOcupado();
                }
                throw e;
            }
        });
    }

    /** DP-04: de 08:00 a 22:00, en punto o y media. */
    private static void comprobarHoraFinal(LocalTime hora) {
        boolean enRango = !hora.isBefore(PRIMERA_HORA_FINAL) && !hora.isAfter(ULTIMA_HORA_FINAL);
        boolean enPuntoOYMedia = hora.getSecond() == 0 && (hora.getMinute() == 0 || hora.getMinute() == 30);
        if (!enRango || !enPuntoOYMedia) {
            throw ErrorNegocio.datosInvalidos("hora_final tiene que estar entre 08:00 y 22:00, en punto o y media.");
        }
    }

    public Cita rechazar(long citaId) {
        return baseDatos.enTransaccion(conexion -> {
            Cita cita = buscar(conexion, citaId);
            if (cita.estado() != EstadoCita.PENDIENTE) {
                throw ErrorNegocio.estadoInvalido("Solo se pueden rechazar las solicitudes pendientes.");
            }
            return guardar(conexion, cita, cita.conEstado(EstadoCita.RECHAZADA, reloj.ahora()));
        });
    }

    /** El barbero cancela una cita confirmada que todavía no ha llegado (RN-16). */
    public Cita cancelarPorAdmin(long citaId) {
        return baseDatos.enTransaccion(conexion -> {
            Cita cita = buscar(conexion, citaId);
            if (cita.estado() != EstadoCita.CONFIRMADA) {
                throw ErrorNegocio.estadoInvalido("Solo se pueden cancelar citas confirmadas. Las pendientes se rechazan.");
            }
            comprobarQueNoHaPasado(cita);
            return guardar(conexion, cita, cita.conEstado(EstadoCita.CANCELADA, null));
        });
    }

    /**
     * Cierra una cita confirmada que ya ha pasado (RN-17): vino (HECHA) o no vino (NO_ASISTIO).
     * Con HECHA se copia en la cita el precio que tiene el cliente en ese momento (RN-18): si
     * luego le cambia el precio, las citas ya cerradas no cambian. La propina solo se guarda
     * si vino.
     */
    public Cita marcarAsistencia(long citaId, boolean vino, BigDecimal propina) {
        if (propina != null && (propina.signum() < 0 || propina.compareTo(MAXIMA_PROPINA) > 0)) {
            throw ErrorNegocio.datosInvalidos("La propina tiene que estar entre 0 y " + MAXIMA_PROPINA + ".");
        }
        return baseDatos.enTransaccion(conexion -> {
            Cita cita = buscar(conexion, citaId);
            if (cita.estado() != EstadoCita.CONFIRMADA) {
                throw ErrorNegocio.estadoInvalido("Solo se puede marcar la asistencia de citas confirmadas.");
            }
            if (!cita.yaHaPasado(reloj.ahora())) {
                throw new ErrorNegocio("cita_futura", 409, "La cita todavía no ha llegado.");
            }
            Cita cerrada;
            if (vino) {
                Usuario cliente = usuarios.buscarPorId(conexion, cita.clienteId()).orElseThrow();
                cerrada = cita.cerrada(EstadoCita.HECHA, cliente.precioCorte(), propina);
            } else {
                cerrada = cita.cerrada(EstadoCita.NO_ASISTIO, null, null);
            }
            return guardar(conexion, cita, cerrada);
        });
    }

    // ---------------------------------------------------------------------------------------
    // Piezas comunes
    // ---------------------------------------------------------------------------------------

    private Cita buscar(Connection conexion, long citaId) throws SQLException {
        return citas.buscarPorId(conexion, citaId)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Esa cita no existe."));
    }

    /**
     * Guarda el cambio solo si la cita sigue en el estado en el que la leímos. Si entre medias
     * otra petición la cambió (el cliente cancela justo cuando el barbero confirma), el UPDATE no
     * toca ninguna fila y respondemos "estado_invalido" en vez de pisar el otro cambio.
     */
    private Cita guardar(Connection conexion, Cita antes, Cita despues) throws SQLException {
        if (!citas.actualizar(conexion, despues, antes.estado())) {
            throw ErrorNegocio.estadoInvalido("La cita ha cambiado mientras tanto. Vuelve a cargarla.");
        }
        return citas.buscarPorId(conexion, antes.id()).orElseThrow();
    }

    private void comprobarQueNoHaPasado(Cita cita) {
        if (cita.yaHaPasado(reloj.ahora())) {
            throw citaPasada();
        }
    }

    private static ErrorNegocio citaPasada() {
        return new ErrorNegocio("cita_pasada", 409, "Esa cita ya ha pasado.");
    }

    private static ErrorNegocio huecoOcupado() {
        return new ErrorNegocio("hueco_ocupado", 409, "Esa hora ya está cogida.");
    }
}
