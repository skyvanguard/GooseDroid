package com.skyvanguard.goozy.brain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modelos en formato .litertlm que la app sabe descargar. Todos son de descarga
 * libre: ninguno pide cuenta ni aceptar una licencia en un sitio web.
 *
 * Tamaños verificados contra el servidor el 2026-09-27.
 */
public final class LocalModelCatalog {
    private static final String HOST = "https://huggingface.co/litert-community/";

    private static final List<LocalModel> MODELS = buildModels();

    private LocalModelCatalog() {
    }

    private static List<LocalModel> buildModels() {
        List<LocalModel> models = new ArrayList<>();
        models.add(new LocalModel(
                "lfm25_230m",
                "LFM2.5 230M",
                "El más liviano. Para teléfonos modestos o para probar.",
                "LFM 1.0",
                "LFM2.5-230M_int4.litertlm",
                HOST + "LFM2.5-230M/resolve/main/LFM2.5-230M_int4.litertlm",
                176_756_720L));
        models.add(new LocalModel(
                "qwen3_06b",
                "Qwen3 0.6B",
                "Chico y rápido. Frases simples.",
                "Apache 2.0",
                "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
                HOST + "Qwen3-0.6B-int4/resolve/main/"
                        + "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
                347_251_840L));
        models.add(new LocalModel(
                "lfm25_12b",
                "LFM2.5 1.2B Instruct",
                "Buen equilibrio entre calidad y velocidad. Habla español.",
                "LFM 1.0",
                "LFM2.5-1.2B-Instruct_int4.litertlm",
                HOST + "LFM2.5-1.2B-Instruct/resolve/main/LFM2.5-1.2B-Instruct_int4.litertlm",
                736_015_744L));
        models.add(new LocalModel(
                "qwen3_17b",
                "Qwen3 1.7B",
                "Más carácter en las respuestas. Necesita un teléfono potente.",
                "Apache 2.0",
                "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
                HOST + "Qwen3-1.7B/resolve/main/Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
                977_184_032L));
        models.add(new LocalModel(
                "gemma4_e2b",
                "Gemma 4 E2B",
                "La mejor calidad. Recomendado con 8 GB de RAM o más; descarga grande.",
                "Apache 2.0",
                "gemma-4-E2B-it.litertlm",
                HOST + "gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
                2_588_147_712L));
        return Collections.unmodifiableList(models);
    }

    public static List<LocalModel> getModels() {
        return MODELS;
    }

    /**
     * @return el modelo con ese id, o null si no existe
     */
    public static LocalModel find(String id) {
        for (LocalModel model : MODELS) {
            if (model.id.equals(id)) return model;
        }
        return null;
    }
}
