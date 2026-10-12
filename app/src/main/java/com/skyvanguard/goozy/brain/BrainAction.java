package com.skyvanguard.goozy.brain;

import java.util.Locale;

/**
 * Lo único que el modelo puede pedirle al ganso que haga. Cada valor corresponde
 * a algo que el juego ya sabe ejecutar.
 */
public enum BrainAction {
    NONE("no hacer nada especial"),
    WANDER("pasear"),
    NAP("dormir una siesta"),
    ZOOMIES("correr como loco"),
    DANCE("bailar"),
    SPIN("girar"),
    SING("cantar"),
    HONK("graznar"),
    STRETCH("estirarse"),
    LOOK_AROUND("mirar alrededor"),
    PLAY_DEAD("hacerse el muerto"),
    MOONWALK("caminar hacia atrás"),
    TRACK_MUD("ensuciar la pantalla con barro"),
    SEEK_ATTENTION("ir al centro a pedir atención"),
    SULK("ponerse triste"),
    CELEBRATE("festejar");

    private final String description;

    BrainAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    private static final int MAX_TYPOS = 2;
    private static final int MIN_LENGTH_FOR_TYPOS = 5;

    /**
     * Acepta mayúsculas, minúsculas, separadores distintos y errores de tipeo
     * chicos ("LOOK_ARROUND", "lookaround"). Desconocido = NONE.
     */
    public static BrainAction fromModelText(String text) {
        if (text == null) return NONE;
        String wanted = lettersOnly(text);
        if (wanted.isEmpty()) return NONE;

        BrainAction closest = NONE;
        int closestDistance = Integer.MAX_VALUE;
        for (BrainAction action : values()) {
            String candidate = lettersOnly(action.name());
            if (candidate.equals(wanted)) return action;
            int distance = editDistance(candidate, wanted);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = action;
            }
        }
        boolean isCloseEnough = closestDistance <= MAX_TYPOS
                && wanted.length() >= MIN_LENGTH_FOR_TYPOS;
        return isCloseEnough ? closest : NONE;
    }

    private static String lettersOnly(String text) {
        StringBuilder sb = new StringBuilder();
        String upper = text.toUpperCase(Locale.ROOT);
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c >= 'A' && c <= 'Z') sb.append(c);
        }
        return sb.toString();
    }

    /** Distancia de Levenshtein. */
    private static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
