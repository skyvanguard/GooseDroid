package com.skyvanguard.goozy.engine;

import com.skyvanguard.goozy.core.Vec2;

/**
 * Ciclo de vida de una nota que el ganso trae desde el borde de la pantalla,
 * Solo lógica, sin Android.
 *
 * NONE → WAITING (el ganso fue a buscarla) → CARRIED (la arrastra)
 *      → PLACED (queda en pantalla un rato) → NONE
 */
final class NoteCarrier {
    enum State { NONE, WAITING, CARRIED, PLACED }

    /** Distancia entre el ganso y el centro de la nota mientras la arrastra. */
    static final float CARRY_DISTANCE = 70f;
    /** Tiempo en pantalla: más largo cuanto más texto hay para leer. */
    static final float MIN_LIFETIME_SECONDS = 20f;
    static final float MAX_LIFETIME_SECONDS = 60f;
    static final float SECONDS_PER_CHAR = 0.2f;
    static final float FADE_SECONDS = 3f;

    private State state = State.NONE;
    private String text = "";
    private float directionX = 0f;
    private float directionY = 0f;
    private float centerX = 0f;
    private float centerY = 0f;
    private float age = 0f;
    private float lifetime = MIN_LIFETIME_SECONDS;

    State getState() {
        return state;
    }

    String getText() {
        return text;
    }

    float getCenterX() {
        return centerX;
    }

    float getCenterY() {
        return centerY;
    }

    /** El ganso sale a buscar una nota; el texto de respaldo ya está elegido. */
    void prepare(String fallbackText) {
        state = State.WAITING;
        text = fallbackText != null ? fallbackText : "";
        age = 0f;
    }

    /**
     * Un texto mejor (por ejemplo escrito por el modelo) reemplaza al de
     * respaldo solo si la nota todavía no está a la vista.
     */
    boolean offerText(String newText) {
        if (state != State.WAITING || newText == null || newText.trim().isEmpty()) return false;
        text = newText.trim();
        return true;
    }

    /**
     * Empieza a arrastrarla.
     *
     * @param dirX dirección hacia el borde de donde viene la nota (unitaria)
     */
    void pickUp(float dirX, float dirY, Vec2 goosePosition) {
        if (state != State.WAITING || text.isEmpty()) {
            state = State.NONE;
            return;
        }
        state = State.CARRIED;
        directionX = dirX;
        directionY = dirY;
        follow(goosePosition);
    }

    /** Deja la nota donde está, completamente dentro del mundo. */
    void place(float worldWidth, float worldHeight, float halfWidth, float halfHeight) {
        if (state == State.WAITING) {
            // Nunca llegó a traerla (la tarea se interrumpió antes)
            state = State.NONE;
            return;
        }
        if (state != State.CARRIED) return;
        centerX = clamp(centerX, halfWidth, worldWidth - halfWidth);
        centerY = clamp(centerY, halfHeight, worldHeight - halfHeight);
        state = State.PLACED;
        age = 0f;
        lifetime = lifetimeFor(text);
    }

    void update(float deltaTime, Vec2 goosePosition) {
        if (state == State.CARRIED) {
            follow(goosePosition);
        } else if (state == State.PLACED) {
            age += deltaTime;
            if (age >= lifetime) {
                state = State.NONE;
            }
        }
    }

    /** Opacidad de 0 a 1: se desvanece en los últimos segundos. */
    float getAlpha() {
        switch (state) {
            case CARRIED:
                return 1f;
            case PLACED:
                float remaining = lifetime - age;
                return remaining >= FADE_SECONDS ? 1f : Math.max(0f, remaining / FADE_SECONDS);
            default:
                return 0f;
        }
    }

    static float lifetimeFor(String text) {
        float seconds = MIN_LIFETIME_SECONDS + text.length() * SECONDS_PER_CHAR;
        return Math.min(MAX_LIFETIME_SECONDS, seconds);
    }

    float getLifetime() {
        return lifetime;
    }

    void reset() {
        state = State.NONE;
        text = "";
        age = 0f;
    }

    private void follow(Vec2 goosePosition) {
        if (goosePosition == null) return;
        centerX = goosePosition.x + directionX * CARRY_DISTANCE;
        centerY = goosePosition.y + directionY * CARRY_DISTANCE;
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) return (min + max) / 2f;
        return Math.max(min, Math.min(value, max));
    }
}
