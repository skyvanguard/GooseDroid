package com.cfks.goosedroid.core;

import java.util.Random;

/** Funciones numéricas chicas que usa el motor. */
public final class MathUtil {
    public static final float RAD_TO_DEG = (float) (180.0 / Math.PI);

    /** Generador compartido del motor (un solo hilo: el del render). */
    public static final Random RNG = new Random();

    private MathUtil() {
    }

    /** Valor uniforme en [min, max). */
    public static float randomRange(float min, float max) {
        return min + RNG.nextFloat() * (max - min);
    }

    public static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** Interpola ángulos en grados por el camino más corto; t se limita a [0, 1]. */
    public static float lerpAngle(float from, float to, float t) {
        float delta = ((to - from) % 360f + 540f) % 360f - 180f;
        return from + delta * clamp(t, 0f, 1f);
    }

    /** Curva suave de aceleración y frenado para t en [0, 1]. */
    public static float easeInOut(float t) {
        float c = clamp(t, 0f, 1f);
        return c * c * (3f - 2f * c);
    }
}
