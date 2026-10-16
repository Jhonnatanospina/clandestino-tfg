package com.clandestino.api.repositorio;

import com.clandestino.api.modelo.EstadoInvitacion;
import com.clandestino.api.modelo.EstadoUsuario;
import com.clandestino.api.modelo.Invitacion;
import com.clandestino.api.modelo.Rol;
import com.clandestino.api.modelo.Usuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RepositorioUsuarios con JDBC y MySQL.
 *
 * Todas las consultas van con PreparedStatement y parámetros "?": los datos que escribe el
 * usuario nunca se pegan dentro del texto SQL, así que no hay inyección SQL posible.
 *
 * Las fechas se leen y se escriben con los tipos de java.time (getObject(..., LocalDateTime.class)
 * y setObject). Las clases antiguas java.sql.Date y java.sql.Timestamp no se usan.
 */
public class RepositorioUsuariosJdbc implements RepositorioUsuarios {

    private static final String COLUMNAS_USUARIO =
            "id, usuario, telefono, password_hash, rol, estado, precio_corte, creado_en, aprobado_en";

    // El LEFT JOIN trae el @usuario de quien usó la invitación (si la usó alguien).
    private static final String SELECT_INVITACION =
            "SELECT i.id, i.codigo, i.nota, i.estado, i.caduca_en, i.creada_por, i.usada_por, "
            + "u.usuario AS usada_por_usuario, i.creada_en "
            + "FROM invitaciones i LEFT JOIN usuarios u ON u.id = i.usada_por";

