package com.clandestino.api.herramientas;

import com.clandestino.api.Configuracion;
import com.clandestino.api.modelo.EstadoUsuario;
import com.clandestino.api.modelo.Rol;
import com.clandestino.api.modelo.Usuario;
import com.clandestino.api.repositorio.BaseDatos;
import com.clandestino.api.repositorio.RepositorioUsuarios;
import com.clandestino.api.repositorio.RepositorioUsuariosJdbc;
import com.clandestino.api.seguridad.Contrasenas;
import com.clandestino.api.servicio.ErrorNegocio;
import com.clandestino.api.servicio.Reloj;
import com.clandestino.api.servicio.ServicioUsuarios;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Crea la cuenta del barbero. Se usa una sola vez, al instalar:
 *
 *     gradlew crearAdmin --args="barbero +34600111222"
 *
 * La contraseña se pide por teclado y no por argumento, para que no se quede guardada en el
 * historial de la consola. No hay ninguna ruta de la API que cree administradores: el registro
 * solo crea clientes, así que esta es la única forma de tener uno.
 */
public class CrearAdmin {

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Uso: gradlew crearAdmin --args=\"usuario telefono\"");
            System.exit(2);
        }

        String usuario;
        String telefono;
        try {
            usuario = ServicioUsuarios.comprobarUsuario(ServicioUsuarios.normalizarUsuario(args[0]));
            telefono = ServicioUsuarios.comprobarTelefono(ServicioUsuarios.normalizarTelefono(args[1]));
        } catch (ErrorNegocio e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }

        String contrasena = pedirContrasena();
        try {
            ServicioUsuarios.comprobarContrasena(contrasena);
        } catch (ErrorNegocio e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }

        Configuracion configuracion = Configuracion.cargar();
        BaseDatos baseDatos = new BaseDatos(configuracion.dbHost(), configuracion.dbPuerto(),
                configuracion.dbNombre(), configuracion.dbUsuario(), configuracion.dbContrasena());
        RepositorioUsuarios usuarios = new RepositorioUsuariosJdbc();

        // La huella fuera de la transacción, igual que en el registro (tarda casi un segundo).
        String hash = new Contrasenas().hashear(contrasena);
        var ahora = Reloj.deMadrid().ahora();

        try {
            long id = baseDatos.enTransaccion(conexion -> {
                // Solo hay un barbero (casos-de-uso.md). Si ya existe, no se crea otro.
                if (usuarios.existeAdmin(conexion)) {
                    throw new ErrorNegocio("ya_hay_admin", 409, "Ya existe una cuenta de administrador.");
                }
                // precio_corte no se usa con el admin, pero la columna no admite NULL: va el de por defecto.
                Usuario admin = new Usuario(0, usuario, telefono, hash, Rol.ADMIN, EstadoUsuario.APROBADO,
                        new BigDecimal("15.00"), ahora, ahora);
                return usuarios.insertar(conexion, admin);
            });
            System.out.println("Administrador @" + usuario + " creado con id " + id + ".");
        } catch (ErrorNegocio e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (BaseDatos.ErrorBaseDatos e) {
            // Lo más normal: el @usuario o el teléfono ya existen, o MySQL no está arrancado.
            System.err.println("No se ha podido crear: " + e.getCause().getMessage());
            System.exit(1);
        }
    }

    /**
     * Desde una consola de verdad la contraseña no se ve al escribirla. Desde Gradle no hay
     * consola (System.console() es null) y se lee de la entrada normal, que sí se ve.
     */
    private static String pedirContrasena() throws IOException {
        Console consola = System.console();
        if (consola != null) {
            char[] primera = consola.readPassword("Contraseña: ");
            char[] segunda = consola.readPassword("Repite la contraseña: ");
            if (primera == null || segunda == null || !new String(primera).equals(new String(segunda))) {
                System.err.println("Las contraseñas no coinciden.");
                System.exit(2);
            }
            return new String(primera);
        }
        System.out.print("Contraseña (se verá al escribirla): ");
        System.out.flush();
        BufferedReader entrada = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String linea = entrada.readLine();
        return linea == null ? "" : linea;
    }
}
