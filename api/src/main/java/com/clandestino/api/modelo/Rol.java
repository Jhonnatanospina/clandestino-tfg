package com.clandestino.api.modelo;

import java.util.Locale;

/**
 * Rol de un usuario. Solo hay dos: el barbero (admin) y sus clientes.
 *
 * En la base de datos es un ENUM en minúsculas ('admin', 'cliente'), así que cada enum del
 * modelo tiene los mismos dos métodos para pasar de uno a otro.
 */
public enum Rol {
    ADMIN,
    CLIENTE;

    public String enBd() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Lanza IllegalArgumentException si el texto no es un rol conocido. */
    public static Rol desdeTexto(String texto) {
        return valueOf(texto.toUpperCase(Locale.ROOT));
    }
}
