package com.skyvanguard.goozy.brain;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Esquema JSON de la respuesta del modelo. Los backends que pueden forzar un
 * esquema lo usan para que la acción y la emoción salgan siempre de la lista
 * real, en vez de confiar en que el modelo la respete.
 */
final class IntentSchema {
    private IntentSchema() {
    }

    /**
     * @param withMemory   si la respuesta puede incluir un recuerdo
     * @param maxSayLength tope de caracteres de la frase
     * @return el esquema serializado
     */
    static String build(boolean withMemory, int maxSayLength) {
        try {
            JSONObject properties = new JSONObject()
                    .put("say", new JSONObject()
                            .put("type", "string")
                            .put("maxLength", maxSayLength))
                    .put("mood", enumOf(moodNames()))
                    .put("action", enumOf(actionNames()));
            JSONArray required = new JSONArray().put("say").put("mood").put("action");

            if (withMemory) {
                properties.put("remember", new JSONObject()
                        .put("type", "string")
                        .put("maxLength", BrainIntent.MAX_REMEMBER_LENGTH));
                required.put("remember");
            }

            return new JSONObject()
                    .put("type", "object")
                    .put("properties", properties)
                    .put("required", required)
                    .put("additionalProperties", false)
                    .toString();
        } catch (JSONException e) {
            // Solo claves y valores constantes: no puede fallar
            throw new IllegalStateException(e);
        }
    }

    private static JSONObject enumOf(JSONArray names) throws JSONException {
        return new JSONObject().put("type", "string").put("enum", names);
    }

    private static JSONArray moodNames() {
        JSONArray names = new JSONArray();
        for (BrainMood mood : BrainMood.values()) {
            names.put(mood.name());
        }
        return names;
    }

    private static JSONArray actionNames() {
        JSONArray names = new JSONArray();
        for (BrainAction action : BrainAction.values()) {
            names.put(action.name());
        }
        return names;
    }
}
