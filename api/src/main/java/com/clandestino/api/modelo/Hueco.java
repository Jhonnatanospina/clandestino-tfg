package com.clandestino.api.modelo;

import java.time.LocalTime;

/**
 * Una hora del día en la que se puede pedir cita, y si ya la tiene otra persona.
 * No dice quién la tiene: el cliente no debe ver las citas de los demás.
 */
public record Hueco(LocalTime hora, boolean ocupado) {
}