    @Override
    public Optional<Usuario> buscarPorId(Connection conexion, long id) throws SQLException {
        String sql = "SELECT " + COLUMNAS_USUARIO + " FROM usuarios WHERE id = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, id);
            return leerUsuarioSiHay(sentencia);
        }
    }

    @Override
    public Optional<Usuario> buscarPorUsuario(Connection conexion, String usuario) throws SQLException {
        String sql = "SELECT " + COLUMNAS_USUARIO + " FROM usuarios WHERE usuario = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, usuario);
            return leerUsuarioSiHay(sentencia);
        }
    }

    @Override
    public long insertar(Connection conexion, Usuario usuario) throws SQLException {
        String sql = "INSERT INTO usuarios (usuario, telefono, password_hash, rol, estado, precio_corte, "
                + "creado_en, aprobado_en) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setString(1, usuario.usuario());
            sentencia.setString(2, usuario.telefono());
            sentencia.setString(3, usuario.passwordHash());
            sentencia.setString(4, usuario.rol().enBd());
            sentencia.setString(5, usuario.estado().enBd());
            sentencia.setBigDecimal(6, usuario.precioCorte());
            sentencia.setObject(7, usuario.creadoEn());
            ponerFechaHora(sentencia, 8, usuario.aprobadoEn());
            sentencia.executeUpdate();
            return idGenerado(sentencia);
        }
    }

    @Override
    public boolean cambiarEstado(Connection conexion, long id, EstadoUsuario estadoAnterior,
                                 EstadoUsuario estadoNuevo, LocalDateTime aprobadoEn) throws SQLException {
        // Si no nos pasan fecha de aprobación (al bloquear), se queda la que tuviera.
        // La condición "AND estado = ?" es la guarda: si otro cambio se nos adelantó, no toca nada.
        String sql = "UPDATE usuarios SET estado = ?, aprobado_en = COALESCE(?, aprobado_en) "
                + "WHERE id = ? AND estado = ? AND rol = 'cliente'";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, estadoNuevo.enBd());
            ponerFechaHora(sentencia, 2, aprobadoEn);
            sentencia.setLong(3, id);
            sentencia.setString(4, estadoAnterior.enBd());
            return sentencia.executeUpdate() == 1;
        }
    }

    @Override
    public boolean existeAdmin(Connection conexion) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement("SELECT 1 FROM usuarios WHERE rol = 'admin' LIMIT 1");
             ResultSet filas = sentencia.executeQuery()) {
            return filas.next();
        }
    }

    @Override
    public List<Usuario> listarClientes(Connection conexion, EstadoUsuario estado) throws SQLException {
        String sql = "SELECT " + COLUMNAS_USUARIO + " FROM usuarios WHERE rol = 'cliente'"
                + (estado != null ? " AND estado = ?" : "")
                + " ORDER BY creado_en DESC, id DESC";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            if (estado != null) {
                sentencia.setString(1, estado.enBd());
            }
            List<Usuario> clientes = new ArrayList<>();
            try (ResultSet filas = sentencia.executeQuery()) {
                while (filas.next()) {
                    clientes.add(leerUsuario(filas));
                }
            }
            return clientes;
        }
    }

    @Override
    public Optional<Invitacion> buscarInvitacionParaUsar(Connection conexion, String codigo) throws SQLException {
        // Sin el JOIN a propósito: FOR UPDATE bloquearía también la fila del usuario unido.
        String sql = "SELECT id, codigo, nota, estado, caduca_en, creada_por, usada_por, "
                + "NULL AS usada_por_usuario, creada_en FROM invitaciones WHERE codigo = ? FOR UPDATE";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, codigo);
            try (ResultSet filas = sentencia.executeQuery()) {
                return filas.next() ? Optional.of(leerInvitacion(filas)) : Optional.empty();
            }
        }
    }

    @Override
    public long insertarInvitacion(Connection conexion, Invitacion invitacion) throws SQLException {
        String sql = "INSERT INTO invitaciones (codigo, nota, estado, caduca_en, creada_por, creada_en) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setString(1, invitacion.codigo());
            sentencia.setString(2, invitacion.nota());
            sentencia.setString(3, invitacion.estado().enBd());
            sentencia.setObject(4, invitacion.caducaEn());
            sentencia.setLong(5, invitacion.creadaPor());
            sentencia.setObject(6, invitacion.creadaEn());
            sentencia.executeUpdate();
            return idGenerado(sentencia);
        }
    }

    @Override
    public void marcarUsada(Connection conexion, long invitacionId, long usuarioId) throws SQLException {
        String sql = "UPDATE invitaciones SET estado = 'usada', usada_por = ? WHERE id = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, usuarioId);
            sentencia.setLong(2, invitacionId);
            sentencia.executeUpdate();
        }
    }

    @Override
    public List<Invitacion> listarInvitaciones(Connection conexion) throws SQLException {
        String sql = SELECT_INVITACION + " ORDER BY i.creada_en DESC, i.id DESC";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
             ResultSet filas = sentencia.executeQuery()) {
            List<Invitacion> invitaciones = new ArrayList<>();
            while (filas.next()) {
                invitaciones.add(leerInvitacion(filas));
            }
            return invitaciones;
        }
    }

    @Override
    public Optional<Invitacion> buscarInvitacionPorId(Connection conexion, long id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(SELECT_INVITACION + " WHERE i.id = ?")) {
            sentencia.setLong(1, id);
            try (ResultSet filas = sentencia.executeQuery()) {
                return filas.next() ? Optional.of(leerInvitacion(filas)) : Optional.empty();
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // De filas a objetos
    // ---------------------------------------------------------------------------------------

    private static Optional<Usuario> leerUsuarioSiHay(PreparedStatement sentencia) throws SQLException {
        try (ResultSet filas = sentencia.executeQuery()) {
            return filas.next() ? Optional.of(leerUsuario(filas)) : Optional.empty();
        }
    }

    private static Usuario leerUsuario(ResultSet fila) throws SQLException {
        return new Usuario(
                fila.getLong("id"),
                fila.getString("usuario"),
                fila.getString("telefono"),
                fila.getString("password_hash"),
                Rol.desdeTexto(fila.getString("rol")),
                EstadoUsuario.desdeTexto(fila.getString("estado")),
                fila.getBigDecimal("precio_corte"),
                fila.getObject("creado_en", LocalDateTime.class),
                fila.getObject("aprobado_en", LocalDateTime.class));
    }

    private static Invitacion leerInvitacion(ResultSet fila) throws SQLException {
        return new Invitacion(
                fila.getLong("id"),
                fila.getString("codigo"),
                fila.getString("nota"),
                EstadoInvitacion.desdeTexto(fila.getString("estado")),
                fila.getObject("caduca_en", LocalDateTime.class),
                fila.getLong("creada_por"),
                // getObject con Long.class devuelve null si la columna es NULL; getLong daría 0.
                fila.getObject("usada_por", Long.class),
                fila.getString("usada_por_usuario"),
                fila.getObject("creada_en", LocalDateTime.class));
    }

    private static void ponerFechaHora(PreparedStatement sentencia, int posicion, LocalDateTime valor)
            throws SQLException {
        if (valor == null) {
            sentencia.setNull(posicion, Types.TIMESTAMP);
        } else {
            sentencia.setObject(posicion, valor);
        }
    }

    private static long idGenerado(PreparedStatement sentencia) throws SQLException {
        try (ResultSet claves = sentencia.getGeneratedKeys()) {
            claves.next();
            return claves.getLong(1);
        }
    }
}
