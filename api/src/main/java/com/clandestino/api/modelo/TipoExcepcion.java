package com.clandestino.api.modelo;

import java.util.Locale;

/**
 * Tipo de una excepción del calendario (RN-02).
 *
 * CERRADO es un día libre o de vacaciones. ABIERTO es un día extra o un día normal con otro
 * horario distinto al de la semana.
 */
public enum TipoExcepcion {
    CERRADO,
    ABIERTO;

    public String enBd() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TipoExcepcion desdeTexto(String texto) {
        return valueOf(texto.toUpperCase(Locale.ROOT));
    }
}
