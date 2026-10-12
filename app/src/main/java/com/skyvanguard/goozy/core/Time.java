package com.skyvanguard.goozy.core;

/**
 * Reloj del juego.
 *
 * - deltaTime es el tiempo real entre frames, con tope para que una pausa
 *   (pantalla apagada, overlay oculto) no produzca un salto.
 * - time es tiempo de juego acumulado: solo avanza mientras hay frames, así que
 *   los timers no vencen todos juntos al reanudar.
 * - time es double: un float pierde resolución de frame tras unas horas.
 */
public class Time {
    public static final int framerate = 120;

    /** Delta usado antes del primer frame medido. */
    public static final float DEFAULT_DELTA_SECONDS = 1f / 60f;
    /** Tope por frame: por encima de esto se considera una pausa, no un frame lento. */
    public static final float MAX_DELTA_SECONDS = 0.05f;

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final long NOT_STARTED = -1L;

    public static float deltaTime = DEFAULT_DELTA_SECONDS;
    public static double time = 0.0;

    private static long lastTickNanos = NOT_STARTED;

    /** Avanza el reloj usando el reloj monotónico del sistema. */
    public static void TickTime() {
        tick(System.nanoTime());
    }

    /** Avanza el reloj hasta el instante dado (separado para poder testearlo). */
    public static void tick(long nowNanos) {
        if (lastTickNanos == NOT_STARTED) {
            deltaTime = DEFAULT_DELTA_SECONDS;
        } else {
            double elapsed = (nowNanos - lastTickNanos) / NANOS_PER_SECOND;
            deltaTime = (float) Math.max(0.0, Math.min(elapsed, MAX_DELTA_SECONDS));
        }
        lastTickNanos = nowNanos;
        time += deltaTime;
    }

    /** Reinicia el reloj; se llama al inicializar el ganso. */
    public static void reset() {
        lastTickNanos = NOT_STARTED;
        deltaTime = DEFAULT_DELTA_SECONDS;
        time = 0.0;
    }

    /** Tiempo de juego como float, para APIs gráficas que lo exigen. */
    public static float timeF() {
        return (float) time;
    }
}
