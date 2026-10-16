package com.clandestino.api.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una fila de la tabla usuarios: el barbero o uno de sus clientes.
 *
 * Las clases del modelo son "records" (Java 16 en adelante): clases inmutables en las que Java
 * genera solo el constructor, los getters (usuario(), rol()...), equals, hashCode y toString.
 * Para datos que solo viajan de la base de datos al JSON nos ahorra mucho código repetido.
 *
 * OJO: toString() incluiría el hash de la contraseña y el teléfono, así que un Usuario no se
 * escribe nunca entero en el registro del servidor.
 */
public record Usuario(
        long id,
        String usuario,
        String telefono,
        String passwordHash,
        Rol rol,
        EstadoUsuario estado,
        BigDecimal precioCorte,
        LocalDateTime creadoEn,
        LocalDateTime aprobadoEn) {

    public boolean esAdmin() {
        return rol == Rol.ADMIN;
    }

    public boolean estaAprobado() {
        return estado == EstadoUsuario.APROBADO;
    }
}
