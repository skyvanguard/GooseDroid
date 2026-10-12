package com.skyvanguard.goozy.brain.backend;

import android.util.Log;

import com.skyvanguard.goozy.brain.LlmBackend;
import com.skyvanguard.goozy.brain.LlmCallback;
import com.skyvanguard.goozy.brain.LlmException;
import com.skyvanguard.goozy.brain.LlmRequest;
import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.ResponseFormat;
import com.google.ai.edge.litertlm.SamplerConfig;
import com.google.ai.edge.litertlm.ThinkingConfig;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Un modelo corriendo dentro del teléfono con LiteRT-LM. Nada sale del equipo.
 *
 * Cargar el modelo tarda segundos y ocupa cientos de MB, así que se carga con
 * el primer pedido y se libera tras un rato sin uso.
 */
public class LiteRtBackend implements LlmBackend {
    public static final String ID = "on_device";

    private static final String TAG = "LiteRtBackend";
    private static final long UNLOAD_AFTER_IDLE_MINUTES = 5;
    private static final int CONTEXT_TOKENS = 2048;
    private static final int TOP_K = 40;
    private static final double TOP_P = 0.95;
    private static final int RANDOM_SEED = 0;
    /** Los modelos de 1B se desvían del formato y del idioma con temperaturas altas. */
    private static final float MAX_LOCAL_TEMPERATURE = 0.7f;

    private final File modelFile;
    private final File cacheDir;
    private final boolean useGpu;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "goose-brain-litert");
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            });

    // Solo se tocan desde el hilo del executor
    private Engine engine;
    private boolean isResponseFormatSupported = true;
    private ScheduledFuture<?> unloadTask;

    private volatile Conversation activeConversation;
    private volatile AtomicBoolean activeCancelFlag;

    /**
     * @param modelFile archivo .litertlm ya descargado
     * @param cacheDir  carpeta donde el motor guarda sus cachés de compilación
     * @param useGpu    true para usar la GPU; false para CPU
     */
    public LiteRtBackend(File modelFile, File cacheDir, boolean useGpu) {
        this.modelFile = modelFile;
        this.cacheDir = cacheDir;
        this.useGpu = useGpu;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public boolean isAvailable() {
        return modelFile != null && modelFile.isFile() && modelFile.length() > 0;
    }

    @Override
    public void generate(LlmRequest request, LlmCallback callback) {
        if (!isAvailable()) {
            callback.onError(new LlmException(LlmException.Kind.UNAVAILABLE,
                    "El modelo no está descargado"));
            return;
        }
        AtomicBoolean cancelFlag = new AtomicBoolean(false);
        activeCancelFlag = cancelFlag;
        executor.execute(() -> run(request, callback, cancelFlag));
    }

    @Override
    public void cancel() {
        AtomicBoolean cancelFlag = activeCancelFlag;
        if (cancelFlag != null) {
            cancelFlag.set(true);
        }
        Conversation conversation = activeConversation;
        if (conversation != null) {
            try {
                conversation.cancelProcess();
            } catch (RuntimeException e) {
                Log.w(TAG, "No se pudo cancelar la generación", e);
            }
        }
    }

    @Override
    public void release() {
        cancel();
        executor.execute(this::unload);
    }

    private void run(LlmRequest request, LlmCallback callback, AtomicBoolean cancelFlag) {
        cancelUnload();
        try {
            if (cancelFlag.get()) throw cancelled();
            ensureEngine();

            String text;
            try {
                text = converse(request, isResponseFormatSupported, cancelFlag);
            } catch (RuntimeException e) {
                if (!isResponseFormatSupported || cancelFlag.get()) throw e;
                // Este modelo o esta versión del motor no acepta el esquema
                Log.w(TAG, "Formato de respuesta no soportado; se sigue sin él", e);
                isResponseFormatSupported = false;
                text = converse(request, false, cancelFlag);
            }

            if (cancelFlag.get()) throw cancelled();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Salida cruda: " + text);
            }
            if (text.trim().isEmpty()) {
                throw new LlmException(LlmException.Kind.BAD_RESPONSE,
                        "El modelo no produjo texto");
            }
            callback.onDone(text);
        } catch (LlmException e) {
            callback.onError(e);
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            Log.e(TAG, "Falla del modelo local", e);
            unload();
            callback.onError(cancelFlag.get() ? cancelled()
                    : new LlmException(LlmException.Kind.BAD_RESPONSE,
                    "Falló el modelo local: " + e.getMessage(), e));
        } finally {
            scheduleUnload();
        }
    }

    private static LlmException cancelled() {
        return new LlmException(LlmException.Kind.CANCELLED, "Generación cancelada");
    }

    private void ensureEngine() {
        if (engine != null) return;
        long startMs = System.currentTimeMillis();
        Backend backend = useGpu ? new Backend.GPU() : new Backend.CPU();
        Engine created = new Engine(new EngineConfig(
                modelFile.getAbsolutePath(), backend, null, null,
                CONTEXT_TOKENS, null, cacheDir.getAbsolutePath()));
        created.initialize();
        engine = created;
        Log.i(TAG, "Modelo " + modelFile.getName() + " cargado en "
                + (System.currentTimeMillis() - startMs) + " ms ("
                + (useGpu ? "GPU" : "CPU") + ")");
    }

    /** Una conversación nueva por pedido: el ganso no arrastra contexto de un pensamiento a otro. */
    private String converse(LlmRequest request, boolean withFormat, AtomicBoolean cancelFlag) {
        ConversationConfig config = new ConversationConfig(
                request.systemPrompt.isEmpty() ? null : Contents.Companion.of(request.systemPrompt),
                Collections.emptyList(),
                Collections.emptyList(),
                new SamplerConfig(TOP_K, TOP_P,
                        Math.min(request.temperature, MAX_LOCAL_TEMPERATURE), RANDOM_SEED),
                false,
                null,
                Collections.emptyMap(),
                null,
                false,
                request.maxTokens,
                new ThinkingConfig(false),
                withFormat);

        ResponseFormat format = withFormat && !request.jsonSchema.isEmpty()
                ? ResponseFormat.json(request.jsonSchema) : null;

        try (Conversation conversation = engine.createConversation(config)) {
            activeConversation = conversation;
            if (cancelFlag.get()) return "";
            Message reply = conversation.sendMessage(request.userPrompt,
                    Collections.emptyMap(), null, null, null, null, null, format);
            return textOf(reply);
        } finally {
            activeConversation = null;
        }
    }

    private static String textOf(Message message) {
        if (message == null || message.getContents() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Content content : message.getContents().getContents()) {
            if (content instanceof Content.Text) {
                sb.append(((Content.Text) content).getText());
            }
        }
        return sb.toString();
    }

    private void scheduleUnload() {
        cancelUnload();
        unloadTask = executor.schedule(this::unload, UNLOAD_AFTER_IDLE_MINUTES,
                TimeUnit.MINUTES);
    }

    private void cancelUnload() {
        if (unloadTask != null) {
            unloadTask.cancel(false);
            unloadTask = null;
        }
    }

    private void unload() {
        if (engine == null) return;
        try {
            engine.close();
            Log.i(TAG, "Modelo liberado de memoria");
        } catch (RuntimeException e) {
            Log.w(TAG, "Error liberando el modelo", e);
        }
        engine = null;
    }
}
