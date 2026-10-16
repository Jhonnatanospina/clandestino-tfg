package com.clandestino.api;

import com.clandestino.api.repositorio.BaseDatos;
import com.clandestino.api.seguridad.Contrasenas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Ayudas para los tests que usan MySQL.
 *
 * Trabajan contra la base clandestino_api_test, que se crea vacía con
 *     scripts\bd.cmd base-tests-api
 * y nunca contra "clandestino". Cada test empieza vaciando las tablas, así no depende de lo
 * que dejó el anterior.
 *
 * Si MySQL no está arrancado, los tests que la usan se saltan (salen como "skipped") en vez de
 * fallar: los tests que no tocan la base de datos se pueden pasar igual.
 */
public final class BaseDePruebas {

    public static final String NOMBRE_BASE = "clandestino_api_test";

    // Para crear usuarios en los tests: 1000 vueltas en vez de 600 000 para que sea instantáneo.
    // verificar() lee las vueltas del propio hash, así que el login de la API los acepta igual.
    private static final Contrasenas contrasenasRapidas = new Contrasenas(1000);

    private BaseDePruebas() {
    }

    /** Conecta con la base de pruebas usando el usuario y la contraseña del .env. */
    public static BaseDatos conectar() {
        Configuracion configuracion = Configuracion.cargar();
        BaseDatos baseDatos = new BaseDatos(configuracion.dbHost(), configuracion.dbPuerto(), NOMBRE_BASE,
                configuracion.dbUsuario(), configuracion.dbContrasena());
        assumeTrue(baseDatos.responde(),
                "MySQL no responde o falta la base " + NOMBRE_BASE + " (scripts\\bd.cmd base-tests-api)");
        return baseDatos;
    }

    /** Borra todas las filas. DELETE y no TRUNCATE porque el usuario de la API no tiene permiso de DROP. */
    public static void vaciar(BaseDatos baseDatos) {
        ejecutar(baseDatos, "DELETE FROM citas", "DELETE FROM invitaciones", "DELETE FROM excepciones_dia",
                "DELETE FROM horarios_semanales", "DELETE FROM usuarios");
    }

    public static long crearAdmin(BaseDatos baseDatos, String usuario, String contrasena) {
        return crearUsuario(baseDatos, usuario, "+34600000001", contrasena, "admin", "aprobado");
    }

    public static long crearCliente(BaseDatos baseDatos, String usuario, String telefono, String estado) {
        return crearUsuario(baseDatos, usuario, telefono, "contrasena-de-prueba", "cliente", estado);
    }

    public static long crearUsuario(BaseDatos baseDatos, String usuario, String telefono, String contrasena,
                                    String rol, String estado) {
        String sql = "INSERT INTO usuarios (usuario, telefono, password_hash, rol, estado, creado_en) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conexion = baseDatos.conexion();
             PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            sentencia.setString(1, usuario);
            sentencia.setString(2, telefono);
            sentencia.setString(3, contrasenasRapidas.hashear(contrasena));
            sentencia.setString(4, rol);
            sentencia.setString(5, estado);
            sentencia.setObject(6, LocalDateTime.of(2026, 9, 1, 12, 0));
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                claves.next();
                return claves.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Un día de la semana con horario (1 = lunes ... 7 = domingo). */
    public static void crearHorario(BaseDatos baseDatos, int diaSemana, LocalTime inicio, LocalTime fin, int cupo) {
        String sql = "INSERT INTO horarios_semanales (dia_semana, hora_inicio, hora_fin, cupo, activo) "
                + "VALUES (?, ?, ?, ?, TRUE)";
        try (Connection conexion = baseDatos.conexion();
             PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, diaSemana);
            sentencia.setObject(2, inicio);
            sentencia.setObject(3, fin);
            sentencia.setInt(4, cupo);
            sentencia.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Un número de una consulta (por ejemplo, un COUNT). */
    public static long numero(BaseDatos baseDatos, String sql) {
        try (Connection conexion = baseDatos.conexion();
             Statement sentencia = conexion.createStatement();
             ResultSet filas = sentencia.executeQuery(sql)) {
            filas.next();
            return filas.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void ejecutar(BaseDatos baseDatos, String... sentencias) {
        try (Connection conexion = baseDatos.conexion(); Statement sentencia = conexion.createStatement()) {
            for (String sql : sentencias) {
                sentencia.executeUpdate(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
