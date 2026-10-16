package com.clandestino.app.modelo;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Una cuenta, tal como la devuelve la API en el login, en GET /api/yo y en la lista de
 * clientes del barbero. Nunca llega la contraseña ni su huella.
 */
public class Usuario {

    private final long id;
    private final String usuario;
    private final String telefono;
    private final boolean admin;
    private final EstadoUsuario estado;

    public Usuario(long id, String usuario, String telefono, boolean admin, EstadoUsuario estado) {
        this.id = id;
        this.usuario = usuario;
        this.telefono = telefono;
        this.admin = admin;
        this.estado = estado;
    }

    public static Usuario desdeJson(JSONObject json) throws JSONException {
        // La lista de clientes del barbero no trae "rol": todos son clientes.
        boolean admin = "admin".equals(json.optString("rol", "cliente"));
        return new Usuario(json.getLong("id"), json.getString("usuario"), json.getString("telefono"),
                admin, EstadoUsuario.desdeApi(json.getString("estado")));
    }

    public long getId() {
        return id;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getTelefono() {
        return telefono;
    }

    public boolean esAdmin() {
        return admin;
    }

    public EstadoUsuario getEstado() {
        return estado;
    }

    public boolean estaAprobado() {
        return estado == EstadoUsuario.APROBADO;
    }
}
