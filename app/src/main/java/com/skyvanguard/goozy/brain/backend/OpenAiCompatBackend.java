package com.skyvanguard.goozy.brain.backend;

import com.skyvanguard.goozy.brain.LlmBackend;
import com.skyvanguard.goozy.brain.LlmCallback;
import com.skyvanguard.goozy.brain.LlmException;
import com.skyvanguard.goozy.brain.LlmRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cualquier servidor que hable el formato de chat de OpenAI: Ollama y LM Studio
 * en la red local, o servicios como OpenRouter, Groq y Gemini (por su endpoint
 * compatible).
 */
public class OpenAiCompatBackend implements LlmBackend {
    public static final String ID = "openai_compat";

    private static final String CHAT_PATH = "/chat/completions";
    private static final String DONE_MARKER = "[DONE]";
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int MAX_ERROR_BODY_BYTES = 2_000;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_REDIRECT_MIN = 300;
    private static final int HTTP_REDIRECT_MAX = 399;
    private static final int HTTP_OK_MIN = 200;
    private static final int HTTP_OK_MAX = 299;

    private final String id;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "goose-brain-http");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean isStrictModeSupported = true;
    private volatile HttpURLConnection activeConnection;
    private volatile AtomicBoolean activeCancelFlag;

    /**
     * @param baseUrl raíz de la API, por ejemplo http://192.168.0.10:11434/v1
     * @param model   nombre del modelo tal como lo conoce el servidor
     * @param apiKey  clave; puede ir vacía para servidores locales
     */
    public OpenAiCompatBackend(String baseUrl, String model, String apiKey) {
        this(ID, baseUrl, model, apiKey);
    }

    /**
     * @param id identificador con el que se presenta (el mismo motor sirve para
     *           varias opciones de la pantalla de ajustes)
     */
    public OpenAiCompatBackend(String id, String baseUrl, String model, String apiKey) {
        this.id = id;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.model = model != null ? model.trim() : "";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    static String normalizeBaseUrl(String url) {
        if (url == null) return "";
        String result = url.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.endsWith(CHAT_PATH)) {
            result = result.substring(0, result.length() - CHAT_PATH.length());
        }
        return result;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    @Override
    public boolean isAvailable() {
        if (baseUrl.isEmpty() || model.isEmpty()) return false;
        if (baseUrl.startsWith("https://")) return true;
        // Sin cifrado solo hacia la red local: el estado de la mascota y lo que
        // escribe el humano no viajan en claro por internet.
        return baseUrl.startsWith("http://") && LocalNetwork.isLocalUrl(baseUrl);
    }

    @Override
    public void generate(LlmRequest request, LlmCallback callback) {
        if (!isAvailable()) {
            callback.onError(new LlmException(LlmException.Kind.UNAVAILABLE,
                    "Falta la URL del servidor o el nombre del modelo"));
            return;
        }
        AtomicBoolean cancelFlag = new AtomicBoolean(false);
        activeCancelFlag = cancelFlag;
        executor.execute(() -> runRequest(request, callback, cancelFlag));
    }

    @Override
    public void cancel() {
        AtomicBoolean cancelFlag = activeCancelFlag;
        if (cancelFlag != null) {
            cancelFlag.set(true);
        }
        HttpURLConnection connection = activeConnection;
        if (connection != null) {
            connection.disconnect();
        }
    }

    @Override
    public void release() {
        cancel();
    }

    private void runRequest(LlmRequest request, LlmCallback callback, AtomicBoolean cancelFlag) {
        HttpURLConnection connection = null;
        try {
            if (cancelFlag.get()) throw cancelled();

            connection = send(request, isStrictModeSupported);
            int status = connection.getResponseCode();
            if (status == HTTP_BAD_REQUEST && isStrictModeSupported) {
                // Este servidor no entiende el esquema estricto o el control de
                // razonamiento: se recuerda y se reintenta con el formato básico.
                isStrictModeSupported = false;
                connection.disconnect();
                connection = send(request, false);
                status = connection.getResponseCode();
            }
            if (status >= HTTP_REDIRECT_MIN && status <= HTTP_REDIRECT_MAX) {
                throw new LlmException(LlmException.Kind.BAD_RESPONSE,
                        "El servidor quiso redirigir el pedido (HTTP " + status
                                + "); configurá la URL final");
            }
            if (status < HTTP_OK_MIN || status > HTTP_OK_MAX) {
                throw errorForStatus(status, readLimited(connection.getErrorStream()));
            }

            String text = readStream(connection, callback, cancelFlag);
            if (cancelFlag.get()) throw cancelled();
            if (text.trim().isEmpty()) {
                throw new LlmException(LlmException.Kind.BAD_RESPONSE,
                        "El servidor respondió sin texto");
            }
            callback.onDone(text);
        } catch (LlmException e) {
            callback.onError(e);
        } catch (SocketTimeoutException e) {
            callback.onError(new LlmException(LlmException.Kind.NETWORK,
                    "El servidor tardó demasiado en responder", e));
        } catch (IOException e) {
            callback.onError(cancelFlag.get() ? cancelled()
                    : new LlmException(LlmException.Kind.NETWORK,
                    "No se pudo conectar con " + baseUrl, e));
        } catch (JSONException e) {
            callback.onError(new LlmException(LlmException.Kind.BAD_RESPONSE,
                    "Respuesta con formato inesperado", e));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            activeConnection = null;
        }
    }

    private HttpURLConnection send(LlmRequest request, boolean isStrict)
            throws IOException, LlmException, JSONException {
        HttpURLConnection connection = open();
        activeConnection = connection;
        byte[] body = buildBody(request, isStrict).toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body);
        }
        return connection;
    }

    private static LlmException cancelled() {
        return new LlmException(LlmException.Kind.CANCELLED, "Generación cancelada");
    }

    private HttpURLConnection open() throws IOException, LlmException {
        URL url;
        try {
            url = new URL(baseUrl + CHAT_PATH);
        } catch (MalformedURLException e) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE,
                    "La URL del servidor no es válida", e);
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        // Una redirección podría llevar el pedido (y la clave) a un servidor de
        // internet en texto plano, saltándose el control de isAvailable()
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "text/event-stream");
        if (!apiKey.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        return connection;
    }

    JSONObject buildBody(LlmRequest request, boolean isStrict) throws JSONException {
        JSONArray messages = new JSONArray();
        if (!request.systemPrompt.isEmpty()) {
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", request.systemPrompt));
        }
        messages.put(new JSONObject()
                .put("role", "user")
                .put("content", request.userPrompt));

        JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("stream", true)
                .put("max_tokens", request.maxTokens)
                .put("temperature", (double) request.temperature);
        if (isStrict) {
            // Pensar en voz alta gasta los tokens de la respuesta y no aporta
            // nada a una frase de ganso
            body.put("reasoning_effort", "none");
        }
        if (isStrict && !request.jsonSchema.isEmpty()) {
            body.put("response_format", new JSONObject()
                    .put("type", "json_schema")
                    .put("json_schema", new JSONObject()
                            .put("name", "goose_intent")
                            .put("strict", true)
                            .put("schema", new JSONObject(request.jsonSchema))));
        } else if (request.isJsonExpected) {
            body.put("response_format", new JSONObject().put("type", "json_object"));
        }
        return body;
    }

    private String readStream(HttpURLConnection connection, LlmCallback callback,
                              AtomicBoolean cancelFlag) throws IOException, JSONException {
        StringBuilder full = new StringBuilder();
        String contentType = connection.getContentType();
        boolean isEventStream = contentType != null && contentType.contains("text/event-stream");

        try (InputStream in = connection.getInputStream()) {
            if (!isEventStream) {
                // Servidor que ignoró "stream": respuesta completa en un solo JSON
                JSONObject root = new JSONObject(readAll(in));
                full.append(extractMessageContent(root));
                return full.toString();
            }

            final JSONException[] parseError = new JSONException[1];
            SseReader.read(in, data -> {
                if (cancelFlag.get() || DONE_MARKER.equals(data)) return false;
                try {
                    String token = extractDeltaContent(new JSONObject(data));
                    if (!token.isEmpty()) {
                        full.append(token);
                        callback.onToken(token);
                    }
                } catch (JSONException e) {
                    parseError[0] = e;
                    return false;
                }
                return true;
            });
            if (parseError[0] != null && full.length() == 0) {
                throw parseError[0];
            }
        }
        return full.toString();
    }

    static String extractDeltaContent(JSONObject chunk) {
        JSONArray choices = chunk.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return "";
        JSONObject choice = choices.optJSONObject(0);
        if (choice == null) return "";
        JSONObject delta = choice.optJSONObject("delta");
        if (delta == null || delta.isNull("content")) return "";
        return delta.optString("content", "");
    }

    static String extractMessageContent(JSONObject root) {
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return "";
        JSONObject choice = choices.optJSONObject(0);
        if (choice == null) return "";
        JSONObject message = choice.optJSONObject("message");
        if (message == null || message.isNull("content")) return "";
        return message.optString("content", "");
    }

    private static LlmException errorForStatus(int status, String body) {
        String detail = "HTTP " + status + (body.isEmpty() ? "" : ": " + body);
        if (status == HTTP_UNAUTHORIZED || status == HTTP_FORBIDDEN) {
            return new LlmException(LlmException.Kind.AUTH, "Clave rechazada (" + detail + ")");
        }
        if (status == HTTP_TOO_MANY_REQUESTS) {
            return new LlmException(LlmException.Kind.RATE_LIMITED,
                    "Límite de uso alcanzado (" + detail + ")");
        }
        return new LlmException(LlmException.Kind.BAD_RESPONSE, detail);
    }

    private static String readLimited(InputStream stream) {
        if (stream == null) return "";
        try (InputStream in = stream) {
            byte[] buffer = new byte[MAX_ERROR_BODY_BYTES];
            int total = 0;
            while (total < buffer.length) {
                int read = in.read(buffer, total, buffer.length - total);
                if (read < 0) break;
                total += read;
            }
            return new String(buffer, 0, total, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
