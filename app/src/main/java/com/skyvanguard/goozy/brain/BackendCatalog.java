package com.skyvanguard.goozy.brain;

import android.content.Context;

import com.skyvanguard.goozy.brain.backend.AnthropicBackend;
import com.skyvanguard.goozy.brain.backend.LiteRtBackend;
import com.skyvanguard.goozy.brain.backend.OpenAiCompatBackend;
import com.skyvanguard.goozy.brain.model.LocalModel;
import com.skyvanguard.goozy.brain.model.LocalModelCatalog;
import com.skyvanguard.goozy.brain.model.ModelDownloads;
import com.skyvanguard.goozy.brain.backend.TemplateBackend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Los tipos de cerebro que el usuario puede elegir y cómo se construye cada uno.
 */
public final class BackendCatalog {
    public static final String ID_OLLAMA = "ollama";
    public static final String ID_OPENAI_COMPAT = "openai_compat";
    public static final String ID_GEMINI = "gemini";

    /** Desde el emulador, 10.0.2.2 es la PC que lo aloja. */
    private static final String DEFAULT_OLLAMA_URL = "http://192.168.0.10:11434/v1";
    private static final String DEFAULT_OLLAMA_MODEL = "gemma3:1b";
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/openai";
    private static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash-lite";
    private static final String DEFAULT_LOCAL_MODEL = "gemma4_e2b";
    private static final String LITERT_CACHE_DIRECTORY = "litert";

    /** Descripción de un tipo de backend para la pantalla de ajustes. Inmutable. */
    public static final class Entry {
        public final String id;
        public final String title;
        public final String summary;
        public final boolean isRemote;
        public final boolean needsUrl;
        public final boolean needsModel;
        public final boolean needsApiKey;
        /** true si piensa con un modelo descargado al teléfono. */
        public final boolean needsLocalModel;
        public final String defaultUrl;
        public final String defaultModel;

        Entry(String id, String title, String summary, boolean isRemote, boolean needsUrl,
              boolean needsModel, boolean needsApiKey, String defaultUrl, String defaultModel) {
            this(id, title, summary, isRemote, needsUrl, needsModel, needsApiKey, false,
                    defaultUrl, defaultModel);
        }

        Entry(String id, String title, String summary, boolean isRemote, boolean needsUrl,
              boolean needsModel, boolean needsApiKey, boolean needsLocalModel,
              String defaultUrl, String defaultModel) {
            this.needsLocalModel = needsLocalModel;
            this.id = id;
            this.title = title;
            this.summary = summary;
            this.isRemote = isRemote;
            this.needsUrl = needsUrl;
            this.needsModel = needsModel;
            this.needsApiKey = needsApiKey;
            this.defaultUrl = defaultUrl;
            this.defaultModel = defaultModel;
        }
    }

    private static final List<Entry> ENTRIES = buildEntries();

    private BackendCatalog() {
    }

    private static List<Entry> buildEntries() {
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(TemplateBackend.ID, "Plantillas",
                "Frases predefinidas. Sin modelo, sin red, sin gasto de batería.",
                false, false, false, false, "", ""));
        entries.add(new Entry(LiteRtBackend.ID, "Modelo en el teléfono",
                "Un modelo descargado al teléfono. Privado y sin conexión; usa más "
                        + "batería mientras piensa.",
                false, false, false, false, true, "", DEFAULT_LOCAL_MODEL));
        entries.add(new Entry(ID_OLLAMA, "Ollama en mi red",
                "Un modelo corriendo en tu PC. Gratis y privado dentro de tu red.",
                true, true, true, false, DEFAULT_OLLAMA_URL, DEFAULT_OLLAMA_MODEL));
        entries.add(new Entry(ID_OPENAI_COMPAT, "Servidor compatible con OpenAI",
                "LM Studio, OpenRouter, Groq u otro servicio con el mismo formato.",
                true, true, true, true, "", ""));
        entries.add(new Entry(AnthropicBackend.ID, "Claude (Anthropic)",
                "La mejor calidad de conversación. Necesita una clave de API de "
                        + "Anthropic y cada respuesta tiene un costo; podés cambiar el modelo.",
                true, false, true, true, "", AnthropicBackend.DEFAULT_MODEL));
        entries.add(new Entry(ID_GEMINI, "Gemini",
                "API de Google. Tiene nivel gratuito; en ese nivel Google usa el "
                        + "contenido para mejorar sus productos.",
                true, false, true, true, GEMINI_URL, DEFAULT_GEMINI_MODEL));
        return Collections.unmodifiableList(entries);
    }

    public static List<Entry> getEntries() {
        return ENTRIES;
    }

    /** El modelo local elegido, o el primero del catálogo si el guardado ya no existe. */
    public static LocalModel getSelectedLocalModel(BrainConfig config) {
        LocalModel model = LocalModelCatalog.find(
                config.getModel(LiteRtBackend.ID, DEFAULT_LOCAL_MODEL));
        return model != null ? model : LocalModelCatalog.getModels().get(0);
    }

    public static Entry find(String backendId) {
        for (Entry entry : ENTRIES) {
            if (entry.id.equals(backendId)) return entry;
        }
        return ENTRIES.get(0);
    }

    /**
     * @return el backend configurado, o null si el elegido es el de plantillas
     *         (que ya es el respaldo del cerebro)
     */
    public static LlmBackend create(Context context, BrainConfig config) {
        Entry entry = find(config.getBackendId());
        if (TemplateBackend.ID.equals(entry.id)) return null;

        if (entry.needsLocalModel) {
            LocalModel model = getSelectedLocalModel(config);
            java.io.File cacheDir = new java.io.File(
                    context.getApplicationContext().getCacheDir(), LITERT_CACHE_DIRECTORY);
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                cacheDir = context.getApplicationContext().getCacheDir();
            }
            return new LiteRtBackend(ModelDownloads.getFile(context, model), cacheDir,
                    config.isGpuEnabled());
        }

        String url = entry.needsUrl ? config.getUrl(entry.id, entry.defaultUrl) : entry.defaultUrl;
        String model = config.getModel(entry.id, entry.defaultModel);
        String apiKey = entry.needsApiKey ? config.getApiKey(entry.id) : "";
        if (AnthropicBackend.ID.equals(entry.id)) {
            return new AnthropicBackend(model, apiKey);
        }
        return new OpenAiCompatBackend(entry.id, url, model, apiKey);
    }
}
