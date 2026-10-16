package com.clandestino.api.repositorio;

import com.clandestino.api.modelo.Cita;
import com.clandestino.api.modelo.EstadoCita;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * RepositorioCitas con JDBC y MySQL.
 *
 * Las consultas de lectura hacen JOIN con usuarios para traer el @usuario y el teléfono del
 * cliente. Es un JOIN por clave primaria, así que apenas cuesta, y nos ahorra una consulta por
 * cada cita cuando el barbero abre la agenda.
 */
public class RepositorioCitasJdbc implements RepositorioCitas {

    private static final String SELECT_CITA =
            "SELECT c.id, c.cliente_id, u.usuario, u.telefono, c.fecha, c.hora_pedida, c.hora_final, "
            + "c.estado, c.precio, c.propina, c.creada_en, c.decidida_en "
            + "FROM citas c JOIN usuarios u ON u.id = c.cliente_id";

    // La hora efectiva (la misma cuenta que la columna generada hora_ocupada, pero para todas
    // las citas, también las que ya no están activas).
    private static final String HORA_EFECTIVA = "COALESCE(c.hora_final, c.hora_pedida)";

    @Override
    public long insertar(Connection conexion, Cita cita) throws SQLException {
        String sql = "INSERT INTO citas (cliente_id, fecha, hora_pedida, hora_final, estado, creada_en) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setLong(1, cita.clienteId());
            sentencia.setObject(2, cita.fecha());
            sentencia.setObject(3, cita.horaPedida());
            ponerONulo(sentencia, 4, cita.horaFinal(), Types.TIME);
            sentencia.setString(5, cita.estado().enBd());
            sentencia.setObject(6, cita.creadaEn());
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        }
    }

    @Override
    public Optional<Cita> buscarPorId(Connection conexion, long id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(SELECT_CITA + " WHERE c.id = ?")) {
            sentencia.setLong(1, id);
            try (ResultSet filas = sentencia.executeQuery()) {
                return filas.next() ? Optional.of(leerCita(filas)) : Optional.empty();
            }
        }
    }

    @Override
    public int contarActivas(Connection conexion, LocalDate fecha) throws SQLException {
        // Este filtro (fecha y estado) es justo el índice ix_citas_fecha_estado.
        String sql = "SELECT COUNT(*) FROM citas WHERE fecha = ? AND estado IN ('pendiente', 'confirmada')";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setObject(1, fecha);
            try (ResultSet filas = sentencia.executeQuery()) {
                filas.next();
                return filas.getInt(1);
            }
        }
    }

    @Override
    public Map<LocalDate, Integer> contarActivasEntre(Connection conexion, LocalDate desde, LocalDate hasta)
            throws SQLException {
        String sql = "SELECT fecha, COUNT(*) AS activas FROM citas "
                + "WHERE fecha BETWEEN ? AND ? AND estado IN ('pendiente', 'confirmada') GROUP BY fecha";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setObject(1, desde);
            sentencia.setObject(2, hasta);
            Map<LocalDate, Integer> activasPorFecha = new HashMap<>();
            try (ResultSet filas = sentencia.executeQuery()) {
                while (filas.next()) {
                    activasPorFecha.put(filas.getObject("fecha", LocalDate.class), filas.getInt("activas"));
                }
            }
            return activasPorFecha;
        }
    }

    @Override
    public Set<LocalTime> horasOcupadas(Connection conexion, LocalDate fecha) throws SQLException {
        // hora_ocupada solo tiene valor en las citas activas (en las demás es NULL).
        String sql = "SELECT hora_ocupada FROM citas WHERE fecha = ? AND hora_ocupada IS NOT NULL";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setObject(1, fecha);
            Set<LocalTime> horas = new HashSet<>();
            try (ResultSet filas = sentencia.executeQuery()) {
                while (filas.next()) {
                    horas.add(filas.getObject("hora_ocupada", LocalTime.class));
                }
            }
            return horas;
        }
    }

    @Override
    public List<Cita> listarPorCliente(Connection conexion, long clienteId, LocalDate desde) throws SQLException {
        String sql = SELECT_CITA + " WHERE c.cliente_id = ? AND c.fecha >= ? "
                + "ORDER BY c.fecha DESC, " + HORA_EFECTIVA + " DESC, c.id DESC";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, clienteId);
            sentencia.setObject(2, desde);
            return leerCitas(sentencia);
        }
    }

    @Override
    public List<Cita> listar(Connection conexion, List<EstadoCita> estados, LocalDate desde, LocalDate hasta)
            throws SQLException {
        // La consulta se monta según los filtros que lleguen, pero los valores siguen yendo
        // como parámetros "?": al texto SQL solo se añaden trozos fijos escritos por nosotros.
        StringBuilder sql = new StringBuilder(SELECT_CITA).append(" WHERE 1 = 1");
        List<Object> parametros = new ArrayList<>();
        if (estados != null && !estados.isEmpty()) {
            // Tantos "?" como estados: con dos estados queda "IN (?, ?)".
            sql.append(" AND c.estado IN (").append(String.join(", ", Collections.nCopies(estados.size(), "?")))
                    .append(")");
            for (EstadoCita estado : estados) {
                parametros.add(estado.enBd());
            }
        }
        if (desde != null) {
            sql.append(" AND c.fecha >= ?");
            parametros.add(desde);
        }
        if (hasta != null) {
            sql.append(" AND c.fecha <= ?");
            parametros.add(hasta);
        }
        sql.append(" ORDER BY c.fecha, ").append(HORA_EFECTIVA).append(", c.id");

        try (PreparedStatement sentencia = conexion.prepareStatement(sql.toString())) {
            for (int i = 0; i < parametros.size(); i++) {
                sentencia.setObject(i + 1, parametros.get(i));
            }
            return leerCitas(sentencia);
        }
    }

    @Override
    public boolean actualizar(Connection conexion, Cita cita, EstadoCita estadoAnterior) throws SQLException {
        String sql = "UPDATE citas SET estado = ?, hora_final = ?, precio = ?, propina = ?, decidida_en = ? "
                + "WHERE id = ? AND estado = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, cita.estado().enBd());
            ponerONulo(sentencia, 2, cita.horaFinal(), Types.TIME);
            ponerONulo(sentencia, 3, cita.precio(), Types.DECIMAL);
            ponerONulo(sentencia, 4, cita.propina(), Types.DECIMAL);
            ponerONulo(sentencia, 5, cita.decididaEn(), Types.TIMESTAMP);
            sentencia.setLong(6, cita.id());
            sentencia.setString(7, estadoAnterior.enBd());
            return sentencia.executeUpdate() == 1;
        }
    }

    // ---------------------------------------------------------------------------------------

    private static List<Cita> leerCitas(PreparedStatement sentencia) throws SQLException {
        List<Cita> citas = new ArrayList<>();
        try (ResultSet filas = sentencia.executeQuery()) {
            while (filas.next()) {
                citas.add(leerCita(filas));
            }
        }
        return citas;
    }

    private static Cita leerCita(ResultSet fila) throws SQLException {
        return new Cita(
                fila.getLong("id"),
                fila.getLong("cliente_id"),
                fila.getString("usuario"),
                fila.getString("telefono"),
                fila.getObject("fecha", LocalDate.class),
                fila.getObject("hora_pedida", LocalTime.class),
                fila.getObject("hora_final", LocalTime.class),
                EstadoCita.desdeTexto(fila.getString("estado")),
                fila.getObject("precio", BigDecimal.class),
                fila.getObject("propina", BigDecimal.class),
                fila.getObject("creada_en", LocalDateTime.class),
                fila.getObject("decidida_en", LocalDateTime.class));
    }

    private static void ponerONulo(PreparedStatement sentencia, int posicion, Object valor, int tipoSql)
            throws SQLException {
        if (valor == null) {
            sentencia.setNull(posicion, tipoSql);
        } else {
            sentencia.setObject(posicion, valor);
        }
    }
}
