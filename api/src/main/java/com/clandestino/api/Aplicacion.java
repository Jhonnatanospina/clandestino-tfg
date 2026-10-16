package com.clandestino.api;

import com.clandestino.api.http.Enrutador;
import com.clandestino.api.http.RutasAdmin;
import com.clandestino.api.http.RutasAutenticacion;
import com.clandestino.api.http.RutasCliente;
import com.clandestino.api.http.ServidorApi;
import com.clandestino.api.repositorio.BaseDatos;
import com.clandestino.api.repositorio.RepositorioCitas;
import com.clandestino.api.repositorio.RepositorioCitasJdbc;
import com.clandestino.api.repositorio.RepositorioHorarios;
import com.clandestino.api.repositorio.RepositorioHorariosJdbc;
import com.clandestino.api.repositorio.RepositorioUsuarios;
import com.clandestino.api.repositorio.RepositorioUsuariosJdbc;
import com.clandestino.api.seguridad.Contrasenas;
import com.clandestino.api.seguridad.FiltroAutenticacion;
import com.clandestino.api.seguridad.LimitadorIntentos;
import com.clandestino.api.seguridad.TokenJwt;
import com.clandestino.api.servicio.Reloj;
import com.clandestino.api.servicio.ServicioCitas;
import com.clandestino.api.servicio.ServicioHorarios;
import com.clandestino.api.servicio.ServicioUsuarios;

import java.io.IOException;
import java.time.Duration;

/**
 * Punto de entrada de la API. Lee la configuración, crea todas las piezas, las conecta entre sí
 * y arranca el servidor.
 *
 * No hay inyección de dependencias automática: cada clase recibe en el constructor lo que
 * necesita y aquí se hace el "montaje" a mano. Así se ve de un vistazo qué depende de qué, y en
 * los tests se puede montar lo mismo con otro reloj o con otra base de datos.
 */
public class Aplicacion {

    // Valores de seguridad de DP-08 (reglas-negocio.md).
    private static final Duration DURACION_TOKEN = Duration.ofDays(7);
    private static final int FALLOS_PERMITIDOS = 5;
    private static final Duration VENTANA_FALLOS = Duration.ofMinutes(15);

    public static void main(String[] args) throws IOException {
        // Una línea por mensaje: "12:30:05 GET /api/dias -> 200 (14 ms)". Hay que ponerlo antes
        // de que se cree el primer Logger, si no ya se queda con el formato de dos líneas.
        // Sin el nivel (%4$s) a propósito: en Windows sale traducido como "INFORMACIÓN" y la
        // consola lo pinta con caracteres rotos. Los errores ya se ven por la traza que llevan.
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tT %5$s%6$s%n");

        Configuracion configuracion = Configuracion.cargar();
        BaseDatos baseDatos = new BaseDatos(configuracion.dbHost(), configuracion.dbPuerto(),
                configuracion.dbNombre(), configuracion.dbUsuario(), configuracion.dbContrasena());

        // Si MySQL no está arrancado, mejor avisarlo ya que descubrirlo en la primera petición.
        if (!baseDatos.responde()) {
            System.err.println("No se puede conectar con MySQL en " + configuracion.dbHost() + ":"
                    + configuracion.dbPuerto() + ". ¿Está arrancado? (scripts\\mysql.cmd iniciar)");
            System.exit(1);
        }

        Enrutador enrutador = montar(baseDatos, configuracion.secretoJwt(), Reloj.deMadrid());
        ServidorApi servidor = new ServidorApi();
        servidor.iniciar(configuracion.apiHost(), configuracion.apiPuerto(), enrutador);

        // Con Ctrl+C se para con calma, dejando acabar las peticiones que estén a medias.
        Runtime.getRuntime().addShutdownHook(new Thread(servidor::parar));

        System.out.println("API de Clandestino escuchando en http://" + configuracion.apiHost() + ":"
                + servidor.puerto() + "  (Ctrl+C para parar)");
    }

    /**
     * Crea los repositorios, los servicios y las rutas, y devuelve el enrutador ya listo. Es
     * público porque el test de punta a punta monta la API igual que aquí, con un reloj fijo.
     */
    public static Enrutador montar(BaseDatos baseDatos, String secretoJwt, Reloj reloj) {
        RepositorioUsuarios repositorioUsuarios = new RepositorioUsuariosJdbc();
        RepositorioCitas repositorioCitas = new RepositorioCitasJdbc();
        RepositorioHorarios repositorioHorarios = new RepositorioHorariosJdbc();

        TokenJwt tokenJwt = new TokenJwt(secretoJwt, reloj, DURACION_TOKEN);
        LimitadorIntentos limitador = new LimitadorIntentos(FALLOS_PERMITIDOS, VENTANA_FALLOS, reloj);

        ServicioHorarios servicioHorarios = new ServicioHorarios(baseDatos, repositorioHorarios, repositorioCitas, reloj);
        ServicioCitas servicioCitas = new ServicioCitas(baseDatos, repositorioCitas, repositorioUsuarios,
                servicioHorarios, reloj);
        ServicioUsuarios servicioUsuarios = new ServicioUsuarios(baseDatos, repositorioUsuarios,
                new Contrasenas(), tokenJwt, reloj);

        Enrutador enrutador = new Enrutador(new FiltroAutenticacion(tokenJwt, baseDatos, repositorioUsuarios));
        new RutasAutenticacion(servicioUsuarios, limitador, baseDatos).registrar(enrutador);
        new RutasCliente(servicioHorarios, servicioCitas).registrar(enrutador);
        new RutasAdmin(servicioCitas, servicioHorarios, servicioUsuarios, reloj).registrar(enrutador);
        return enrutador;
    }
}
