package com.clandestino.api.seguridad;

import com.clandestino.api.modelo.Usuario;
import com.clandestino.api.repositorio.BaseDatos;
import com.clandestino.api.repositorio.RepositorioUsuarios;
import com.clandestino.api.servicio.ErrorNegocio;

/**
 * Averigua quién hace la petición a partir de la cabecera "Authorization: Bearer <token>".
 *
 * Primero valida el token (firma y caducidad) y después busca al usuario en la base de datos.
 * Esa segunda parte es la importante: el rol y el estado se leen de MySQL en cada petición y
 * no se fían de lo que diga el token. Así, si el barbero bloquea a un cliente, en su siguiente
 * petición ya recibe 403, aunque su token dure todavía 7 días.
 *
 * Cuesta una consulta por petición, pero es por clave primaria y tarda muy poco.
 */
public class FiltroAutenticacion {

    private static final String PREFIJO = "Bearer ";

    private final TokenJwt tokenJwt;
    private final BaseDatos baseDatos;
    private final RepositorioUsuarios usuarios;

    public FiltroAutenticacion(TokenJwt tokenJwt, BaseDatos baseDatos, RepositorioUsuarios usuarios) {
        this.tokenJwt = tokenJwt;
        this.baseDatos = baseDatos;
        this.usuarios = usuarios;
    }

    /**
     * Devuelve el usuario dueño del token o lanza un 401:
     * "no_autenticado" si no hay cabecera, "token_invalido" si el token no vale o si su
     * usuario ya no existe (por ejemplo, porque se borró).
     */
    public Usuario autenticar(String cabeceraAuthorization) {
        if (cabeceraAuthorization == null || !cabeceraAuthorization.startsWith(PREFIJO)) {
            throw new ErrorNegocio("no_autenticado", 401, "Tienes que iniciar sesión.")
                    .conCabecera("WWW-Authenticate", "Bearer");
        }
        String token = cabeceraAuthorization.substring(PREFIJO.length()).trim();
        long usuarioId = tokenJwt.validar(token);

        return baseDatos.consultar(conexion -> usuarios.buscarPorId(conexion, usuarioId))
                .orElseThrow(() -> new ErrorNegocio("token_invalido", 401,
                        "La sesión no es válida o ha caducado. Vuelve a entrar."));
    }
}
