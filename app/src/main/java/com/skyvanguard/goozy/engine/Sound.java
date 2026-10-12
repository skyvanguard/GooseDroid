package com.skyvanguard.goozy.engine;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.Random;

/**
 * Efectos de sonido del ganso.
 *
 * Hay cuatro sonidos base, sintetizados por {@code tools/gen_sounds.py}; la
 * variedad sale de reproducirlos a distinta velocidad (más agudo o más grave).
 */
public final class Sound {
    private static final String TAG = "Sound";
    private static final int MAX_STREAMS = 4;
    private static final long CHIME_ECHO_DELAY_MS = 180;

    private enum Cue {
        HONK("sound/honk.wav"),
        PAT("sound/pat.wav"),
        MUD("sound/mud.wav"),
        CHIME("sound/chime.wav");

        final String asset;

        Cue(String asset) {
            this.asset = asset;
        }
    }

    private static final Random random = new Random();
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final int[] soundIds = new int[Cue.values().length];

    private static SoundPool pool;
    private static boolean silenced;

    private Sound() {
    }

    public static void init(Context context, boolean silent) {
        release();
        silenced = silent;
        pool = new SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        for (Cue cue : Cue.values()) {
            soundIds[cue.ordinal()] = load(context, cue);
        }
    }

    public static void release() {
        handler.removeCallbacksAndMessages(null);
        if (pool != null) {
            pool.release();
            pool = null;
        }
    }

    public static void setSilenceMode(boolean silent) {
        silenced = silent;
    }

    public static boolean isSilenced() {
        return silenced;
    }

    public static void honk() {
        play(Cue.HONK, 0.8f, randomRate(0.9f, 1.15f));
    }

    public static void happy() {
        play(Cue.HONK, 0.45f, randomRate(1.25f, 1.45f));
    }

    public static void sad() {
        play(Cue.HONK, 0.35f, randomRate(0.65f, 0.75f));
    }

    public static void playful() {
        play(Cue.HONK, 0.5f, randomRate(1.1f, 1.3f));
    }

    public static void pat() {
        play(Cue.PAT, 0.7f, randomRate(0.85f, 1.2f));
    }

    public static void mud() {
        play(Cue.MUD, 0.6f, randomRate(0.9f, 1.1f));
    }

    public static void score() {
        play(Cue.CHIME, 0.4f, 1f);
    }

    /** Campanita y un graznido de festejo. */
    public static void achievement() {
        play(Cue.CHIME, 0.55f, 1f);
        handler.postDelayed(Sound::happy, CHIME_ECHO_DELAY_MS);
    }

    private static int load(Context context, Cue cue) {
        try (AssetFileDescriptor fd = context.getAssets().openFd(cue.asset)) {
            return pool.load(fd, 1);
        } catch (IOException e) {
            Log.e(TAG, "No se pudo cargar " + cue.asset, e);
            return 0;
        }
    }

    private static void play(Cue cue, float volume, float rate) {
        int id = soundIds[cue.ordinal()];
        if (silenced || pool == null || id == 0) return;
        pool.play(id, volume, volume, 1, 0, rate);
    }

    private static float randomRate(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }
}
