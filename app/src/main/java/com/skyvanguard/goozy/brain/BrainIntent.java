package com.skyvanguard.goozy.brain;

/**
 * Lo que el cerebro decidió: qué decir, con qué emoción, qué hacer y qué
 * recordar. Inmutable y ya validado.
 */
public final class BrainIntent {
    public static final int MAX_SAY_LENGTH = 90;
    public static final int MAX_REMEMBER_LENGTH = 160;

    public final String say;
    public final BrainMood mood;
    public final BrainAction action;
    /** Dato para la memoria a largo plazo; vacío si no hay nada que recordar. */
    public final String remember;

    public BrainIntent(String say, BrainMood mood, BrainAction action, String remember) {
        this.say = say != null ? say : "";
        this.mood = mood != null ? mood : BrainMood.NEUTRAL;
        this.action = action != null ? action : BrainAction.NONE;
        this.remember = remember != null ? remember : "";
    }

    public static BrainIntent ofSpeech(String say) {
        return new BrainIntent(say, BrainMood.NEUTRAL, BrainAction.NONE, "");
    }

    public boolean hasSpeech() {
        return !say.isEmpty();
    }

    public boolean hasMemory() {
        return !remember.isEmpty();
    }

    public boolean isEmpty() {
        return say.isEmpty() && action == BrainAction.NONE && remember.isEmpty();
    }
}
