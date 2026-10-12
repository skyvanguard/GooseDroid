package com.skyvanguard.goozy.brain;

import android.content.Context;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * La voz del ganso: lee sus frases con el sintetizador del sistema.
 *
 * Todos los métodos se llaman desde el hilo principal.
 */
final class GooseVoice {
    private static final String TAG = "GooseVoice";
    /** Un ganso tiene voz aguda y habla rápido. */
    private static final float PITCH = 1.5f;
    private static final float SPEECH_RATE = 1.1f;
    /** Lo que dice por su cuenta no se lee más seguido que esto. */
    private static final long MIN_SPONTANEOUS_INTERVAL_MS = 30_000L;
    private static final String UTTERANCE_ID = "goose";

    private final Context appContext;
    private final Locale locale;
    private TextToSpeech engine;
    private boolean isReady = false;
    private String pendingText = null;
    private long lastSpontaneousMs = Long.MIN_VALUE / 2;

    GooseVoice(Context context, String language) {
        this.appContext = context.getApplicationContext();
        this.locale = localeFor(language);
    }

    static Locale localeFor(String language) {
        String lower = language != null ? language.trim().toLowerCase(Locale.ROOT) : "";
        if (lower.startsWith("en")) return Locale.ENGLISH;
        if (lower.startsWith("port")) return new Locale("pt");
        return new Locale("es");
    }

    /**
     * @param isUserInitiated true si responde a algo que hizo el humano
     */
    void speak(String text, boolean isUserInitiated) {
        String speakable = toSpeakable(text);
        if (speakable.isEmpty()) return;

        long now = SystemClock.elapsedRealtime();
        if (!isUserInitiated) {
            if (now - lastSpontaneousMs < MIN_SPONTANEOUS_INTERVAL_MS) return;
            lastSpontaneousMs = now;
        }

        if (engine == null) {
            // Se crea con el primer uso: el sintetizador tarda en arrancar
            pendingText = speakable;
            engine = new TextToSpeech(appContext, this::onInit);
            return;
        }
        if (!isReady) {
            pendingText = speakable;
            return;
        }
        engine.speak(speakable, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
    }

    private void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || engine == null) {
            Log.w(TAG, "No hay sintetizador de voz disponible (" + status + ")");
            return;
        }
        int result = engine.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "El sintetizador no tiene voz para " + locale);
        }
        engine.setPitch(PITCH);
        engine.setSpeechRate(SPEECH_RATE);
        isReady = true;
        if (pendingText != null) {
            engine.speak(pendingText, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
            pendingText = null;
        }
    }

    void release() {
        if (engine != null) {
            engine.stop();
            engine.shutdown();
            engine = null;
        }
        isReady = false;
        pendingText = null;
    }

    /**
     * Quita acotaciones ("*se estira*"), emojis y caritas que el sintetizador
     * leería mal.
     */
    static String toSpeakable(String text) {
        if (text == null) return "";
        String result = text.replaceAll("\\*[^*]*\\*", " ")
                .replaceAll("[<>:;=]-?[()DPp3]+", " ")
                .replace("<3", " ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.length(); ) {
            int codePoint = result.codePointAt(i);
            if (Character.getType(codePoint) != Character.OTHER_SYMBOL
                    && Character.getType(codePoint) != Character.SURROGATE) {
                sb.appendCodePoint(codePoint);
            }
            i += Character.charCount(codePoint);
        }
        String clean = sb.toString().replaceAll("\\s+", " ").trim();
        for (int i = 0; i < clean.length(); i++) {
            if (Character.isLetterOrDigit(clean.charAt(i))) return clean;
        }
        return "";
    }
}
