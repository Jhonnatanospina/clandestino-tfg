package com.clandestino.api.servicio;

import com.clandestino.api.BaseDePruebas;
import com.clandestino.api.RelojMovible;
import com.clandestino.api.modelo.Cita;
import com.clandestino.api.modelo.EstadoCita;
import com.clandestino.api.repositorio.BaseDatos;
import com.clandestino.api.repositorio.RepositorioCitas;
import com.clandestino.api.repositorio.RepositorioCitasJdbc;
import com.clandestino.api.repositorio.RepositorioHorariosJdbc;
import com.clandestino.api.repositorio.RepositorioUsuariosJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La carrera del cupo (ver ServicioCitas.pedir) y cómo la evita el synchronized.
 *
 * Varios hilos piden cita a la vez para el mismo martes. Para que la carrera pase SIEMPRE y no
 * solo de vez en cuando, el repositorio de citas va envuelto en uno "lento" que, después de
 * contar las citas del día, espera 300 ms antes de devolver la cuenta. En ese rato todos los
 * hilos han contado ya, y todos creen que hay sitio.
 *
 *  - Sin cerrojo: con cupo 1 entran todas (el día acaba con más citas que cupo). Es el fallo.
 *  - Con cerrojo: entra una y las demás reciben dia_lleno.
 *  - Mismo hueco: aunque todas pasen las comprobaciones, el índice único deja entrar solo a una.
 */
class PruebaConcurrenciaCupo {

    private static final LocalDate MARTES = LocalDate.of(2026, 10, 6);
    private static final int HILOS = 5;

    private BaseDatos baseDatos;
    private RelojMovible reloj;
    private final List<Long> clientes = new ArrayList<>();

    @BeforeEach
    void prepararBase() {
        baseDatos = BaseDePruebas.conectar();
        BaseDePruebas.vaciar(baseDatos);
        reloj = new RelojMovible(LocalDateTime.of(2026, 10, 5, 10, 0));
        clientes.clear();
        for (int i = 0; i < HILOS; i++) {
            clientes.add(BaseDePruebas.crearCliente(baseDatos, "cliente" + i, "+3461100010" + i, "aprobado"));
        }
    }

    @Test
    void sinCerrojoElCupoSeDesborda() throws InterruptedException {
        // Tramo de 16:00 a 21:00 (cinco horas distintas) y cupo 1.
        BaseDePruebas.crearHorario(baseDatos, 2, LocalTime.of(16, 0), LocalTime.of(21, 0), 1);
        List<Resultado> resultados = pedirALaVez(servicio(false), true);

        long creadas = resultados.stream().filter(r -> r.cita != null).count();
        System.out.println("Sin cerrojo, con cupo 1, se han creado " + creadas + " citas");
        assertTrue(creadas > 1, "Sin el synchronized deberían colarse varias citas");
        assertEquals(creadas, citasActivasDelMartes());
    }

    @Test
    void conCerrojoSoloEntraUna() throws InterruptedException {
        BaseDePruebas.crearHorario(baseDatos, 2, LocalTime.of(16, 0), LocalTime.of(21, 0), 1);
        List<Resultado> resultados = pedirALaVez(servicio(true), true);

        assertEquals(1, resultados.stream().filter(r -> r.cita != null).count());
        assertEquals(HILOS - 1, resultados.stream().filter(r -> "dia_lleno".equals(r.error)).count());
        assertEquals(1, citasActivasDelMartes());
    }

    @Test
    void elMismoHuecoLoProtegeElIndiceUnicoAunqueNoHayaCerrojo() throws InterruptedException {
        // Cupo 5: el cupo no frena a nadie, todos piden las 17:00.
        BaseDePruebas.crearHorario(baseDatos, 2, LocalTime.of(16, 0), LocalTime.of(21, 0), 5);
        List<Resultado> resultados = pedirALaVez(servicio(false), false);

        assertEquals(1, resultados.stream().filter(r -> r.cita != null).count());
        assertEquals(HILOS - 1, resultados.stream().filter(r -> "hueco_ocupado".equals(r.error)).count());
        assertEquals(1, citasActivasDelMartes());
    }

    // ---------------------------------------------------------------------------------------

    /** Lo que le ha pasado a cada hilo: o una cita o el código de error. */
    private static final class Resultado {
        volatile Cita cita;
        volatile String error;
    }

