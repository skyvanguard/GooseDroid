package com.skyvanguard.goozy.brain;

import java.util.Locale;

/** Emoción con la que el ganso dice algo. */
public enum BrainMood {
    NEUTRAL,
    HAPPY,
    EXCITED,
    LOVING,
    SLEEPY,
    HUNGRY,
    SAD,
    ANGRY,
    MISCHIEVOUS,
    CURIOUS,
    SCARED;

    public static BrainMood fromModelText(String text) {
        if (text == null) return NEUTRAL;
        String normalized = text.trim().toUpperCase(Locale.ROOT);
        for (BrainMood mood : values()) {
            if (mood.name().equals(normalized)) return mood;
        }
        return NEUTRAL;
    }
}
