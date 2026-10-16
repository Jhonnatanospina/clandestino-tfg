package com.clandestino.api.repositorio;

import com.clandestino.api.modelo.Cita;
import com.clandestino.api.modelo.EstadoCita;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Acceso a la tabla citas.
 *
 * Es una interfaz, aunque solo tenga una implementación de verdad (RepositorioCitasJdbc), por
 * los tests de concurrencia: allí se envuelve en una versión "lenta" que espera un poco entre
 * contar e insertar, para provocar la carrera del cupo siempre y no solo de vez en cuando.
 */
public interface RepositorioCitas {

    /** Inserta la cita y devuelve su id. Si el hueco ya está ocupado, MySQL lanza el error 1062. */
    long insertar(Connection conexion, Cita cita) throws SQLException;

    Optional<Cita> buscarPorId(Connection conexion, long id) throws SQLException;

    /** Citas activas (pendientes y confirmadas) de una fecha: es lo que se compara con el cupo. */
    int contarActivas(Connection conexion, LocalDate fecha) throws SQLException;

    /** Lo mismo para un rango de fechas de una sola consulta. Las fechas sin citas no aparecen. */
    Map<LocalDate, Integer> contarActivasEntre(Connection conexion, LocalDate desde, LocalDate hasta)
            throws SQLException;

    /** Horas efectivas de las citas activas de una fecha. */
    Set<LocalTime> horasOcupadas(Connection conexion, LocalDate fecha) throws SQLException;

    /** Citas de un cliente desde una fecha, de la más reciente a la más antigua. */
    List<Cita> listarPorCliente(Connection conexion, long clienteId, LocalDate desde) throws SQLException;

    /** Citas de todos los clientes. Cualquier filtro puede ser null (o la lista vacía) para no filtrar. */
    List<Cita> listar(Connection conexion, List<EstadoCita> estados, LocalDate desde, LocalDate hasta)
            throws SQLException;

    /**
     * Guarda estado, hora final, precio, propina y decidida_en de la cita, pero solo si en la
     * base de datos sigue en estadoAnterior. Devuelve false si no tocó ninguna fila: otra
     * petición cambió la cita entre que la leímos y la guardamos.
     */
    boolean actualizar(Connection conexion, Cita cita, EstadoCita estadoAnterior) throws SQLException;
}
