package com.clandestino.api.servicio;

import com.clandestino.api.BaseDePruebas;
import com.clandestino.api.RelojMovible;
import com.clandestino.api.modelo.EstadoInvitacion;
import com.clandestino.api.modelo.EstadoUsuario;
import com.clandestino.api.modelo.Invitacion;
import com.clandestino.api.modelo.Rol;
import com.clandestino.api.repositorio.BaseDatos;
import com.clandestino.api.repositorio.RepositorioUsuariosJdbc;
import com.clandestino.api.seguridad.Contrasenas;
import com.clandestino.api.seguridad.TokenJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Registro con invitación (UC01), login (UC02), aprobar y bloquear (UC07) e invitaciones (UC06). */
class PruebaServicioUsuarios {

    private BaseDatos baseDatos;
    private RelojMovible reloj;
    private ServicioUsuarios servicio;
    private TokenJwt tokenJwt;
    private long adminId;

    @BeforeEach
    void prepararBase() {
        baseDatos = BaseDePruebas.conectar();
        BaseDePruebas.vaciar(baseDatos);
        adminId = BaseDePruebas.crearAdmin(baseDatos, "barbero", "clave-del-barbero");

        reloj = new RelojMovible(LocalDateTime.of(2026, 10, 5, 10, 0));
        tokenJwt = new TokenJwt("clave-de-pruebas-con-mas-de-32-caracteres", reloj.comoReloj(), Duration.ofDays(7));
        servicio = new ServicioUsuarios(baseDatos, new RepositorioUsuariosJdbc(), new Contrasenas(1000), tokenJwt,
                reloj.comoReloj());
    }

    @Test
    void conUnCodigoBuenoSeCreaUnClientePendienteConSesion() {
        Invitacion invitacion = servicio.generarInvitacion(adminId, "Marcos, el del gimnasio");

        // El código se escribe en minúsculas y con un guion en medio: se acepta igual.
        String escrito = invitacion.codigo().substring(0, 6).toLowerCase() + "-" + invitacion.codigo().substring(6);
        ServicioUsuarios.Sesion sesion = servicio.registrar(escrito, "@Marcos", "611 22 23 33", "una-clave-larga");

        assertEquals("marcos", sesion.usuario().usuario());
        assertEquals("+34611222333", sesion.usuario().telefono());
        assertEquals(Rol.CLIENTE, sesion.usuario().rol());
        assertEquals(EstadoUsuario.PENDIENTE, sesion.usuario().estado());
        assertEquals(sesion.usuario().id(), tokenJwt.validar(sesion.token()));

        Invitacion usada = servicio.invitaciones().get(0);
        assertEquals(EstadoInvitacion.USADA, usada.estado());
        assertEquals("marcos", usada.usadaPorUsuario());
    }

    @Test
    void unCodigoSoloSirveUnaVez() {
        String codigo = servicio.generarInvitacion(adminId, null).codigo();
        servicio.registrar(codigo, "marcos", "611222333", "una-clave-larga");
        falla("invitacion_usada", () -> servicio.registrar(codigo, "lucia", "611222444", "una-clave-larga"));
    }

    @Test
    void unCodigoQueNoExisteOCaducadoNoSirve() {
        falla("invitacion_invalida", () -> servicio.registrar("NOEXISTE2345", "marcos", "611222333", "una-clave-larga"));

        String codigo = servicio.generarInvitacion(adminId, null).codigo();
        reloj.avanzar(Duration.ofDays(7));
        falla("invitacion_caducada", () -> servicio.registrar(codigo, "marcos", "611222333", "una-clave-larga"));
    }

    @Test
    void siElUsuarioYaExisteNoSeGastaLaInvitacion() {
        BaseDePruebas.crearCliente(baseDatos, "marcos", "+34699999999", "aprobado");
        Invitacion invitacion = servicio.generarInvitacion(adminId, null);

        falla("duplicado", () -> servicio.registrar(invitacion.codigo(), "marcos", "611222333", "una-clave-larga"));

        // El rollback deja la invitación como estaba, y se puede usar con otro nombre.
        assertEquals(EstadoInvitacion.PENDIENTE, servicio.invitaciones().get(0).estado());
        assertNotNull(servicio.registrar(invitacion.codigo(), "marcos2", "611222333", "una-clave-larga").token());
    }

    @Test
    void elTelefonoRepetidoTambienDaDuplicado() {
        BaseDePruebas.crearCliente(baseDatos, "lucia", "+34611222333", "aprobado");
        String codigo = servicio.generarInvitacion(adminId, null).codigo();
        falla("duplicado", () -> servicio.registrar(codigo, "marcos", "0034 611 222 333", "una-clave-larga"));
    }

    @Test
    void elLoginAceptaLaContrasenaBuenaYNadaMas() {
        assertNotNull(servicio.iniciarSesion("Barbero", "clave-del-barbero").token());
        falla("credenciales_invalidas", () -> servicio.iniciarSesion("barbero", "otra-clave"));
        // Mismo error si el usuario no existe: no se dice cuál de las dos cosas está mal.
        falla("credenciales_invalidas", () -> servicio.iniciarSesion("nadie", "clave-del-barbero"));
    }

    @Test
    void aprobarYBloquearCambianElEstado() {
        long marcos = BaseDePruebas.crearCliente(baseDatos, "marcos", "+34611222333", "pendiente");

        assertEquals(EstadoUsuario.APROBADO, servicio.aprobar(marcos).estado());
        assertEquals(LocalDateTime.of(2026, 10, 5, 10, 0), servicio.clientes(EstadoUsuario.APROBADO).get(0).aprobadoEn());
        falla("estado_invalido", () -> servicio.aprobar(marcos));

        assertEquals(EstadoUsuario.BLOQUEADO, servicio.bloquear(marcos).estado());
        falla("estado_invalido", () -> servicio.bloquear(marcos));
    }

    @Test
    void alBarberoNoSeLePuedeBloquear() {
        falla("no_encontrado", () -> servicio.bloquear(adminId));
        falla("no_encontrado", () -> servicio.aprobar(999_999));
    }

    @Test
    void laInvitacionCaducaALosSieteDias() {
        Invitacion invitacion = servicio.generarInvitacion(adminId, "  Lucía  ");
        assertEquals("Lucía", invitacion.nota());
        assertEquals(LocalDateTime.of(2026, 10, 12, 10, 0), invitacion.caducaEn());
        falla("datos_invalidos", () -> servicio.generarInvitacion(adminId, "x".repeat(61)));
    }

    private static void falla(String codigoEsperado, Executable accion) {
        ErrorNegocio error = assertThrows(ErrorNegocio.class, accion);
        assertEquals(codigoEsperado, error.getCodigo(), error.getMessage());
    }
}
