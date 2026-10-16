package com.clandestino.app.datos.repositorio;

import com.clandestino.app.datos.remoto.Callback;
import com.clandestino.app.datos.remoto.ClienteApi;
import com.clandestino.app.modelo.Invitacion;
import com.clandestino.app.modelo.Usuario;

import org.json.JSONObject;

import java.util.List;

/** Los clientes del barbero y los códigos de invitación con los que entran. */
public class RepositorioClientes {

    private final ClienteApi api;

    public RepositorioClientes(ClienteApi api) {
        this.api = api;
    }

    public void clientes(Callback<List<Usuario>> callback) {
        api.enviar("GET", "/api/admin/clientes", null,
                ClienteApi.lista("clientes", Usuario::desdeJson), callback);
    }

    public void aprobar(long id, Callback<Usuario> callback) {
        api.enviar("POST", "/api/admin/clientes/" + id + "/aprobar", null, Usuario::desdeJson, callback);
    }

    public void bloquear(long id, Callback<Usuario> callback) {
        api.enviar("POST", "/api/admin/clientes/" + id + "/bloquear", null, Usuario::desdeJson, callback);
    }

    public void invitaciones(Callback<List<Invitacion>> callback) {
        api.enviar("GET", "/api/admin/invitaciones", null,
                ClienteApi.lista("invitaciones", Invitacion::desdeJson), callback);
    }

    /** @param nota a quién se le da el código, para acordarse; puede ir vacía */
    public void generarInvitacion(String nota, Callback<Invitacion> callback) {
        api.enviar("POST", "/api/admin/invitaciones", () -> {
            JSONObject cuerpo = new JSONObject();
            if (!nota.trim().isEmpty()) {
                cuerpo.put("nota", nota.trim());
            }
            return cuerpo;
        }, Invitacion::desdeJson, callback);
    }
}
