package com.cfks.goosedroid.core;

/**
 * Punto o dirección en el plano del mundo.
 *
 * Los campos son públicos y mutables porque el motor actualiza posiciones en el
 * lugar; las operaciones estáticas siempre devuelven un vector nuevo.
 */
public final class Vec2 {
    /** Origen compartido: no modificar sus campos. */
    public static final Vec2 ZERO = new Vec2(0f, 0f);

    public float x;
    public float y;

    public Vec2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public static Vec2 add(Vec2 a, Vec2 b) {
        return new Vec2(a.x + b.x, a.y + b.y);
    }

    public static Vec2 sub(Vec2 a, Vec2 b) {
        return new Vec2(a.x - b.x, a.y - b.y);
    }

    public static Vec2 neg(Vec2 v) {
        return new Vec2(-v.x, -v.y);
    }

    /** Producto componente a componente. */
    public static Vec2 mul(Vec2 a, Vec2 b) {
        return new Vec2(a.x * b.x, a.y * b.y);
    }

    public static Vec2 mul(Vec2 v, float factor) {
        return new Vec2(v.x * factor, v.y * factor);
    }

    public static Vec2 div(Vec2 v, float divisor) {
        return new Vec2(v.x / divisor, v.y / divisor);
    }

    public static float dot(Vec2 a, Vec2 b) {
        return a.x * b.x + a.y * b.y;
    }

    public static float length(Vec2 v) {
        return (float) Math.hypot(v.x, v.y);
    }

    public static float dist(Vec2 a, Vec2 b) {
        return (float) Math.hypot(a.x - b.x, a.y - b.y);
    }

    /** Vector de largo 1 en la misma dirección; el vector nulo devuelve el origen. */
    public static Vec2 normalize(Vec2 v) {
        float len = length(v);
        return len == 0f ? new Vec2(0f, 0f) : new Vec2(v.x / len, v.y / len);
    }

    public static Vec2 lerp(Vec2 from, Vec2 to, float t) {
        return new Vec2(MathUtil.lerp(from.x, to.x, t), MathUtil.lerp(from.y, to.y, t));
    }

    /** Dirección unitaria para un ángulo en grados (0° = derecha, sentido horario en pantalla). */
    public static Vec2 fromDegrees(float degrees) {
        double radians = Math.toRadians(degrees);
        return new Vec2((float) Math.cos(radians), (float) Math.sin(radians));
    }
}
