package com.skyvanguard.goozy.brain;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Decide cuándo pensar y qué hacer con lo pensado.
 *
 * Reglas:
 * - un solo pensamiento en curso a la vez;
 * - los pensamientos espontáneos respetan un intervalo mínimo; las reacciones
 *   al humano no;
 * - si el backend principal falla o responde algo inutilizable, se usa el de
 *   respaldo, de modo que el ganso nunca se queda mudo;
 * - la intención se entrega por el executor de entrega (el hilo principal en
 *   la app), nunca desde el hilo del backend.
 */
public class GooseBrain {
    static final long DEFAULT_MIN_INTERVAL_MS = 45_000L;
    /** Tras varias fallas seguidas se deja descansar al backend principal. */
    static final int FAILURES_BEFORE_BACKOFF = 3;
    static final long BACKOFF_MS = 5 * 60_000L;

    /** Recibe lo que el cerebro decidió. */
    public interface Listener {
        void onIntent(BrainIntent intent, BrainTrigger trigger, String backendId);

        /** El backend principal falló; ya se intentó el respaldo. */
        default void onBackendError(LlmException error, String backendId) {
        }
    }

    /** Reloj inyectable, para poder testear el límite de frecuencia. */
    public interface Clock {
        long nowMs();
    }

    private final LlmBackend fallback;
    private final PromptBuilder promptBuilder;
    private final BrainMemory memory;
    private final Executor deliveryExecutor;
    private final Clock clock;

    private LlmBackend primary;
    private Listener listener;
    private long minIntervalMs = DEFAULT_MIN_INTERVAL_MS;

    private final Object lock = new Object();
    private boolean isThinking = false;
    private long lastSpontaneousMs = Long.MIN_VALUE / 2;
    private int consecutiveFailures = 0;
    private long backoffUntilMs = 0L;
    private long generation = 0L;

