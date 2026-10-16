package com.clandestino.api.http;

import com.clandestino.api.modelo.Usuario;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/**
 * Lo que hace la API en una ruta concreta (por ejemplo, POST /api/citas).
 *
 * Cuando se llama, el Enrutador ya ha comprobado la ruta, el método, el token, el rol y el
 * estado del usuario. Al manejador solo le queda leer los datos, llamar al servicio y responder.
 * usuario es null en las rutas públicas.
 */
@FunctionalInterface
public interface Manejador {
    void atender(HttpExchange intercambio, Usuario usuario) throws IOException;
}
