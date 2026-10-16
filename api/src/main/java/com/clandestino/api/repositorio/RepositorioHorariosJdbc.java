package com.clandestino.api.repositorio;

import com.clandestino.api.modelo.ExcepcionDia;
import com.clandestino.api.modelo.HorarioSemanal;
import com.clandestino.api.modelo.TipoExcepcion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** RepositorioHorarios con JDBC y MySQL. */
public class RepositorioHorariosJdbc implements RepositorioHorarios {

    private static final String SELECT_EXCEPCION =
            "SELECT id, fecha, tipo, hora_inicio, hora_fin, cupo, nota FROM excepciones_dia";

    @Override
    public List<HorarioSemanal> semanal(Connection conexion) throws SQLException {
        String sql = "SELECT id, dia_semana, hora_inicio, hora_fin, cupo, activo "
                + "FROM horarios_semanales ORDER BY dia_semana";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
             ResultSet filas = sentencia.executeQuery()) {
            List<HorarioSemanal> horarios = new ArrayList<>();
            while (filas.next()) {
                horarios.add(new HorarioSemanal(
                        filas.getInt("id"),
                        filas.getInt("dia_semana"),
                        filas.getObject("hora_inicio", LocalTime.class),
                        filas.getObject("hora_fin", LocalTime.class),
                        filas.getInt("cupo"),
                        filas.getBoolean("activo")));
            }
            return horarios;
        }
    }

    @Override
    public void guardarSemanal(Connection conexion, HorarioSemanal horario) throws SQLException {
        // Un solo INSERT que, si ya hay fila para ese día (índice único uq_horarios_dia), la
        // actualiza. "AS nuevo" es la forma actual de MySQL de referirse a los valores que
        // intentábamos insertar; la antigua, VALUES(columna), está marcada como obsoleta.
        String sql = "INSERT INTO horarios_semanales (dia_semana, hora_inicio, hora_fin, cupo, activo) "
                + "VALUES (?, ?, ?, ?, ?) AS nuevo "
                + "ON DUPLICATE KEY UPDATE hora_inicio = nuevo.hora_inicio, hora_fin = nuevo.hora_fin, "
                + "cupo = nuevo.cupo, activo = nuevo.activo";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, horario.diaSemana());
            sentencia.setObject(2, horario.horaInicio());
            sentencia.setObject(3, horario.horaFin());
            sentencia.setInt(4, horario.cupo());
            sentencia.setBoolean(5, horario.activo());
            sentencia.executeUpdate();
        }
    }

    @Override
    public Optional<ExcepcionDia> excepcion(Connection conexion, LocalDate fecha) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(SELECT_EXCEPCION + " WHERE fecha = ?")) {
            sentencia.setObject(1, fecha);
            try (ResultSet filas = sentencia.executeQuery()) {
                return filas.next() ? Optional.of(leerExcepcion(filas)) : Optional.empty();
            }
        }
    }

    @Override
    public List<ExcepcionDia> excepciones(Connection conexion, LocalDate desde, LocalDate hasta)
            throws SQLException {
        String sql = SELECT_EXCEPCION + " WHERE fecha >= ?" + (hasta != null ? " AND fecha <= ?" : "")
                + " ORDER BY fecha";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setObject(1, desde);
            if (hasta != null) {
                sentencia.setObject(2, hasta);
            }
            List<ExcepcionDia> excepciones = new ArrayList<>();
            try (ResultSet filas = sentencia.executeQuery()) {
                while (filas.next()) {
                    excepciones.add(leerExcepcion(filas));
                }
            }
            return excepciones;
        }
    }

    @Override
    public int guardarExcepcion(Connection conexion, ExcepcionDia excepcion) throws SQLException {
        // Como en guardarSemanal: si ya había una excepción ese día (uq_excepciones_fecha), la nueva la sustituye.
        String sql = "INSERT INTO excepciones_dia (fecha, tipo, hora_inicio, hora_fin, cupo, nota) "
                + "VALUES (?, ?, ?, ?, ?, ?) AS nueva "
                + "ON DUPLICATE KEY UPDATE tipo = nueva.tipo, hora_inicio = nueva.hora_inicio, "
                + "hora_fin = nueva.hora_fin, cupo = nueva.cupo, nota = nueva.nota";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setObject(1, excepcion.fecha());
            sentencia.setString(2, excepcion.tipo().enBd());
            ponerONulo(sentencia, 3, excepcion.horaInicio(), Types.TIME);
            ponerONulo(sentencia, 4, excepcion.horaFin(), Types.TIME);
            ponerONulo(sentencia, 5, excepcion.cupo(), Types.TINYINT);
            ponerONulo(sentencia, 6, excepcion.nota(), Types.VARCHAR);
            sentencia.executeUpdate();
        }
        // Al sustituir una fila, getGeneratedKeys no da el id de la que ya existía, así que lo
        // buscamos por la fecha, que es única. Es una consulta más, pero se entiende sin trucos.
        return excepcion(conexion, excepcion.fecha()).orElseThrow().id();
    }

    @Override
    public boolean borrarExcepcion(Connection conexion, int id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement("DELETE FROM excepciones_dia WHERE id = ?")) {
            sentencia.setInt(1, id);
            return sentencia.executeUpdate() == 1;
        }
    }

    // ---------------------------------------------------------------------------------------

    private static ExcepcionDia leerExcepcion(ResultSet fila) throws SQLException {
        return new ExcepcionDia(
                fila.getInt("id"),
                fila.getObject("fecha", LocalDate.class),
                TipoExcepcion.desdeTexto(fila.getString("tipo")),
                fila.getObject("hora_inicio", LocalTime.class),
                fila.getObject("hora_fin", LocalTime.class),
                fila.getObject("cupo", Integer.class),
                fila.getString("nota"));
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
