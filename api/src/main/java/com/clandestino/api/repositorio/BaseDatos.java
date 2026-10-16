package com.clandestino.api.repositorio;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Da conexiones a MySQL y se encarga de las transacciones.
 *
 * En esta primera versión cada petición abre su propia conexión con DriverManager y la cierra
 * al terminar. Es lo más sencillo y, con los pocos usuarios de una barbería, suficiente. Un
 * pool de conexiones (HikariCP) queda como mejora (P2) y la idea es medir la diferencia.
 *
 * Los repositorios no abren conexiones: reciben la que les pasa el servicio. Así un servicio
 * puede hacer varias operaciones (contar las citas del día y luego insertar) dentro de la
 * misma transacción.
 */
public class BaseDatos {

    /**
     * Lo que se hace con una conexión. Es una interfaz funcional para poder escribirlo como
     * lambda: baseDatos.enTransaccion(conexion -> repositorio.insertar(conexion, cita)).
     * Puede lanzar SQLException para no tener que poner try/catch en cada lambda.
     */
    @FunctionalInterface
    public interface Trabajo<T> {
        T hacer(Connection conexion) throws SQLException;
    }

    private final String url;
    private final String usuario;
    private final String contrasena;

    public BaseDatos(String host, int puerto, String nombreBase, String usuario, String contrasena) {
        // sslMode=DISABLED porque MySQL solo escucha en 127.0.0.1 (bind-address del my.ini) y la
        // conexión no sale del ordenador. Con un servidor de base de datos en otra máquina habría
        // que activarlo.
        this.url = "jdbc:mysql://" + host + ":" + puerto + "/" + nombreBase
                + "?sslMode=DISABLED&allowPublicKeyRetrieval=true"
                + "&characterEncoding=UTF-8&connectTimeout=5000";
        this.usuario = usuario;
        this.contrasena = contrasena;
    }

    /** Conexión nueva en modo autocommit. Quien la pide la tiene que cerrar (try-with-resources). */
    public Connection conexion() throws SQLException {
        return DriverManager.getConnection(url, usuario, contrasena);
    }

    /**
     * Para lecturas sueltas que no necesitan transacción: abre la conexión, hace el trabajo y la
     * cierra pase lo que pase.
     */
    public <T> T consultar(Trabajo<T> trabajo) {
        try (Connection conexion = conexion()) {
            return trabajo.hacer(conexion);
        } catch (SQLException e) {
            throw new ErrorBaseDatos(e);
        }
    }

    /**
     * Ejecuta el trabajo en una transacción: o se guarda todo, o no se guarda nada.
     *
     * Si el trabajo termina bien se hace commit. Si lanza cualquier excepción (un error SQL o un
     * ErrorNegocio, por ejemplo "duplicado" a mitad de un registro) se hace rollback y la
     * excepción sigue hacia arriba tal cual, para que el Enrutador responda lo que toque.
     */
    public <T> T enTransaccion(Trabajo<T> trabajo) {
        try (Connection conexion = conexion()) {
            conexion.setAutoCommit(false);
            try {
                T resultado = trabajo.hacer(conexion);
                conexion.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                conexion.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new ErrorBaseDatos(e);
        }
    }

    /** Consulta mínima para saber si MySQL responde. La usa GET /api/salud. */
    public boolean responde() {
        try (Connection conexion = conexion(); Statement sentencia = conexion.createStatement()) {
            return sentencia.execute("SELECT 1");
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Error de la base de datos que no esperábamos (MySQL apagado, una consulta mal escrita...).
     * Lo envolvemos en una excepción no comprobada; el Enrutador responde 500 sin dar detalles
     * y lo apunta en el registro del servidor.
     */
    public static class ErrorBaseDatos extends RuntimeException {
        public ErrorBaseDatos(SQLException causa) {
            super(causa);
        }
    }

    /**
     * ¿Es un choque con un índice único? MySQL da SQLState 23000 y código 1062 ("Duplicate
     * entry"). Lo usamos para el teléfono o el usuario repetidos y para dos citas en el mismo
     * hueco. Ojo, el mismo SQLState 23000 sale también con las claves foráneas (1452), por eso
     * miramos el código concreto.
     */
    public static boolean esDuplicado(SQLException e) {
        return "23000".equals(e.getSQLState()) && e.getErrorCode() == 1062;
    }
}
