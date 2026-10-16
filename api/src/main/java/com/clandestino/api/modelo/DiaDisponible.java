package com.clandestino.api.modelo;

import java.time.LocalDate;

/** Una fecha del calendario del cliente con su estado (libre, completo, vacaciones o cerrado). */
public record DiaDisponible(LocalDate fecha, EstadoDia estado) {
}
