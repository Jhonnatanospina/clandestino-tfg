package com.clandestino.api.servicio;

import com.clandestino.api.modelo.EstadoInvitacion;
import com.clandestino.api.modelo.EstadoUsuario;
import com.clandestino.api.modelo.Invitacion;
import com.clandestino.api.modelo.Rol;
import com.clandestino.api.modelo.Usuario;
import com.clandestino.api.repositorio.BaseDatos;
import com.clandestino.api.repositorio.RepositorioUsuarios;
import com.clandestino.api.seguridad.Contrasenas;
import com.clandestino.api.seguridad.TokenJwt;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cuentas de usuario: registro con invitación, inicio de sesión, aprobar y bloquear clientes, y
 * los códigos de invitación que genera el barbero.
 */
public class ServicioUsuarios {

    /** Lo que se devuelve al registrarse o al entrar: el token y la cuenta. */
    public record Sesion(String token, Usuario usuario) {
    }

    private static final Pattern FORMATO_USUARIO = Pattern.compile("[a-z0-9_]{3,20}");
    // Formato internacional E.164: un + y entre 8 y 15 cifras, la primera distinta de 0.
    private static final Pattern FORMATO_TELEFONO = Pattern.compile("\\+[1-9][0-9]{7,14}");

    private static final int MINIMO_CONTRASENA = 8;
    private static final int MAXIMO_CONTRASENA = 128;

    // Sin 0, O, 1, I ni L, que se confunden al dictar el código o al leerlo en una pantalla.
    private static final String ALFABETO_CODIGO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LARGO_CODIGO = 12;
    private static final int DIAS_DE_VALIDEZ_INVITACION = 7;
    private static final int MAXIMO_LARGO_NOTA = 60;

    /** El mismo valor por defecto que tiene la columna precio_corte en la tabla. */
    private static final BigDecimal PRECIO_CORTE_POR_DEFECTO = new BigDecimal("15.00");

    private static final SecureRandom azar = new SecureRandom();

    private final BaseDatos baseDatos;
    private final RepositorioUsuarios usuarios;
    private final Contrasenas contrasenas;
    private final TokenJwt tokenJwt;
    private final Reloj reloj;

    // Huella de una contraseña inventada. Ver iniciarSesion: sirve para gastar el mismo tiempo
    // cuando el usuario no existe que cuando la contraseña está mal.
    private final String hashDeRelleno;

    public ServicioUsuarios(BaseDatos baseDatos, RepositorioUsuarios usuarios, Contrasenas contrasenas,
                            TokenJwt tokenJwt, Reloj reloj) {
        this.baseDatos = baseDatos;
        this.usuarios = usuarios;
        this.contrasenas = contrasenas;
        this.tokenJwt = tokenJwt;
        this.reloj = reloj;
        this.hashDeRelleno = contrasenas.hashear("relleno-" + azar.nextLong());
    }

    // ---------------------------------------------------------------------------------------
    // Registro e inicio de sesión
    // ---------------------------------------------------------------------------------------

    /**
     * Da de alta un cliente con un código de invitación (UC01, RN-21). El cliente queda
     * pendiente hasta que el barbero lo apruebe, pero ya sale con sesión para ver la pantalla
     * de espera.
     */
    public Sesion registrar(String codigo, String usuario, String telefono, String contrasena) {
        String codigoLimpio = normalizarCodigo(codigo);
        String usuarioLimpio = comprobarUsuario(normalizarUsuario(usuario));
        String telefonoLimpio = comprobarTelefono(normalizarTelefono(telefono));
        comprobarContrasena(contrasena);

        // La huella se calcula ANTES de abrir la transacción: tarda casi un segundo y, si la
        // calculáramos dentro, la fila de la invitación estaría bloqueada todo ese rato.
        String hash = contrasenas.hashear(contrasena);
        LocalDateTime ahora = reloj.ahora();

        Usuario creado = baseDatos.enTransaccion(conexion -> {
            // FOR UPDATE: si dos personas usan el mismo código a la vez, la segunda espera
            // aquí a que la primera termine, y entonces ya lo ve como "usada".
            Invitacion invitacion = usuarios.buscarInvitacionParaUsar(conexion, codigoLimpio)
                    .orElseThrow(() -> new ErrorNegocio("invitacion_invalida", 400, "Ese código de invitación no existe."));
            if (invitacion.estado() == EstadoInvitacion.USADA) {
                throw new ErrorNegocio("invitacion_usada", 409, "Ese código de invitación ya se ha usado.");
            }
            if (invitacion.estaCaducada(ahora)) {
                throw new ErrorNegocio("invitacion_caducada", 409, "Ese código de invitación ha caducado. Pide otro.");
            }

            Usuario nuevo = new Usuario(0, usuarioLimpio, telefonoLimpio, hash, Rol.CLIENTE,
                    EstadoUsuario.PENDIENTE, PRECIO_CORTE_POR_DEFECTO, ahora, null);
            long id;
            try {
                id = usuarios.insertar(conexion, nuevo);
            } catch (SQLException e) {
                if (BaseDatos.esDuplicado(e)) {
                    // No decimos si lo repetido es el usuario o el teléfono, para no confirmar
                    // a nadie qué teléfonos están registrados. Al lanzar el error se hace
                    // rollback y la invitación sigue sin usar.
                    throw new ErrorNegocio("duplicado", 409, "Ese usuario o ese teléfono ya están registrados.");
                }
                throw e;
            }
            usuarios.marcarUsada(conexion, invitacion.id(), id);
            return usuarios.buscarPorId(conexion, id).orElseThrow();
        });
        return new Sesion(tokenJwt.emitir(creado.id()), creado);
    }

