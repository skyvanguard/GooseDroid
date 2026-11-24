package com.skyvanguard.goozy.engine;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;


import java.util.Random;

/**
 * Sistema de efectos de sonido extendido para el ganso.
 * Maneja diferentes tipos de honks, melodías, y efectos especiales.
 */
public class GooseSoundEffects {

    private static final String TAG = "GooseSoundEffects";

    // ============== TIPOS DE SONIDOS ==============

    public enum SoundType {
        HONK_NORMAL,
        HONK_HAPPY,
        HONK_ANGRY,
        HONK_SAD,
        HONK_EXCITED,
        HONK_SLEEPY,
        HONK_QUESTIONING,
        HONK_TRIUMPHANT,
        FOOTSTEP,
        SPLASH,
        EAT,
        YAWN,
        SNORE,
        GIGGLE,
        GASP,
        WHISTLE,
        MELODY_NOTE,
        ACHIEVEMENT,
        LEVEL_UP,
        SECRET_FOUND
    }

    // ============== MELODÍAS ==============

    public enum Melody {
        HAPPY_TUNE,      // Do-Mi-Sol
        SAD_TUNE,        // La-Fa-Re
        VICTORY_TUNE,    // Sol-Do-Mi-Sol (alto)
        MORNING_TUNE,    // Do-Re-Mi
        NIGHT_TUNE,      // Mi-Re-Do
        MISCHIEF_TUNE,   // Fa#-Sol-Fa#
        LOVE_TUNE        // Do-Mi-Sol-Do (alto)
    }

    // ============== ESTADO ==============

    private static Context context;
    private static Random random = new Random();
    private static boolean isInitialized = false;

    // Pitch variations para diferentes emociones
    private static final float PITCH_HAPPY = 1.2f;
    private static final float PITCH_SAD = 0.8f;
    private static final float PITCH_ANGRY = 0.9f;
    private static final float PITCH_EXCITED = 1.4f;
    private static final float PITCH_SLEEPY = 0.7f;
    private static final float PITCH_NORMAL = 1.0f;

    // Melody notes (en términos relativos de pitch)
    private static final float[] MELODY_HAPPY = {1.0f, 1.25f, 1.5f};
    private static final float[] MELODY_SAD = {1.0f, 0.89f, 0.75f};
    private static final float[] MELODY_VICTORY = {1.0f, 1.33f, 1.5f, 2.0f};
    private static final float[] MELODY_MISCHIEF = {1.12f, 1.19f, 1.12f};

    // Secuencia actual
    private static float[] currentMelody = null;
    private static int melodyIndex = 0;
    private static long lastNoteTime = 0;
    private static final long NOTE_DELAY = 300; // ms entre notas

    // ============== INICIALIZACIÓN ==============

    public static void init(Context appContext) {
        context = appContext.getApplicationContext();
        isInitialized = true;
    }

    // ============== REPRODUCCIÓN ==============

    /**
     * Reproducir un tipo de sonido específico.
     */
    public static void play(SoundType type) {
        if (Sound.isSilenced()) return;

        switch (type) {
            case HONK_NORMAL:
                playHonkWithPitch(PITCH_NORMAL);
                break;
            case HONK_HAPPY:
                playHonkWithPitch(PITCH_HAPPY);
                break;
            case HONK_ANGRY:
                playHonkWithPitch(PITCH_ANGRY);
                break;
            case HONK_SAD:
                playHonkWithPitch(PITCH_SAD);
                break;
            case HONK_EXCITED:
                playHonkWithPitch(PITCH_EXCITED);
                break;
            case HONK_SLEEPY:
                playHonkWithPitch(PITCH_SLEEPY);
                break;
            case HONK_QUESTIONING:
                playHonkSequence(new float[]{1.0f, 1.3f}); // Sube al final
                break;
            case HONK_TRIUMPHANT:
                playHonkSequence(MELODY_VICTORY);
                break;
            case FOOTSTEP:
                // No footstep sound available
                break;
            case ACHIEVEMENT:
                playHonkSequence(MELODY_HAPPY);
                break;
            case SECRET_FOUND:
                playHonkSequence(new float[]{0.8f, 1.0f, 1.2f, 1.5f, 2.0f});
                break;
            default:
                Sound.honk();
                break;
        }
    }

    /**
     * Reproducir honk con pitch específico.
     */
    private static void playHonkWithPitch(float pitch) {
        // Por ahora usamos el honk normal - en futuro se puede implementar pitch shifting
        Sound.honk();
    }

