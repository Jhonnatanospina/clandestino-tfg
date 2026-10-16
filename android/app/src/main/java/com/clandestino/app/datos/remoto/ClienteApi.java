package com.clandestino.app.datos.remoto;

import android.os.Handler;
import android.os.Looper;

import com.clandestino.app.utilidades.SesionAlmacen;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Habla con la API REST. Cada petición va en su propio hilo, porque Android no deja usar la
 * red en el hilo principal (lanza NetworkOnMainThreadException), y la respuesta vuelve al hilo
 * principal con un Handler, que es el único que puede tocar las vistas. Es el esquema
 * Thread + Handler de la unidad 9 de PMDM.
 *
 * Usamos HttpURLConnection, que viene con Android, en lugar de Retrofit u OkHttp: son unas pocas
 * rutas con JSON sencillo y así no hay ninguna librería de red que explicar.
 */
public class ClienteApi {

    /**
     * Prepara el JSON que se envía. Es una lambda y no un JSONObject ya hecho porque put()
     * declara JSONException: así se monta dentro del hilo de la petición y cada repositorio
     * no tiene que llenarse de try/catch.
     */
    public interface Cuerpo {
        JSONObject crear() throws JSONException;
    }

    /** Convierte el JSON de la respuesta en el objeto que espera quien llama. */
    public interface Conversor<T> {
        T convertir(JSONObject json) throws JSONException;
    }

    // Los tiempos de espera del plan (5 s para conectar y 10 s para leer). Sin ellos, si el PC
    // no responde, la petición se queda colgada minutos y la pantalla parece congelada.
    private static final int ESPERA_CONEXION_MS = 5_000;
    private static final int ESPERA_LECTURA_MS = 10_000;

    private final String urlBase;
    private final SesionAlmacen sesion;
    private final Handler principal = new Handler(Looper.getMainLooper());

    public ClienteApi(String urlBase, SesionAlmacen sesion) {
        this.urlBase = urlBase;
        this.sesion = sesion;
    }

    /**
     * Conversor para las respuestas que meten la lista dentro de un campo, como
     * {"citas": [...]}. Lo usan casi todos los repositorios.
     */
    public static <T> Conversor<List<T>> lista(String campo, Conversor<T> elemento) {
        return json -> {
            JSONArray array = json.getJSONArray(campo);
            List<T> lista = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                lista.add(elemento.convertir(array.getJSONObject(i)));
            }
            return lista;
        };
    }

    /**
     * Lanza la petición en segundo plano. El conversor también se ejecuta en ese hilo, así el
     * hilo principal solo recibe el resultado ya preparado.
     *
     * @param cuerpo lo que se envía, o null si la petición no lleva cuerpo
     */
    public <T> void enviar(String metodo, String ruta, Cuerpo cuerpo,
                           Conversor<T> conversor, Callback<T> callback) {
        new Thread(() -> {
            try {
                JSONObject json = cuerpo == null ? null : cuerpo.crear();
                T datos = conversor.convertir(ejecutar(metodo, ruta, json));
                principal.post(() -> callback.onExito(datos));
            } catch (ErrorApi error) {
                principal.post(() -> callback.onError(error));
            } catch (JSONException | RuntimeException e) {
                // RuntimeException cubre una fecha o un estado que no se pueden leer
                // (DateTimeParseException, IllegalArgumentException del valueOf del enum).
                // Sin este catch el hilo moriría y la pantalla se quedaría cargando.
                ErrorApi error = new ErrorApi(0, "respuesta_invalida",
                        "El servidor ha respondido algo que la app no entiende.");
                principal.post(() -> callback.onError(error));
            }
        }, "peticion " + metodo + " " + ruta).start();
    }

    private JSONObject ejecutar(String metodo, String ruta, JSONObject cuerpo)
            throws ErrorApi, JSONException {
        HttpURLConnection conexion = null;
        try {
            conexion = abrirConexion(metodo, ruta);
            if (cuerpo != null) {
                conexion.setDoOutput(true);
                try (OutputStream salida = conexion.getOutputStream()) {
                    salida.write(cuerpo.toString().getBytes(StandardCharsets.UTF_8));
                }
            }

            int estado = conexion.getResponseCode();
            if (estado == HttpURLConnection.HTTP_NO_CONTENT) {
                return new JSONObject();
            }
            // OJO: con un 4xx o 5xx getInputStream() lanza una excepción. El cuerpo con el
            // error está en getErrorStream().
            if (estado >= 400) {
                throw leerError(estado, conexion.getErrorStream());
            }
            return new JSONObject(leerTexto(conexion.getInputStream()));
        } catch (IOException e) {
            throw new ErrorApi(0, "sin_conexion",
                    "No se puede conectar con el servidor. Comprueba que la API está arrancada.");
        } finally {
            if (conexion != null) {
                conexion.disconnect();
            }
        }
    }

    private HttpURLConnection abrirConexion(String metodo, String ruta) throws IOException {
        HttpURLConnection conexion = (HttpURLConnection) new URL(urlBase + ruta).openConnection();
        conexion.setRequestMethod(metodo);
        conexion.setConnectTimeout(ESPERA_CONEXION_MS);
        conexion.setReadTimeout(ESPERA_LECTURA_MS);
        conexion.setRequestProperty("Accept", "application/json");
        conexion.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        String token = sesion.leerToken();
        if (token != null) {
            conexion.setRequestProperty("Authorization", "Bearer " + token);
        }
        return conexion;
    }

    /** La API siempre contesta los errores con {"error": "codigo", "mensaje": "texto"}. */
    private ErrorApi leerError(int estado, InputStream flujo) {
        try {
            JSONObject json = new JSONObject(leerTexto(flujo));
            return new ErrorApi(estado, json.getString("error"), json.getString("mensaje"));
        } catch (IOException | JSONException | NullPointerException e) {
            // No ha llegado el JSON esperado (por ejemplo, otro programa escucha en ese puerto).
            return new ErrorApi(estado, "error_desconocido", "El servidor ha devuelto un error " + estado + ".");
        }
    }

    private static String leerTexto(InputStream flujo) throws IOException {
        StringBuilder texto = new StringBuilder();
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(flujo, StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                texto.append(linea);
            }
        }
        return texto.toString();
    }
}
