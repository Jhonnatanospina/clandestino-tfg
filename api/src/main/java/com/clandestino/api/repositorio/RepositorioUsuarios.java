package com.clandestino.api.repositorio;

import com.clandestino.api.modelo.EstadoUsuario;
import com.clandestino.api.modelo.Invitacion;
import com.clandestino.api.modelo.Usuario;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a las tablas usuarios e invitaciones.
 *
 * Las invitaciones van aquí y no en un repositorio aparte porque solo se usan para dar de alta
 * usuarios, y en el registro las dos tablas se tocan en la misma transacción.
 *
 * Todos los métodos reciben la conexión como primer parámetro: la abre el servicio, que es
 * quien sabe si hace falta una transacción (ver BaseDatos).
 */
public interface RepositorioUsuarios {

    Optional<Usuario> buscarPorId(Connection conexion, long id) throws SQLException;

    Optional<Usuario> buscarPorUsuario(Connection conexion, String usuario) throws SQLException;

    /** Inserta el usuario y devuelve su id. Si el @usuario o el teléfono ya existen, MySQL lanza el error 1062. */
    long insertar(Connection conexion, Usuario usuario) throws SQLException;

    /**
     * Cambia el estado solo si el usuario sigue en el estado que esperábamos. Devuelve false si
     * no se tocó ninguna fila (alguien lo cambió entre medias).
     */
    boolean cambiarEstado(Connection conexion, long id, EstadoUsuario estadoAnterior,
                          EstadoUsuario estadoNuevo, LocalDateTime aprobadoEn) throws SQLException;

    /** Si ya hay un usuario con rol admin. Lo usa la herramienta CrearAdmin para no crear dos. */
    boolean existeAdmin(Connection conexion) throws SQLException;

    /** Usuarios con rol cliente, los más nuevos primero. Con estado null, todos. */
    List<Usuario> listarClientes(Connection conexion, EstadoUsuario estado) throws SQLException;

    /**
     * Busca la invitación y bloquea su fila (SELECT ... FOR UPDATE) hasta el final de la
     * transacción, para que dos personas no puedan registrarse a la vez con el mismo código.
     * Solo tiene sentido dentro de una transacción.
     */
    Optional<Invitacion> buscarInvitacionParaUsar(Connection conexion, String codigo) throws SQLException;

    long insertarInvitacion(Connection conexion, Invitacion invitacion) throws SQLException;

    void marcarUsada(Connection conexion, long invitacionId, long usuarioId) throws SQLException;

    /** Todas las invitaciones, las más recientes primero, con el @usuario de quien la usó. */
    List<Invitacion> listarInvitaciones(Connection conexion) throws SQLException;

    Optional<Invitacion> buscarInvitacionPorId(Connection conexion, long id) throws SQLException;
}