    /**
     * Reproducir secuencia de honks (melodía).
     */
    private static void playHonkSequence(float[] melody) {
        currentMelody = melody;
        melodyIndex = 0;
        lastNoteTime = 0;
        // La secuencia se actualiza en update()
    }

    /**
     * Actualizar melodía en progreso.
     */
    public static void update() {
        if (currentMelody == null || melodyIndex >= currentMelody.length) {
            currentMelody = null;
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastNoteTime >= NOTE_DELAY) {
            playHonkWithPitch(currentMelody[melodyIndex]);
            melodyIndex++;
            lastNoteTime = now;
        }
    }

    // ============== MELODÍAS PREDEFINIDAS ==============

    /**
     * Reproducir melodía predefinida.
     */
    public static void playMelody(Melody melody) {
        if (Sound.isSilenced()) return;

        switch (melody) {
            case HAPPY_TUNE:
                playHonkSequence(MELODY_HAPPY);
                break;
            case SAD_TUNE:
                playHonkSequence(MELODY_SAD);
                break;
            case VICTORY_TUNE:
                playHonkSequence(MELODY_VICTORY);
                break;
            case MISCHIEF_TUNE:
                playHonkSequence(MELODY_MISCHIEF);
                break;
            case MORNING_TUNE:
                playHonkSequence(new float[]{1.0f, 1.12f, 1.25f});
                break;
            case NIGHT_TUNE:
                playHonkSequence(new float[]{1.25f, 1.12f, 1.0f});
                break;
            case LOVE_TUNE:
                playHonkSequence(new float[]{1.0f, 1.25f, 1.5f, 2.0f});
                break;
        }
    }

    // ============== HONKS EXPRESIVOS ==============

    /**
     * Honk basado en el estado emocional.
     */
    public static void honkForMood(float happiness) {
        if (happiness > 80) {
            play(SoundType.HONK_EXCITED);
        } else if (happiness > 60) {
            play(SoundType.HONK_HAPPY);
        } else if (happiness > 40) {
            play(SoundType.HONK_NORMAL);
        } else if (happiness > 20) {
            play(SoundType.HONK_SAD);
        } else {
            play(SoundType.HONK_ANGRY);
        }
    }

    /**
     * Honk de reacción a evento.
     */
    public static void honkForEvent(GooseAI.RandomEvent event) {
        if (Sound.isSilenced()) return;

        switch (event) {
            case DANCE:
            case ZOOMIES:
                play(SoundType.HONK_EXCITED);
                break;
            case YAWN:
                play(SoundType.HONK_SLEEPY);
                break;
            case SHAKE:
                play(SoundType.HONK_ANGRY);
                break;
            case RANDOM_HONK:
                play(SoundType.HONK_NORMAL);
                break;
            case STRETCH:
                // Sin sonido
                break;
            case SINGING:
                playMelody(Melody.HAPPY_TUNE);
                break;
            case PLAY_DEAD:
                play(SoundType.HONK_SAD);
                break;
            default:
                play(SoundType.HONK_NORMAL);
                break;
        }
    }

    /**
     * Honk aleatorio variado.
     */
    public static void randomHonk() {
        if (Sound.isSilenced()) return;

        float roll = random.nextFloat();
        if (roll < 0.4f) {
            play(SoundType.HONK_NORMAL);
        } else if (roll < 0.6f) {
            play(SoundType.HONK_HAPPY);
        } else if (roll < 0.75f) {
            play(SoundType.HONK_QUESTIONING);
        } else if (roll < 0.9f) {
            play(SoundType.HONK_EXCITED);
        } else {
            // Honk especial
            playMelody(random.nextBoolean() ? Melody.HAPPY_TUNE : Melody.MISCHIEF_TUNE);
        }
    }

    /**
     * Secuencia de honks rápidos (para zoomies, etc).
     */
    public static void honkBurst(int count) {
        if (Sound.isSilenced()) return;

        float[] burst = new float[count];
        for (int i = 0; i < count; i++) {
            burst[i] = 1.0f + random.nextFloat() * 0.5f;
        }
        playHonkSequence(burst);
    }

    /**
     * Verificar si hay melodía en progreso.
     */
    public static boolean isMelodyPlaying() {
        return currentMelody != null && melodyIndex < currentMelody.length;
    }

    /**
     * Detener melodía actual.
     */
    public static void stopMelody() {
        currentMelody = null;
        melodyIndex = 0;
    }
}
