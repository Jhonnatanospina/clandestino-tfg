package com.clandestino.api.http;

/** Quién puede llamar a cada ruta. Lo comprueba el Enrutador antes de llamar al manejador. */
public enum Acceso {
    /** Sin token: salud, registro y login. */
    PUBLICO,
    /** Cualquier usuario con token, esté como esté su cuenta. Solo GET /api/yo, para la pantalla de espera. */
    CUALQUIER_SESION,
    /** Cliente aprobado. */
    CLIENTE,
    /** El barbero. */
    ADMIN
}