    /**
     * Inicio de sesión (UC02). Se da token también a los pendientes y a los bloqueados: con él
     * solo pueden consultar su estado (GET /api/yo) para que la app les enseñe la pantalla A3.
     */
    public Sesion iniciarSesion(String usuario, String contrasena) {
        String usuarioLimpio = normalizarUsuario(usuario);
        Usuario encontrado = baseDatos.consultar(conexion -> usuarios.buscarPorUsuario(conexion, usuarioLimpio))
                .orElse(null);

        if (encontrado == null) {
            // OJO: aunque el usuario no exista calculamos una huella igualmente. Si no, la
            // respuesta llegaría casi un segundo antes y midiendo el tiempo se podría saber
            // qué usuarios existen.
            contrasenas.verificar(contrasena, hashDeRelleno);
            throw credencialesInvalidas();
        }
        if (!contrasenas.verificar(contrasena, encontrado.passwordHash())) {
            throw credencialesInvalidas();
        }
        return new Sesion(tokenJwt.emitir(encontrado.id()), encontrado);
    }

    private static ErrorNegocio credencialesInvalidas() {
        return new ErrorNegocio("credenciales_invalidas", 401, "Usuario o contraseña incorrectos.");
    }

    // ---------------------------------------------------------------------------------------
    // Clientes (D4)
    // ---------------------------------------------------------------------------------------

    public List<Usuario> clientes(EstadoUsuario estado) {
        return baseDatos.consultar(conexion -> usuarios.listarClientes(conexion, estado));
    }

    /** Pendiente o bloqueado pasa a aprobado (RN-23). Se guarda cuándo se aprobó. */
    public Usuario aprobar(long clienteId) {
        return cambiarEstado(clienteId, EstadoUsuario.APROBADO, "Ese cliente ya está aprobado.");
    }

    /** Pendiente o aprobado pasa a bloqueado. Sus citas no se tocan (DP-13). */
    public Usuario bloquear(long clienteId) {
        return cambiarEstado(clienteId, EstadoUsuario.BLOQUEADO, "Ese cliente ya está bloqueado.");
    }

    private Usuario cambiarEstado(long clienteId, EstadoUsuario estadoNuevo, String mensajeSiYaEsta) {
        return baseDatos.enTransaccion(conexion -> {
            Usuario cliente = usuarios.buscarPorId(conexion, clienteId).orElse(null);
            // Al barbero no se le puede aprobar ni bloquear: para esta ruta "no existe".
            if (cliente == null || cliente.esAdmin()) {
                throw ErrorNegocio.noEncontrado("Ese cliente no existe.");
            }
            if (cliente.estado() == estadoNuevo) {
                throw ErrorNegocio.estadoInvalido(mensajeSiYaEsta);
            }
            LocalDateTime aprobadoEn = estadoNuevo == EstadoUsuario.APROBADO ? reloj.ahora() : null;
            if (!usuarios.cambiarEstado(conexion, clienteId, cliente.estado(), estadoNuevo, aprobadoEn)) {
                throw ErrorNegocio.estadoInvalido("El cliente ha cambiado mientras tanto. Vuelve a cargarlo.");
            }
            return usuarios.buscarPorId(conexion, clienteId).orElseThrow();
        });
    }

    // ---------------------------------------------------------------------------------------
    // Invitaciones (D4, UC06)
    // ---------------------------------------------------------------------------------------

