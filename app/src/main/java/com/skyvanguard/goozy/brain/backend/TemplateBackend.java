package com.skyvanguard.goozy.brain.backend;

import com.skyvanguard.goozy.brain.LlmBackend;
import com.skyvanguard.goozy.brain.LlmCallback;
import com.skyvanguard.goozy.brain.LlmException;
import com.skyvanguard.goozy.brain.LlmRequest;

/**
 * El "modelo" que siempre está: frases armadas con plantillas. No usa red ni
 * memoria extra, así que es el respaldo cuando cualquier otro backend falla.
 */
public class TemplateBackend implements LlmBackend {
    public static final String ID = "templates";

    /** De dónde salen las frases. El juego ya tiene un generador por plantillas. */
    public interface PhraseSource {
        /**
         * @param tag motivo del pedido (nombre del disparador)
         * @return una frase corta, o null si no hay nada que decir
         */
        String nextPhrase(String tag);
    }

    private final PhraseSource source;

    public TemplateBackend(PhraseSource source) {
        this.source = source;
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
        return source != null;
    }

    @Override
    public void generate(LlmRequest request, LlmCallback callback) {
        if (source == null) {
            callback.onError(new LlmException(LlmException.Kind.UNAVAILABLE,
                    "No hay generador de plantillas"));
            return;
        }
        try {
            String phrase = source.nextPhrase(request.tag);
            if (phrase == null || phrase.trim().isEmpty()) {
                callback.onError(new LlmException(LlmException.Kind.BAD_RESPONSE,
                        "La plantilla no produjo texto"));
                return;
            }
            callback.onDone(phrase);
        } catch (RuntimeException e) {
            callback.onError(new LlmException(LlmException.Kind.BAD_RESPONSE,
                    "Falló el generador de plantillas", e));
        }
    }

    @Override
    public void cancel() {
        // La generación es instantánea: no hay nada que cancelar
    }

    @Override
    public void release() {
        // Sin recursos
    }
}
