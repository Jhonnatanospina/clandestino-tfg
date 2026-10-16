package com.clandestino.app.datos.repositorio;

import com.clandestino.app.datos.remoto.Callback;
import com.clandestino.app.datos.remoto.ClienteApi;
import com.clandestino.app.modelo.Usuario;
import com.clandestino.app.utilidades.SesionAlmacen;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Entrar, registrarse con código y consultar la propia cuenta. Salir no pasa por aquí: el JWT
 * no se guarda en el servidor, así que basta con olvidarlo en el móvil (Pantallas.cerrarSesion).
 */
public class RepositorioAcceso {

    private final ClienteApi api;
    private final SesionAlmacen sesion;

    public RepositorioAcceso(ClienteApi api, SesionAlmacen sesion) {
        this.api = api;
        this.sesion = sesion;
    }

    public boolean haySesion() {
        return sesion.leerToken() != null;
    }

    public void iniciarSesion(String usuario, String clave, Callback<Usuario> callback) {
        api.enviar("POST", "/api/auth/login",
                () -> new JSONObject().put("usuario", usuario).put("password", clave),
                this::guardarSesion, callback);
    }

    public void registrarse(String codigo, String usuario, String telefono, String clave,
                            Callback<Usuario> callback) {
        api.enviar("POST", "/api/auth/registro",
                () -> new JSONObject()
                        .put("codigo", codigo)
                        .put("usuario", usuario)
                        .put("telefono", telefono)
                        .put("password", clave),
                this::guardarSesion, callback);
    }

    /** La cuenta del token guardado. La pantalla de espera la usa para ver si ya la aprobaron. */
    public void perfil(Callback<Usuario> callback) {
        api.enviar("GET", "/api/yo", null, Usuario::desdeJson, callback);
    }

    // Login y registro contestan igual: {"token": "...", "usuario": {...}}. El token se guarda
    // en el hilo de la petición, antes de avisar a la pantalla, para que la siguiente llamada
    // ya lo lleve puesto.
    private Usuario guardarSesion(JSONObject json) throws JSONException {
        sesion.guardarToken(json.getString("token"));
        return Usuario.desdeJson(json.getJSONObject("usuario"));
    }
}