    /**
     * Lanza un hilo por cliente. Todos esperan en la "línea de salida" (un CountDownLatch) y
     * salen a la vez. Con horasDistintas cada uno pide una hora (16:00, 17:00...); si no, todos
     * piden las 17:00.
     */
    private List<Resultado> pedirALaVez(ServicioCitas servicio, boolean horasDistintas) throws InterruptedException {
        CountDownLatch salida = new CountDownLatch(1);
        List<Resultado> resultados = Collections.synchronizedList(new ArrayList<>());
        List<Thread> hilos = new ArrayList<>();

        for (int i = 0; i < HILOS; i++) {
            long clienteId = clientes.get(i);
            LocalTime hora = horasDistintas ? LocalTime.of(16 + i, 0) : LocalTime.of(17, 0);
            Thread hilo = new Thread(() -> {
                Resultado resultado = new Resultado();
                try {
                    salida.await();
                    resultado.cita = servicio.pedir(clienteId, MARTES, hora);
                } catch (ErrorNegocio e) {
                    resultado.error = e.getCodigo();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    resultado.error = "interrumpido";
                } catch (RuntimeException e) {
                    resultado.error = e.toString();
                }
                resultados.add(resultado);
            }, "cliente-" + i);
            hilos.add(hilo);
            hilo.start();
        }

        salida.countDown();
        for (Thread hilo : hilos) {
            hilo.join(30_000);
        }
        assertEquals(HILOS, resultados.size(), "Algún hilo no ha terminado");
        for (Resultado resultado : resultados) {
            // Cualquier error que no sea de negocio (un deadlock de MySQL, por ejemplo) es un fallo del test.
            assertTrue(resultado.cita != null || resultado.error.matches("[a-z_]+"), resultado.error);
        }
        return resultados;
    }

    private ServicioCitas servicio(boolean usarCerrojo) {
        RepositorioCitas lento = new RepositorioCitasLento(new RepositorioCitasJdbc());
        ServicioHorarios horarios = new ServicioHorarios(baseDatos, new RepositorioHorariosJdbc(), lento,
                reloj.comoReloj());
        return new ServicioCitas(baseDatos, lento, new RepositorioUsuariosJdbc(), horarios, reloj.comoReloj(),
                usarCerrojo);
    }

    private long citasActivasDelMartes() {
        return BaseDePruebas.numero(baseDatos,
                "SELECT COUNT(*) FROM citas WHERE fecha = '2026-10-06' AND estado IN ('pendiente', 'confirmada')");
    }

    /**
     * Igual que el repositorio de verdad, pero se para 300 ms después de contar. Todo lo demás
     * lo pasa tal cual al repositorio que envuelve.
     */
    private static final class RepositorioCitasLento implements RepositorioCitas {

        private final RepositorioCitas real;

        RepositorioCitasLento(RepositorioCitas real) {
            this.real = real;
        }

        @Override
        public int contarActivas(Connection conexion, LocalDate fecha) throws SQLException {
            int cuenta = real.contarActivas(conexion, fecha);
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return cuenta;
        }

        @Override
        public long insertar(Connection conexion, Cita cita) throws SQLException {
            return real.insertar(conexion, cita);
        }

        @Override
        public Optional<Cita> buscarPorId(Connection conexion, long id) throws SQLException {
            return real.buscarPorId(conexion, id);
        }

        @Override
        public Map<LocalDate, Integer> contarActivasEntre(Connection conexion, LocalDate desde, LocalDate hasta)
                throws SQLException {
            return real.contarActivasEntre(conexion, desde, hasta);
        }

        @Override
        public Set<LocalTime> horasOcupadas(Connection conexion, LocalDate fecha) throws SQLException {
            return real.horasOcupadas(conexion, fecha);
        }

        @Override
        public List<Cita> listarPorCliente(Connection conexion, long clienteId, LocalDate desde) throws SQLException {
            return real.listarPorCliente(conexion, clienteId, desde);
        }

        @Override
        public List<Cita> listar(Connection conexion, List<EstadoCita> estados, LocalDate desde, LocalDate hasta)
                throws SQLException {
            return real.listar(conexion, estados, desde, hasta);
        }

        @Override
        public boolean actualizar(Connection conexion, Cita cita, EstadoCita estadoAnterior) throws SQLException {
            return real.actualizar(conexion, cita, estadoAnterior);
        }
    }
}
