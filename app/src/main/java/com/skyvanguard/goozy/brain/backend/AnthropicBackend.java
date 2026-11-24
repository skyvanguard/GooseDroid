package com.skyvanguard.goozy.brain.backend;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.skyvanguard.goozy.brain.LlmBackend;
import com.skyvanguard.goozy.brain.LlmCallback;
import com.skyvanguard.goozy.brain.LlmException;
import com.skyvanguard.goozy.brain.LlmRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Claude, con el SDK oficial de Anthropic.
 *
 * Pide la respuesta con el esquema JSON del ganso (salida estructurada) y con
 * esfuerzo bajo: una frase de mascota no necesita pensar mucho. Si Claude
 * rechaza el pedido, el servidor reintenta con otro modelo (respaldo del lado
 * del servidor) y, si igual se rechaza, el cerebro usa las plantillas.
 */
public class AnthropicBackend implements LlmBackend {
    public static final String ID = "anthropic";
    public static final String DEFAULT_MODEL = "claude-opus-5";

    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    private static final String FALLBACK_MODE = "default";
    /**
     * El pensamiento adaptativo cuenta dentro de max_tokens: con el tope chico
     * del ganso la respuesta saldría cortada.
     */
    private static final long MIN_MAX_TOKENS = 4096L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_RETRIES = 2;

    private final String model;
    private final String apiKey;
    private final String baseUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "goose-brain-claude");
        thread.setDaemon(true);
        return thread;
    });

    private AnthropicClient client;
    private volatile AtomicBoolean activeCancelFlag;

    public AnthropicBackend(String model, String apiKey) {
        this(model, apiKey, null);
    }

    /**
     * @param baseUrl raíz de la API; null para la de Anthropic (se cambia en tests)
     */
    AnthropicBackend(String model, String apiKey, String baseUrl) {
        String trimmedModel = model != null ? model.trim() : "";
        this.model = trimmedModel.isEmpty() ? DEFAULT_MODEL : trimmedModel;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.baseUrl = baseUrl;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    @Override
    public boolean isAvailable() {
        return !apiKey.isEmpty();
    }

    @Override
    public void generate(LlmRequest request, LlmCallback callback) {
        if (!isAvailable()) {
            callback.onError(new LlmException(LlmException.Kind.UNAVAILABLE,
                    "Falta la clave de API de Anthropic"));
            return;
        }
        AtomicBoolean cancelFlag = new AtomicBoolean(false);
        activeCancelFlag = cancelFlag;
        executor.execute(() -> run(request, callback, cancelFlag));
    }

    /**
     * El SDK no permite interrumpir un pedido en curso: se marca como cancelado
     * y la respuesta, cuando llega, se descarta.
     */
    @Override
    public void cancel() {
        AtomicBoolean cancelFlag = activeCancelFlag;
        if (cancelFlag != null) {
            cancelFlag.set(true);
        }
    }

    @Override
    public void release() {
        cancel();
        executor.execute(() -> {
            if (client != null) {
                client.close();
                client = null;
            }
        });
    }

    private void run(LlmRequest request, LlmCallback callback, AtomicBoolean cancelFlag) {
        try {
            if (cancelFlag.get()) throw cancelled();
            BetaMessage message = getClient().beta().messages().create(buildParams(request));
            if (cancelFlag.get()) throw cancelled();
            callback.onDone(extractText(message));
        } catch (LlmException e) {
            callback.onError(e);
        } catch (UnauthorizedException | PermissionDeniedException e) {
            callback.onError(new LlmException(LlmException.Kind.AUTH,
                    "Anthropic rechazó la clave (" + e.statusCode() + ")", e));
        } catch (RateLimitException e) {
            callback.onError(new LlmException(LlmException.Kind.RATE_LIMITED,
                    "Límite de uso de Anthropic alcanzado", e));
        } catch (BadRequestException e) {
            callback.onError(new LlmException(LlmException.Kind.BAD_RESPONSE,
                    "Pedido rechazado por Anthropic: " + e.getMessage(), e));
        } catch (AnthropicServiceException e) {
            callback.onError(new LlmException(LlmException.Kind.BAD_RESPONSE,
                    "Error de Anthropic (" + e.statusCode() + ")", e));
        } catch (AnthropicIoException e) {
            callback.onError(new LlmException(LlmException.Kind.NETWORK,
                    "No se pudo conectar con Anthropic", e));
        } catch (JSONException e) {
            callback.onError(new LlmException(LlmException.Kind.BAD_RESPONSE,
                    "Esquema de respuesta inválido", e));
        }
    }

    private static LlmException cancelled() {
        return new LlmException(LlmException.Kind.CANCELLED, "Generación cancelada");
    }

    private AnthropicClient getClient() {
        if (client == null) {
            AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                    .apiKey(apiKey)
                    .timeout(REQUEST_TIMEOUT)
                    .maxRetries(MAX_RETRIES);
            if (baseUrl != null) {
                builder.baseUrl(baseUrl);
            }
            client = builder.build();
        }
        return client;
    }

    MessageCreateParams buildParams(LlmRequest request) throws JSONException {
        BetaOutputConfig.Builder outputConfig = BetaOutputConfig.builder()
                .effort(BetaOutputConfig.Effort.LOW);
        if (!request.jsonSchema.isEmpty()) {
            outputConfig.format(BetaJsonOutputFormat.builder()
                    .schema(toSchema(new JSONObject(request.jsonSchema)))
                    .build());
        }

        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(Math.max(MIN_MAX_TOKENS, request.maxTokens))
                .addUserMessage(request.userPrompt)
                .outputConfig(outputConfig.build())
                .addBeta(FALLBACK_BETA)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from(FALLBACK_MODE));
        if (!request.systemPrompt.isEmpty()) {
            params.system(request.systemPrompt);
        }
        return params.build();
    }

    private static BetaJsonOutputFormat.Schema toSchema(JSONObject schema) throws JSONException {
        BetaJsonOutputFormat.Schema.Builder builder = BetaJsonOutputFormat.Schema.builder();
        for (Map.Entry<String, Object> entry : toMap(schema).entrySet()) {
            builder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
        }
        return builder.build();
    }

    static String extractText(BetaMessage message) throws LlmException {
        BetaStopReason stopReason = message.stopReason().orElse(null);
        if (BetaStopReason.REFUSAL.equals(stopReason)) {
            throw new LlmException(LlmException.Kind.REFUSED, "Claude no quiso responder");
        }
        StringBuilder text = new StringBuilder();
        for (BetaContentBlock block : message.content()) {
            block.text().ifPresent(textBlock -> text.append(textBlock.text()));
        }
        if (text.toString().trim().isEmpty()) {
            throw new LlmException(LlmException.Kind.BAD_RESPONSE, "Claude respondió sin texto");
        }
        return text.toString();
    }

    // org.json -> Map/List, que es lo que acepta JsonValue.from

    private static Map<String, Object> toMap(JSONObject object) throws JSONException {
        Map<String, Object> map = new LinkedHashMap<>();
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, toPlain(object.get(key)));
        }
        return map;
    }

    private static List<Object> toList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            list.add(toPlain(array.get(i)));
        }
        return list;
    }

    private static Object toPlain(Object value) throws JSONException {
        if (value instanceof JSONObject) return toMap((JSONObject) value);
        if (value instanceof JSONArray) return toList((JSONArray) value);
        if (value == JSONObject.NULL) return null;
        return value;
    }
}