    public GooseBrain(LlmBackend primary, LlmBackend fallback, PromptBuilder promptBuilder,
                      BrainMemory memory, Executor deliveryExecutor, Clock clock) {
        if (fallback == null) {
            throw new IllegalArgumentException("El backend de respaldo es obligatorio");
        }
        this.primary = primary;
        this.fallback = fallback;
        this.promptBuilder = promptBuilder;
        this.memory = memory;
        this.deliveryExecutor = deliveryExecutor;
        this.clock = clock;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setMinIntervalMs(long value) {
        this.minIntervalMs = Math.max(0L, value);
    }

    /** Cambia el backend principal y olvida las fallas del anterior. */
    public void setPrimary(LlmBackend backend) {
        LlmBackend previous;
        synchronized (lock) {
            previous = primary;
            primary = backend;
            consecutiveFailures = 0;
            backoffUntilMs = 0L;
            generation++;
            isThinking = false;
        }
        if (previous != null && previous != backend) {
            previous.release();
        }
    }

    public String getActiveBackendId() {
        LlmBackend backend = selectBackend();
        return backend.getId();
    }

    public boolean isThinking() {
        synchronized (lock) {
            return isThinking;
        }
    }

    /**
     * Pide un pensamiento.
     *
     * @return true si se empezó a pensar; false si se descartó por estar
     *         ocupado o por el límite de frecuencia
     */
    public boolean think(PetSnapshot pet, BrainTrigger trigger) {
        long now = clock.nowMs();
        long requestGeneration;
        synchronized (lock) {
            if (isThinking) return false;
            if (!trigger.kind.isUserInitiated()
                    && now - lastSpontaneousMs < minIntervalMs) {
                return false;
            }
            if (!trigger.kind.isUserInitiated()) {
                lastSpontaneousMs = now;
            }
            isThinking = true;
            requestGeneration = generation;
        }

        List<String> facts = memory != null ? memory.getFacts() : null;
        LlmRequest request = promptBuilder.build(pet, trigger, facts);
        LlmBackend backend = selectBackend();
        backend.generate(request, new Attempt(request, trigger, backend, requestGeneration));
        return true;
    }

    /** Cancela lo que se esté pensando; no se entrega ninguna intención. */
    public void cancel() {
        LlmBackend backend;
        synchronized (lock) {
            generation++;
            isThinking = false;
            backend = primary;
        }
        if (backend != null) {
            backend.cancel();
        }
    }

    public void release() {
        cancel();
        LlmBackend backend = primary;
        if (backend != null) {
            backend.release();
        }
        fallback.release();
    }

    private LlmBackend selectBackend() {
        synchronized (lock) {
            boolean isBackingOff = clock.nowMs() < backoffUntilMs;
            if (primary != null && !isBackingOff && primary.isAvailable()) {
                return primary;
            }
            return fallback;
        }
    }

    private boolean isCurrent(long requestGeneration) {
        synchronized (lock) {
            return requestGeneration == generation;
        }
    }

    private void finish(long requestGeneration) {
        synchronized (lock) {
            if (requestGeneration == generation) {
                isThinking = false;
            }
        }
    }

    private void recordFailure() {
        synchronized (lock) {
            consecutiveFailures++;
            if (consecutiveFailures >= FAILURES_BEFORE_BACKOFF) {
                backoffUntilMs = clock.nowMs() + BACKOFF_MS;
                consecutiveFailures = 0;
            }
        }
    }

    private void recordSuccess() {
        synchronized (lock) {
            consecutiveFailures = 0;
        }
    }

    /** Un intento contra un backend; si falla y no era el respaldo, reintenta ahí. */
    private final class Attempt implements LlmCallback {
        private final LlmRequest request;
        private final BrainTrigger trigger;
        private final LlmBackend backend;
        private final long requestGeneration;

        Attempt(LlmRequest request, BrainTrigger trigger, LlmBackend backend,
                long requestGeneration) {
            this.request = request;
            this.trigger = trigger;
            this.backend = backend;
            this.requestGeneration = requestGeneration;
        }

        @Override
        public void onDone(String fullText) {
            if (!isCurrent(requestGeneration)) return;

            BrainIntent intent = IntentParser.parse(fullText,
                    PromptBuilder.maxSayLength(trigger));
            if (intent != null && PromptBuilder.isToneExample(intent.say)) {
                // Un ejemplo copiado no es una frase propia
                intent = new BrainIntent("", intent.mood, intent.action, intent.remember);
            }
            if (intent == null || intent.isEmpty()) {
                onError(new LlmException(LlmException.Kind.BAD_RESPONSE,
                        "El modelo respondió algo que no se pudo interpretar"));
                return;
            }

            if (backend != fallback) {
                recordSuccess();
            }
            if (!PromptBuilder.allowsMemory(trigger)) {
                // Un recuerdo que no salió de algo dicho por el humano es relleno
                intent = new BrainIntent(intent.say, intent.mood, intent.action, "");
            }
            if (intent.hasMemory() && memory != null) {
                memory.remember(intent.remember);
            }
            finish(requestGeneration);
            deliver(intent);
        }

        @Override
        public void onError(LlmException error) {
            if (!isCurrent(requestGeneration)) return;

            if (error.getKind() == LlmException.Kind.CANCELLED) {
                finish(requestGeneration);
                return;
            }
            if (backend == fallback) {
                finish(requestGeneration);
                return;
            }

            recordFailure();
            notifyError(error);
            fallback.generate(request,
                    new Attempt(request, trigger, fallback, requestGeneration));
        }

        private void deliver(BrainIntent intent) {
            Listener target = listener;
            if (target == null) return;
            deliveryExecutor.execute(() -> {
                if (isCurrent(requestGeneration)) {
                    target.onIntent(intent, trigger, backend.getId());
                }
            });
        }

        private void notifyError(LlmException error) {
            Listener target = listener;
            if (target == null) return;
            deliveryExecutor.execute(() -> target.onBackendError(error, backend.getId()));
        }
    }
}