    public List<Invitacion> invitaciones() {
        return baseDatos.consultar(usuarios::listarInvitaciones);
    }

    /** Crea un código nuevo que caduca en 7 días (RN-20). La nota es para que el barbero se acuerde de a quién se lo dio. */
    public Invitacion generarInvitacion(long adminId, String nota) {
        String notaLimpia = (nota == null || nota.isBlank()) ? null : nota.strip();
        if (notaLimpia != null && notaLimpia.length() > MAXIMO_LARGO_NOTA) {
            throw ErrorNegocio.datosInvalidos("La nota no puede pasar de " + MAXIMO_LARGO_NOTA + " caracteres.");
        }
        LocalDateTime ahora = reloj.ahora();
        return baseDatos.enTransaccion(conexion -> {
            // Con 31^12 combinaciones que se repita un código es casi imposible, pero si pasa,
            // el índice único lo detecta y probamos con otro.
            for (int intento = 1; ; intento++) {
                Invitacion nueva = new Invitacion(0, generarCodigo(), notaLimpia, EstadoInvitacion.PENDIENTE,
                        ahora.plusDays(DIAS_DE_VALIDEZ_INVITACION), adminId, null, null, ahora);
                try {
                    long id = usuarios.insertarInvitacion(conexion, nueva);
                    return usuarios.buscarInvitacionPorId(conexion, id).orElseThrow();
                } catch (SQLException e) {
                    if (!BaseDatos.esDuplicado(e) || intento == 3) {
                        throw e;
                    }
                }
            }
        });
    }

    static String generarCodigo() {
        StringBuilder codigo = new StringBuilder(LARGO_CODIGO);
        for (int i = 0; i < LARGO_CODIGO; i++) {
            codigo.append(ALFABETO_CODIGO.charAt(azar.nextInt(ALFABETO_CODIGO.length())));
        }
        return codigo.toString();
    }

    // ---------------------------------------------------------------------------------------
    // Normalizar y validar lo que escribe la persona
    // ---------------------------------------------------------------------------------------

    /** "  @Marcos_R " pasa a "marcos_r": sin espacios, en minúsculas y sin la @ del principio. */
    public static String normalizarUsuario(String usuario) {
        String limpio = usuario == null ? "" : usuario.strip().toLowerCase(Locale.ROOT);
        return limpio.startsWith("@") ? limpio.substring(1) : limpio;
    }

    /**
     * "611 22 23 33" pasa a "+34611222333". Quitamos espacios, guiones, puntos y paréntesis.
     * Si empieza por 00 lo cambiamos por + y, si son 9 cifras sin prefijo, suponemos que es
     * un móvil español. No comprobamos que el número exista de verdad (DP-09).
     */
    public static String normalizarTelefono(String telefono) {
        String limpio = telefono == null ? "" : telefono.replaceAll("[\\s.\\-()]", "");
        if (limpio.startsWith("00")) {
            limpio = "+" + limpio.substring(2);
        }
        if (!limpio.startsWith("+") && limpio.matches("[0-9]{9}")) {
            limpio = "+34" + limpio;
        }
        return limpio;
    }

    /** "k7m2-qx9p td4r" pasa a "K7M2QX9PTD4R": al comparar se ignoran mayúsculas, espacios y guiones. */
    public static String normalizarCodigo(String codigo) {
        String limpio = codigo == null ? "" : codigo.replaceAll("[\\s\\-]", "").toUpperCase(Locale.ROOT);
        if (limpio.isEmpty() || limpio.length() > 32) {
            throw ErrorNegocio.datosInvalidos("Escribe el código de invitación que te ha dado el barbero.");
        }
        return limpio;
    }

    public static String comprobarUsuario(String usuario) {
        if (!FORMATO_USUARIO.matcher(usuario).matches()) {
            throw ErrorNegocio.datosInvalidos("El usuario tiene que tener de 3 a 20 letras minúsculas, números o _.");
        }
        return usuario;
    }

    public static String comprobarTelefono(String telefono) {
        if (!FORMATO_TELEFONO.matcher(telefono).matches()) {
            throw ErrorNegocio.datosInvalidos("El teléfono no es válido.");
        }
        return telefono;
    }

    public static void comprobarContrasena(String contrasena) {
        if (contrasena == null || contrasena.length() < MINIMO_CONTRASENA || contrasena.length() > MAXIMO_CONTRASENA) {
            throw ErrorNegocio.datosInvalidos("La contraseña tiene que tener entre " + MINIMO_CONTRASENA
                    + " y " + MAXIMO_CONTRASENA + " caracteres.");
        }
    }
}
