package com.skyvanguard.goozy;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

/**
 * Widget de Android para mostrar el estado de la mascota
 * y proporcionar accesos rápidos a las acciones principales.
 */
public class PetWidget extends AppWidgetProvider {

    public static final String ACTION_FEED = "com.skyvanguard.goozy.ACTION_FEED";
    public static final String ACTION_PLAY = "com.skyvanguard.goozy.ACTION_PLAY";
    public static final String ACTION_SLEEP = "com.skyvanguard.goozy.ACTION_SLEEP";
    public static final String ACTION_REFRESH = "com.skyvanguard.goozy.ACTION_REFRESH";
    static final float MIN_ENERGY_TO_PLAY = 20f;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // Este receptor está exportado (lo exige el sistema para los widgets), así
        // que solo atiende las actualizaciones del sistema. Los botones van a
        // PetWidgetActionReceiver, que no es alcanzable desde otras apps.
        super.onReceive(context, intent);
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        PetRepository.ensureLoaded(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.pet_widget);

        // Update pet name and status
        String petName = PetAppearance.get().petName;
        String mood = getMoodEmoji();
        views.setTextViewText(R.id.widget_pet_name, petName + " " + mood);

        // Update status text
        String statusText = getStatusText();
        views.setTextViewText(R.id.widget_status, statusText);

        // Update progress bars
        int hungerProgress = (int) (100 - PetNeeds.get().hunger);
        int energyProgress = (int) PetNeeds.get().energy;
        int happinessProgress = (int) PetNeeds.get().happiness;

        views.setProgressBar(R.id.widget_hunger_bar, 100, hungerProgress, false);
        views.setProgressBar(R.id.widget_energy_bar, 100, energyProgress, false);
        views.setProgressBar(R.id.widget_happiness_bar, 100, happinessProgress, false);

        // Set click intents for buttons
        views.setOnClickPendingIntent(R.id.widget_feed_button,
                getPendingSelfIntent(context, ACTION_FEED));
        views.setOnClickPendingIntent(R.id.widget_play_button,
                getPendingSelfIntent(context, ACTION_PLAY));
        views.setOnClickPendingIntent(R.id.widget_sleep_button,
                getPendingSelfIntent(context, ACTION_SLEEP));

        // Set click intent for the whole widget to open app
        Intent openAppIntent = new Intent(context, MainActivity.class);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE :
                        PendingIntent.FLAG_UPDATE_CURRENT
        );
        views.setOnClickPendingIntent(R.id.widget_pet_name, openAppPendingIntent);

        // Update widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static PendingIntent getPendingSelfIntent(Context context, String action) {
        Intent intent = new Intent(context, PetWidgetActionReceiver.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(
                context, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE :
                        PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private static String getMoodEmoji() {
        PetNeeds.MoodState mood = PetNeeds.get().getMoodState();
        switch (mood) {
            case HUNGRY: return "\uD83D\uDE1F"; // Worried face
            case TIRED: return "\uD83D\uDE34"; // Sleeping face
            case SAD: return "\uD83D\uDE22"; // Crying face
            case HAPPY: return "\uD83D\uDE0A"; // Smiling face
            case DIRTY: return "\uD83D\uDCA9"; // Pile of poo
            case SICK: return "\uD83E\uDD12"; // Face with thermometer
            default: return "\uD83D\uDC23"; // Baby chick
        }
    }

    private static String getStatusText() {
        PetNeeds.MoodState mood = PetNeeds.get().getMoodState();
        switch (mood) {
            case HUNGRY: return "I'm hungry!";
            case TIRED: return "I need sleep...";
            case SAD: return "Play with me!";
            case HAPPY: return "I'm happy!";
            case DIRTY: return "I need a bath!";
            case SICK: return "I feel sick...";
            default: return "Doing fine~";
        }
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, PetWidget.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onEnabled(Context context) {
        // Widget first instance created
    }

    @Override
    public void onDisabled(Context context) {
        // Last widget instance deleted
    }
}
