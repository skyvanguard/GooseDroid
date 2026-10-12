package com.cfks.goosedroid.core;

/** Huella de barro que deja el ganso; se desvanece con el tiempo de juego. */
public final class Footprint {
    /** Segundos que la huella se ve entera. */
    public static final float VISIBLE_SECONDS = 8f;
    /** Segundos que tarda en desaparecer después. */
    public static final float FADE_SECONDS = 1.2f;

    public Vec2 position;
    /** {@link Time#time} al pisar; 0 = ranura sin usar. */
    public double stampedAt;

    /** Fracción visible en [0, 1] en el instante dado. */
    public float visibility(double now) {
        if (stampedAt == 0.0) return 0f;
        double age = now - stampedAt;
        if (age <= VISIBLE_SECONDS) return 1f;
        return (float) Math.max(0.0, 1.0 - (age - VISIBLE_SECONDS) / FADE_SECONDS);
    }
}
