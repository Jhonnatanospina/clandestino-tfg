package com.clandestino.api.modelo;

import java.time.LocalDateTime;

/**
 * Código de invitación de un solo uso que el barbero le pasa a un cliente nuevo (UC06).
 *
 * usadaPor y usadaPorUsuario son null mientras nadie la ha usado. El nombre del usuario no está
 * en la tabla invitaciones: lo trae la consulta con un LEFT JOIN para que el barbero vea quién
 * la usó sin hacer otra petición.
 */
public record Invitacion(
        long id,
        String codigo,
        String nota,
        EstadoInvitacion estado,
        LocalDateTime caducaEn,
        long creadaPor,
        Long usadaPor,
        String usadaPorUsuario,
        LocalDateTime creadaEn) {

    /**
     * Caducada = sigue pendiente pero ya pasó su fecha de caducidad (RN-22). Una invitación
     * usada nunca se considera caducada, aunque haya pasado la fecha: ya cumplió su función.
     */
    public boolean estaCaducada(LocalDateTime ahora) {
        return estado == EstadoInvitacion.PENDIENTE && !caducaEn.isAfter(ahora);
    }
}
