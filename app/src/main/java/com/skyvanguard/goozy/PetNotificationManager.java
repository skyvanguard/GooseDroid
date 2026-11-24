package com.skyvanguard.goozy;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Gestiona las notificaciones de la mascota virtual.
 * Alerta cuando la mascota necesita atención.
 */
public class PetNotificationManager {

    private static final String CHANNEL_ID = "goozy_pet_channel";
    private static final String CHANNEL_NAME = "Pet Notifications";
    private static final int NOTIFICATION_ID_HUNGRY = 1001;
    private static final int NOTIFICATION_ID_TIRED = 1002;
    private static final int NOTIFICATION_ID_SAD = 1003;
    private static final int NOTIFICATION_ID_REMINDER = 1004;

    private static Context appContext;
    private static boolean notificationsEnabled = true;
    private static Handler checkHandler;
    private static Runnable checkRunnable;

    // Thresholds for notifications
    private static final float HUNGER_THRESHOLD = 80f;
    private static final float ENERGY_THRESHOLD = 20f;
    private static final float HAPPINESS_THRESHOLD = 30f;

    // Cooldown to avoid spamming (milliseconds)
    private static final long NOTIFICATION_COOLDOWN = 30 * 60 * 1000; // 30 minutes
    private static long lastHungryNotification = 0;
    private static long lastTiredNotification = 0;
    private static long lastSadNotification = 0;

    /**
     * Initialize the notification manager.
     */
    public static void init(Context context) {
        appContext = context.getApplicationContext();
        createNotificationChannel();
    }

    /**
     * Create notification channel (required for Android 8+).
     */
    private static void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications when your pet needs attention");
            channel.enableLights(true);
            channel.setLightColor(0xFFFFA500);
            channel.enableVibration(true);

            NotificationManager manager = appContext.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Enable or disable notifications.
     */
    public static void setEnabled(boolean enabled) {
        notificationsEnabled = enabled;
        if (!enabled) {
            cancelAllNotifications();
        }
    }

    /**
     * Check if notifications are enabled.
     */
    public static boolean isEnabled() {
        return notificationsEnabled;
    }

    /**
     * Start periodic checking for pet needs.
     * Should be called when app goes to background.
     */
    public static void startPeriodicCheck() {
        if (checkHandler == null) {
            checkHandler = new Handler(Looper.getMainLooper());
        }

        if (checkRunnable != null) {
            checkHandler.removeCallbacks(checkRunnable);
        }

        checkRunnable = new Runnable() {
            @Override
            public void run() {
                checkPetNeeds();
                checkHandler.postDelayed(this, 5 * 60 * 1000); // Check every 5 minutes
            }
        };

        checkHandler.postDelayed(checkRunnable, 5 * 60 * 1000);
    }

    /**
     * Stop periodic checking.
     * Should be called when app comes to foreground.
     */
    public static void stopPeriodicCheck() {
        if (checkHandler != null) {
            checkHandler.removeCallbacksAndMessages(null);
        }
        checkRunnable = null;
    }

    /**
     * Release all resources and cleanup.
     * Should be called when app is being destroyed.
     */
    public static void cleanup() {
        stopPeriodicCheck();
        cancelAllNotifications();
        cancelScheduledReminder();
        checkHandler = null;
        appContext = null;
    }

    /**
     * Check pet needs and send notifications if necessary.
     */
    public static void checkPetNeeds() {
        if (!notificationsEnabled || appContext == null) return;

        long now = System.currentTimeMillis();

        // Check hunger
        if (PetNeeds.get().hunger >= HUNGER_THRESHOLD && now - lastHungryNotification > NOTIFICATION_COOLDOWN) {
            sendHungryNotification();
            lastHungryNotification = now;
        }

        // Check energy
        if (PetNeeds.get().energy <= ENERGY_THRESHOLD && now - lastTiredNotification > NOTIFICATION_COOLDOWN) {
            sendTiredNotification();
            lastTiredNotification = now;
        }

        // Check happiness
        if (PetNeeds.get().happiness <= HAPPINESS_THRESHOLD && now - lastSadNotification > NOTIFICATION_COOLDOWN) {
            sendSadNotification();
            lastSadNotification = now;
        }
    }

