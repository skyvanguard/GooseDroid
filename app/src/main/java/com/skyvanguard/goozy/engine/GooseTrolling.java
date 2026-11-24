package com.skyvanguard.goozy.engine;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.skyvanguard.goozy.MainActivity;
import com.skyvanguard.goozy.R;

import java.util.Random;

/**
 * Sistema de trolleo del ganso.
 * Incluye notificaciones falsas, vibración, y mensajes trolleadores.
 */
public class GooseTrolling {

    private static final String TAG = "GooseTrolling";
    private static final String TROLL_CHANNEL_ID = "goose_troll_channel";
    private static final int TROLL_NOTIFICATION_ID = 9999;

    private static Context context;
    private static Handler trollHandler;
    private static Random random = new Random();
    private static boolean trollingEnabled = true;
    private static long lastTrollTime = 0;
    private static final long TROLL_COOLDOWN = 60000; // 1 minuto entre trolleos
    /** Una notificación falsa es más invasiva que una vibración: como mucho cada media hora. */
    private static final long TROLL_NOTIFICATION_COOLDOWN_MS = 30 * 60_000L;
    private static long lastTrollNotificationTime = 0;

    // ============== MENSAJES TROLLEADORES ==============

    private static final String[] TROLL_NOTIFICATION_TITLES = {
            "CONGRATULATIONS!",
            "You have won!",
            "Important Message",
            "System Alert",
            "Free Gift!",
            "Warning!",
            "Your phone has a virus!",
            "New message from: Unknown",
            "Limited Time Offer!",
            "Honk Honk!",
            "Urgent: Read Now",
            "You've been selected!",
            "Breaking News!",
            "Secret Message",
            "Click Here NOW"
    };

    private static final String[] TROLL_NOTIFICATION_MESSAGES = {
            "Just kidding! It's just me, the goose. Honk!",
            "Ha! Made you look! - Your friendly neighborhood goose",
            "Chaos is a love language. - Goozy",
            "I'm a goose. Being a menace is my cardio.",
            "Gotcha! Now pet me.",
            "This is not a drill! ...It's a goose.",
            "Error 404: Your attention not found. HONK!",
            "Your phone is fine. But are YOU fine?",
            "The goose demands tribute (snacks).",
            "Beep boop. Goozy needs a snack.",
            "Surprise! It's goose time!",
            "No virus here, just chaos.",
            "You fell for it! Classic human.",
            "The goose sees all. The goose knows all.",
            "This message will self-destruct in... just kidding, HONK!"
    };

    private static final String[] CLIPBOARD_TROLLS = {
            "HONK HONK HONK",
            "Goozy was here.",
            "Chaos is a love language.",
            "The goose was here.",
            "*aggressive honking*",
            "Certified menace, certified cute",
            "You've been goosed!",
            "honk honk, it's goose o'clock",
            "bread first, questions later",
            "Mess with the honk, get the bonk"
    };

    private static final String[] GOOSE_QUOTES = {
            "\"Bread first, questions later.\" - Goozy",
            "\"Honk.\" - Ancient Goose Proverb",
            "\"To honk or not to honk? HONK.\" - Goosespeare",
            "\"I think, therefore I honk.\" - René Gooscartes",
            "\"That's one small step for goose, one giant HONK for goosekind.\"",
            "\"In the beginning, there was HONK.\"",
            "\"The only thing we have to fear is... HONK!\"",
            "\"Ask not what your goose can honk for you...\"",
            "\"I have a dream... of unlimited bread.\"",
            "\"Et tu, Goose?\" - Julius Honksar"
    };

    // ============== SPANISH MEME MESSAGES ==============

    private static final String[] SPANISH_TROLL_TITLES = {
            "FELICIDADES!",
            "Has ganado un iPhone!",
            "Mensaje importante",
            "Alerta del sistema",
            "Regalo gratis!",
            "Advertencia!",
            "Tu telefono tiene virus!",
            "Nuevo mensaje",
            "Oferta limitada!",
            "HONK HONK!"
    };

