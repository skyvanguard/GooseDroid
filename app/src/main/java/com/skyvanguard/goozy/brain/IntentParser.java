package com.skyvanguard.goozy.brain;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Convierte el texto de un modelo en una intención validada.
 *
 * Los modelos chicos rodean el JSON con texto, lo envuelven en bloques de
 * código o directamente contestan en prosa: nada de eso debe romper al ganso.
 */
public final class IntentParser {
    private static final String ELLIPSIS = "…";
    private static final String THINK_OPEN = "<think>";
    private static final String THINK_CLOSE = "</think>";
    private static final String CODE_FENCE = "```";
    private static final String LEADING_NOISE = ":;,.-\u2013\u2014 ";

    private IntentParser() {
    }

    /**
     * @return la intención, o null si el texto no contiene nada utilizable
     */
    public static BrainIntent parse(String modelOutput) {
        return parse(modelOutput, BrainIntent.MAX_SAY_LENGTH);
    }

    /**
     * @param maxSayLength tope de caracteres de la frase (el diario admite más)
     * @return la intención, o null si el texto no contiene nada utilizable
     */
    public static BrainIntent parse(String modelOutput, int maxSayLength) {
        if (modelOutput == null) return null;
        String text = stripReasoning(modelOutput).trim();
        if (text.isEmpty()) return null;

        String json = extractFirstJsonObject(text);
        if (json != null) {
            BrainIntent intent = parseJson(json, maxSayLength);
            if (intent != null) return intent;
        }

        // JSON cortado a la mitad: rescatar los campos que llegaron completos
        if (text.indexOf('{') >= 0) {
            return salvageFields(text, maxSayLength);
        }

        // Sin JSON: tratar la respuesta como una frase suelta
        String speech = cleanSpeech(stripCodeFence(text), maxSayLength);
        if (speech.isEmpty() || speech.indexOf('{') >= 0) return null;
        return BrainIntent.ofSpeech(speech);
    }

    private static BrainIntent parseJson(String json, int maxSayLength) {
        try {
            JSONObject object = new JSONObject(json);
            BrainIntent intent = new BrainIntent(
                    cleanSpeech(optStringOrEmpty(object, "say"), maxSayLength),
                    BrainMood.fromModelText(optStringOrEmpty(object, "mood")),
                    BrainAction.fromModelText(optStringOrEmpty(object, "action")),
                    cleanSpeech(optStringOrEmpty(object, "remember"),
                            BrainIntent.MAX_REMEMBER_LENGTH));
            return intent.isEmpty() ? null : intent;
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Lee "say", "mood" y "action" de un JSON incompleto. Un campo sin cerrar
     * se descarta; "remember" solo se acepta completo.
     */
    static BrainIntent salvageFields(String text, int maxSayLength) {
        BrainIntent intent = new BrainIntent(
                cleanSpeech(findStringField(text, "say"), maxSayLength),
                BrainMood.fromModelText(findStringField(text, "mood")),
                BrainAction.fromModelText(findStringField(text, "action")),
                cleanSpeech(findStringField(text, "remember"), BrainIntent.MAX_REMEMBER_LENGTH));
        return intent.isEmpty() ? null : intent;
    }

    /**
     * Busca "clave": "valor" y devuelve el valor si la cadena está cerrada;
     * cadena vacía si no está o quedó cortada.
     */
    static String findStringField(String text, String key) {
        String quotedKey = "\"" + key + "\"";
        int keyIndex = text.indexOf(quotedKey);
        if (keyIndex < 0) return "";
        int colon = text.indexOf(':', keyIndex + quotedKey.length());
        if (colon < 0) return "";
        int start = colon + 1;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
        if (start >= text.length() || text.charAt(start) != '"') return "";

        StringBuilder value = new StringBuilder();
        boolean isEscaped = false;
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isEscaped) {
                value.append(unescape(c));
                isEscaped = false;
            } else if (c == '\\') {
                isEscaped = true;
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
        }
        return "";
    }

    private static char unescape(char c) {
        switch (c) {
            case 'n':
            case 'r':
            case 't':
                return ' ';
            default:
                return c;
        }
    }

    /** Un valor null o no textual cuenta como vacío. */
    private static String optStringOrEmpty(JSONObject object, String key) {
        if (object.isNull(key)) return "";
        Object value = object.opt(key);
        return value instanceof String ? (String) value : "";
    }

    /** Algunos modelos razonan entre etiquetas antes de contestar. */
    static String stripReasoning(String text) {
        String result = text;
        int start = result.indexOf(THINK_OPEN);
        while (start >= 0) {
            int end = result.indexOf(THINK_CLOSE, start);
            if (end < 0) {
                return result.substring(0, start);
            }
            result = result.substring(0, start) + result.substring(end + THINK_CLOSE.length());
            start = result.indexOf(THINK_OPEN);
        }
        return result;
    }

    private static String stripCodeFence(String text) {
        String result = text.trim();
        if (!result.startsWith(CODE_FENCE)) return result;

        int firstNewline = result.indexOf('\n');
        result = firstNewline >= 0 ? result.substring(firstNewline + 1) : "";
        int closing = result.lastIndexOf(CODE_FENCE);
        if (closing >= 0) result = result.substring(0, closing);
        return result.trim();
    }

    /**
     * Devuelve el primer objeto JSON balanceado del texto, respetando llaves
     * dentro de cadenas.
     */
    static String extractFirstJsonObject(String text) {
        int start = text.indexOf('{');
        while (start >= 0) {
            int end = findMatchingBrace(text, start);
            if (end > start) return text.substring(start, end + 1);
            start = text.indexOf('{', start + 1);
        }
        return null;
    }

    private static int findMatchingBrace(String text, int start) {
        int depth = 0;
        boolean isInString = false;
        boolean isEscaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isEscaped) {
                isEscaped = false;
            } else if (c == '\\') {
                isEscaped = isInString;
            } else if (c == '"') {
                isInString = !isInString;
            } else if (!isInString && c == '{') {
                depth++;
            } else if (!isInString && c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** Una sola línea, sin comillas envolventes, con largo acotado. */
    static String cleanSpeech(String raw, int maxLength) {
        if (raw == null) return "";
        String text = raw.replaceAll("\\s+", " ").trim();
        while (text.length() >= 2 && isQuote(text.charAt(0))
                && isQuote(text.charAt(text.length() - 1))) {
            text = text.substring(1, text.length() - 1).trim();
        }
        // Solo puntuación ("," o "...") es ruido del modelo, no una frase
        if (!hasMeaningfulCharacter(text)) return "";
        // La decodificación con esquema a veces deja ":" o "," al principio
        text = stripLeadingNoise(text);
        if (text.length() <= maxLength) return text;

        String cut = text.substring(0, maxLength - 1);
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace > maxLength / 2) cut = cut.substring(0, lastSpace);
        return cut.trim() + ELLIPSIS;
    }

    /** Quita puntuación suelta del comienzo; los signos ¿ y ¡ se conservan. */
    private static String stripLeadingNoise(String text) {
        int start = 0;
        while (start < text.length() && LEADING_NOISE.indexOf(text.charAt(start)) >= 0) {
            start++;
        }
        return text.substring(start).trim();
    }

    /** Letras, números o emojis. */
    private static boolean hasMeaningfulCharacter(String text) {
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (Character.isLetterOrDigit(codePoint)
                    || Character.getType(codePoint) == Character.OTHER_SYMBOL) {
                return true;
            }
            i += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean isQuote(char c) {
        return c == '"' || c == '\'' || c == '“' || c == '”' || c == '`';
    }
}