    /**
     * Send hungry notification.
     */
    private static void sendHungryNotification() {
        String title = PetAppearance.get().petName + " is hungry!";
        String message = "Your pet hasn't eaten in a while. Tap to feed them!";
        sendNotification(NOTIFICATION_ID_HUNGRY, title, message, R.drawable.ic_stat_goozy);
    }

    /**
     * Send tired notification.
     */
    private static void sendTiredNotification() {
        String title = PetAppearance.get().petName + " is tired!";
        String message = "Your pet needs some rest. Let them sleep!";
        sendNotification(NOTIFICATION_ID_TIRED, title, message, R.drawable.ic_stat_goozy);
    }

    /**
     * Send sad notification.
     */
    private static void sendSadNotification() {
        String title = PetAppearance.get().petName + " misses you!";
        String message = "Your pet is feeling lonely. Come play with them!";
        sendNotification(NOTIFICATION_ID_SAD, title, message, R.drawable.ic_stat_goozy);
    }

    /**
     * Send a daily reminder notification.
     */
    public static void sendDailyReminder() {
        if (!notificationsEnabled || appContext == null) return;

        String title = "Don't forget " + PetAppearance.get().petName + "!";
        String message = "Your virtual pet is waiting for you. Check in to see how they're doing!";
        sendNotification(NOTIFICATION_ID_REMINDER, title, message, R.drawable.ic_stat_goozy);
    }

    /**
     * Generic notification sender.
     */
    private static void sendNotification(int notificationId, String title, String message, int iconRes) {
        if (appContext == null) return;

        // Create intent to open MainActivity
        Intent intent = new Intent(appContext, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                appContext, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                        PendingIntent.FLAG_IMMUTABLE : 0
        );

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // Show notification
        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(appContext);
            notificationManager.notify(notificationId, builder.build());
        } catch (SecurityException e) {
            // Permission not granted
            e.printStackTrace();
        }
    }

    /**
     * Cancel all notifications.
     */
    public static void cancelAllNotifications() {
        if (appContext == null) return;

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(appContext);
        notificationManager.cancelAll();
    }

    /**
     * Cancel a specific notification.
     */
    public static void cancelNotification(int notificationId) {
        if (appContext == null) return;

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(appContext);
        notificationManager.cancel(notificationId);
    }

    /**
     * Check if notification permission is granted (Android 13+).
     */
    public static boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(appContext).areNotificationsEnabled();
        }
        return true; // Pre-Android 13 doesn't require explicit permission
    }

    /**
     * Schedule a notification to be sent later.
     * Uses AlarmManager for reliable delivery.
     */
    public static void scheduleReminder(long delayMillis) {
        if (appContext == null || !notificationsEnabled) return;

        Intent intent = new Intent(appContext, NotificationReceiver.class);
        intent.setAction("com.skyvanguard.goozy.NOTIFICATION_REMINDER");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                appContext, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                        PendingIntent.FLAG_IMMUTABLE : 0
        );

        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            long triggerTime = System.currentTimeMillis() + delayMillis;
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
    }

    /**
     * Cancel scheduled reminder.
     */
    public static void cancelScheduledReminder() {
        if (appContext == null) return;

        Intent intent = new Intent(appContext, NotificationReceiver.class);
        intent.setAction("com.skyvanguard.goozy.NOTIFICATION_REMINDER");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                appContext, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                        PendingIntent.FLAG_IMMUTABLE : 0
        );

        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    /**
     * BroadcastReceiver for scheduled notifications.
     */
    public static class NotificationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.skyvanguard.goozy.NOTIFICATION_REMINDER".equals(intent.getAction())) {
                appContext = context.getApplicationContext();
                sendDailyReminder();
            }
        }
    }
}