    private static final String[] SPANISH_TROLL_MESSAGES = {
            "Era broma! Solo soy el ganso. Cuac!",
            "Te hice mirar! - Tu ganso amigable",
            "La paz nunca fue una opcion. - El Ganso",
            "Soy un ganso. Causo problemas a proposito.",
            "Te atrape! Ahora acariciame.",
            "Esto no es un simulacro! ...Es un ganso.",
            "Error 404: Tu atencion no encontrada. HONK!",
            "Tu telefono esta bien. Pero TU estas bien?",
            "El ganso demanda tributo (snacks).",
            "Sorpresa! Es hora del ganso!",
            "Caiste! Clasico humano.",
            "El ganso lo ve todo. El ganso lo sabe todo."
    };

    // ============== INICIALIZACIÓN ==============

    /**
     * Inicializar el sistema de trolleo.
     */
    public static void init(Context appContext) {
        context = appContext.getApplicationContext();
        trollHandler = new Handler(Looper.getMainLooper());
        createTrollNotificationChannel();
        Log.i(TAG, "Troll system initialized");
    }

    /**
     * Crear canal de notificaciones para trolleos.
     */
    private static void createTrollNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && context != null) {
            NotificationChannel channel = new NotificationChannel(
                    TROLL_CHANNEL_ID,
                    "Goose Messages",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Important messages from your goose");

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    // ============== TROLLEOS PRINCIPALES ==============

    /**
     * Ejecutar un trolleo aleatorio.
     */
    public static void doRandomTroll() {
        if (!canTroll()) return;

        int trollType = random.nextInt(5);
        switch (trollType) {
            case 0:
                trySendTrollNotification();
                break;
            case 1:
                vibrateHonk();
                break;
            case 2:
                vibratePattern();
                break;
            case 3:
                trySendTrollNotification(); // Doble chance de notificación
                break;
            case 4:
                vibrateSOS();
                break;
        }

        lastTrollTime = System.currentTimeMillis();
    }

    /**
     * Verificar si se puede trollear (cooldown y permisos).
     */
    private static boolean canTroll() {
        if (!trollingEnabled || context == null) return false;

        long now = System.currentTimeMillis();
        return (now - lastTrollTime) > TROLL_COOLDOWN;
    }

    // ============== NOTIFICACIONES FALSAS ==============

    /**
     * Envía una notificación troll si pasó el cooldown.
     *
     * @return true si se envió
     */
    public static boolean trySendTrollNotification() {
        if (!trollingEnabled || context == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastTrollNotificationTime < TROLL_NOTIFICATION_COOLDOWN_MS) return false;
        lastTrollNotificationTime = now;
        sendTrollNotification();
        return true;
    }

    /**
     * Enviar una notificación troll.
     */
    private static void sendTrollNotification() {
        if (context == null) return;

        try {
            // Elegir si usar español o inglés aleatoriamente
            boolean useSpanish = random.nextBoolean();

            String title, message;
            if (useSpanish) {
                title = SPANISH_TROLL_TITLES[random.nextInt(SPANISH_TROLL_TITLES.length)];
                message = SPANISH_TROLL_MESSAGES[random.nextInt(SPANISH_TROLL_MESSAGES.length)];
            } else {
                title = TROLL_NOTIFICATION_TITLES[random.nextInt(TROLL_NOTIFICATION_TITLES.length)];
                message = TROLL_NOTIFICATION_MESSAGES[random.nextInt(TROLL_NOTIFICATION_MESSAGES.length)];
            }

            // Intent para abrir la app
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                            PendingIntent.FLAG_IMMUTABLE : 0
            );

            // Construir notificación
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, TROLL_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_goozy)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

            // Mostrar notificación
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            // ID fijo: una nueva reemplaza a la anterior en vez de apilarse
            notificationManager.notify(TROLL_NOTIFICATION_ID, builder.build());

            Log.d(TAG, "Troll notification sent: " + title);
        } catch (SecurityException e) {
            Log.w(TAG, "No notification permission", e);
        } catch (Exception e) {
            Log.e(TAG, "Error sending troll notification", e);
        }
    }

    /**
     * Enviar notificación de cita del ganso.
     */
    public static void sendGooseQuote() {
        if (context == null) return;

        try {
            String quote = GOOSE_QUOTES[random.nextInt(GOOSE_QUOTES.length)];

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, TROLL_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_goozy)
                    .setContentTitle("Wisdom from The Goose")
                    .setContentText(quote)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(quote))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setAutoCancel(true);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.notify(TROLL_NOTIFICATION_ID + 200, builder.build());
        } catch (Exception e) {
            Log.e(TAG, "Error sending quote notification", e);
        }
    }

    // ============== VIBRACIÓN ==============

    /**
     * Vibrar al hacer honk.
     */
    public static void vibrateHonk() {
        vibrate(new long[]{0, 100, 50, 100}); // Honk honk pattern
    }

    /**
     * Vibrar patrón de S.O.S.
     */
    public static void vibrateSOS() {
        // ... --- ... (SOS en morse)
        long[] pattern = {
                0,
                100, 100, 100, 100, 100, 100, // S (...)
                200,
                300, 100, 300, 100, 300, 100, // O (---)
                200,
                100, 100, 100, 100, 100, 100  // S (...)
        };
        vibrate(pattern);
    }

    /**
     * Vibrar patrón aleatorio divertido.
     */
    public static void vibratePattern() {
        int patternType = random.nextInt(4);
        long[] pattern;

        switch (patternType) {
            case 0: // Heartbeat
                pattern = new long[]{0, 100, 100, 200, 400, 100, 100, 200};
                break;
            case 1: // Rapid fire
                pattern = new long[]{0, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50};
                break;
            case 2: // Dramatic pause
                pattern = new long[]{0, 500, 500, 500};
                break;
            case 3: // Excited
            default:
                pattern = new long[]{0, 100, 50, 100, 50, 100, 50, 300};
                break;
        }

        vibrate(pattern);
    }

    /**
     * Vibrar con un patrón específico.
     */
    private static void vibrate(long[] pattern) {
        if (context == null) return;

        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vibrator.vibrate(pattern, -1);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error vibrating", e);
        }
    }

    /**
     * Vibración simple.
     */
    public static void vibrateSimple(long durationMs) {
        if (context == null) return;

        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs,
                            VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(durationMs);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error vibrating", e);
        }
    }

    // ============== MENSAJES PARA COPIAR ==============

    /**
     * Obtener un mensaje troll aleatorio para el portapapeles.
     */
    public static String getRandomClipboardTroll() {
        return CLIPBOARD_TROLLS[random.nextInt(CLIPBOARD_TROLLS.length)];
    }

    /**
     * Obtener una cita del ganso.
     */
    public static String getRandomGooseQuote() {
        return GOOSE_QUOTES[random.nextInt(GOOSE_QUOTES.length)];
    }

    // ============== TROLLEO PROGRAMADO ==============

    /**
     * Programar un trolleo aleatorio en el futuro.
     */
    public static void scheduleRandomTroll(long delayMs) {
        if (trollHandler == null) return;

        trollHandler.postDelayed(() -> {
            if (trollingEnabled) {
                doRandomTroll();
            }
        }, delayMs);
    }

    /**
     * Programar trolleos periódicos mientras la app está activa.
     */
    public static void startPeriodicTrolling(long intervalMs) {
        if (trollHandler == null) return;

        Runnable trollRunnable = new Runnable() {
            @Override
            public void run() {
                if (trollingEnabled && random.nextFloat() < 0.3f) { // 30% chance
                    doRandomTroll();
                }
                trollHandler.postDelayed(this, intervalMs);
            }
        };

        trollHandler.postDelayed(trollRunnable, intervalMs);
    }

    /**
     * Detener trolleos periódicos.
     */
    public static void stopPeriodicTrolling() {
        if (trollHandler != null) {
            trollHandler.removeCallbacksAndMessages(null);
        }
    }

    // ============== CONFIGURACIÓN ==============

    /**
     * Habilitar/deshabilitar trolleos.
     */
    public static void setTrollingEnabled(boolean enabled) {
        trollingEnabled = enabled;
        if (!enabled) {
            stopPeriodicTrolling();
        }
    }

    /**
     * Verificar si el trolleo está habilitado.
     */
    public static boolean isTrollingEnabled() {
        return trollingEnabled;
    }

    /**
     * Limpiar recursos.
     */
    public static void cleanup() {
        stopPeriodicTrolling();
        trollHandler = null;
        context = null;
    }
}
