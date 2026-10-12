package com.skyvanguard.goozy.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.skyvanguard.goozy.PetConfigFile;
import com.skyvanguard.goozy.engine.GooseRenderer;
import com.skyvanguard.goozy.engine.TheGoose;
import com.skyvanguard.goozy.MainActivity;
import com.skyvanguard.goozy.PetNeeds;
import com.skyvanguard.goozy.PetRepository;
import com.skyvanguard.goozy.PetWidget;
import com.skyvanguard.goozy.R;
import com.skyvanguard.goozy.core.Time;
import com.skyvanguard.goozy.core.Vec2;

import java.io.IOException;

/**
 * Dueño del overlay. Vive como foreground service para que el ganso siga en
 * pantalla aunque la Activity se cierre.
 *
 * Usa dos ventanas: una de pantalla completa no táctil para efectos y una chica
 * y táctil que sigue al ganso. Así las apps de abajo siguen recibiendo los toques.
 */
public class GooseOverlayService extends Service implements GooseLayerView.Host {
    private static final String TAG = "GooseOverlayService";

    public static final String ACTION_START = "com.skyvanguard.goozy.overlay.START";
    public static final String ACTION_STOP = "com.skyvanguard.goozy.overlay.STOP";

    private static final String CHANNEL_ID = "goozy_overlay_channel";
    private static final int NOTIFICATION_ID = 4201;

    /** Medio lado del cuerpo del ganso, en unidades de mundo por unidad de DrawScale. */
    private static final float GOOSE_HALF_EXTENT_UNITS = 45f;
    /** Margen de la ventana del ganso alrededor del cuerpo, en píxeles. */
    private static final int GOOSE_WINDOW_MARGIN_PX = 48;
    /** Fracción del margen que el ganso puede recorrer antes de recentrar la ventana. */
    private static final float RECENTER_MARGIN_FRACTION = 0.5f;
    private static final int MIN_GOOSE_WINDOW_PX = 120;
    /**
     * Android 12+ bloquea los toques que atraviesan un overlay no táctil más opaco
     * que esto.
     */
    private static final float PASS_THROUGH_ALPHA = 0.8f;
    private static final float OPAQUE_ALPHA = 1f;
    private static final float DEFAULT_WORLD_SCALE = 2.5f;
    private static final float MIN_WORLD_SCALE = 0.1f;
    private static final float MAX_WORLD_SCALE = 10f;
    private static final long WIDGET_REFRESH_INTERVAL_MS = 60_000L;

    private static final String STATE_PREFS = "goose_overlay";
    private static final String KEY_ENABLED = "enabled";

    private static boolean isServiceRunning = false;
    private static boolean isTouchEnabled = true;
    private static GooseOverlayService activeInstance;

    private WindowManager windowManager;
    private GooseLayerView worldView;
    private GooseLayerView gooseView;
    private WindowManager.LayoutParams worldParams;
    private WindowManager.LayoutParams gooseParams;
    private final int[] worldLocation = new int[2];

    private boolean isFrameLoopActive = false;
    private boolean isGooseInitialized = false;
    private boolean wasMiniGamePlaying = false;
    private long lastWidgetRefreshMs = 0L;
    private int tickErrorCount = 0;

    private final Choreographer.FrameCallback frameCallback = this::onFrame;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                onScreenOff();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                onScreenOn();
            }
        }
    };

    // ============== API ESTÁTICA ==============

    public static boolean isRunning() {
        return isServiceRunning;
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, GooseOverlayService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /** Apagado pedido por el usuario: no vuelve a encenderse solo. */
    public static void stop(Context context) {
        setEnabledByUser(context, false);
        context.stopService(new Intent(context, GooseOverlayService.class));
    }

    /** true si el usuario dejó el ganso encendido (sobrevive a reinicios). */
    public static boolean isEnabledByUser(Context context) {
        return context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    private static void setEnabledByUser(Context context, boolean enabled) {
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** Activa o desactiva los toques sobre el ganso. */
    public static void setTouchEnabled(boolean enabled) {
        isTouchEnabled = enabled;
        if (activeInstance != null) {
            activeInstance.applyTouchMode();
        }
    }

    public static void setWorldScale(float scale) {
        TheGoose.WorldScale = clampWorldScale(scale);
        if (activeInstance != null) {
            activeInstance.applyWorldSize();
        }
    }

    private static float clampWorldScale(float scale) {
        if (Float.isNaN(scale)) return DEFAULT_WORLD_SCALE;
        return Math.max(MIN_WORLD_SCALE, Math.min(scale, MAX_WORLD_SCALE));
    }

    // ============== CICLO DE VIDA ==============

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            // Apagado desde la notificación: es una decisión del usuario
            setEnabledByUser(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }

        startInForeground();

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Sin permiso de overlay; el servicio se detiene");
            stopSelf();
            return START_NOT_STICKY;
        }

        setEnabledByUser(this, true);
        if (worldView == null) {
            try {
                showOverlay();
            } catch (RuntimeException | IOException e) {
                Log.e(TAG, "No se pudo mostrar el overlay", e);
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopFrameLoop();
        try {
            unregisterReceiver(screenReceiver);
        } catch (IllegalArgumentException e) {
            // No llegó a registrarse
        }

        TheGoose.destroy();
        isGooseInitialized = false;

        removeViewSafely(gooseView);
        removeViewSafely(worldView);
        gooseView = null;
        worldView = null;

        activeInstance = null;
        isServiceRunning = false;
        PetWidget.updateAllWidgets(this);
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // La capa WORLD es MATCH_PARENT: su onSizeChanged avisa el nuevo tamaño
        if (worldView != null) {
            worldView.requestLayout();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ============== VENTANAS ==============

    private void showOverlay() throws IOException {
        PetConfigFile config = PetRepository.readConfig(this);
        PetRepository.ensureLoaded(this);
        PetNeeds.get().updateOfflineTime();

        TheGoose.WorldScale = clampWorldScale(parseWorldScale(config.get("DrawSize")));
        String touchable = config.get("TouchableEnabled");
        isTouchEnabled = touchable == null || Boolean.parseBoolean(touchable.trim().toLowerCase());

        worldView = new GooseLayerView(this, GooseRenderer.Layer.WORLD, this);
        gooseView = new GooseLayerView(this, GooseRenderer.Layer.GOOSE, this);

        worldParams = createBaseParams();
        worldParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        worldParams.height = WindowManager.LayoutParams.MATCH_PARENT;

        gooseParams = createBaseParams();
        int size = computeGooseWindowSize();
        gooseParams.width = size;
        gooseParams.height = size;

        applyTouchFlags();

        // Orden de alta = orden Z: el mundo debajo, el ganso encima
        windowManager.addView(worldView, worldParams);
        windowManager.addView(gooseView, gooseParams);

        pendingConfig = config;

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, filter);

        activeInstance = this;
        isServiceRunning = true;
        startFrameLoop();
    }

    private PetConfigFile pendingConfig;

    private WindowManager.LayoutParams createBaseParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params.format = PixelFormat.RGBA_8888;
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return params;
    }

    private static final int COMMON_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

    /**
     * Decide qué ventana recibe toques.
     * - Normal: solo la ventana chica del ganso.
     * - Minijuego: la capa de mundo completa, porque los objetivos están en toda la pantalla.
     * - Toques desactivados: ninguna.
     */
    private void applyTouchFlags() {
        boolean isMiniGame = TheGoose.isMiniGamePlaying() && isTouchEnabled;

        if (isMiniGame) {
            worldParams.flags = COMMON_FLAGS | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            worldParams.alpha = OPAQUE_ALPHA;
        } else {
            worldParams.flags = COMMON_FLAGS | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            worldParams.alpha = passThroughAlpha();
        }

        if (isTouchEnabled && !isMiniGame) {
            gooseParams.flags = COMMON_FLAGS | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            gooseParams.alpha = OPAQUE_ALPHA;
        } else {
            gooseParams.flags = COMMON_FLAGS | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            gooseParams.alpha = passThroughAlpha();
        }
    }

    private static float passThroughAlpha() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PASS_THROUGH_ALPHA : OPAQUE_ALPHA;
    }

    private void applyTouchMode() {
        if (worldView == null || gooseView == null) return;
        applyTouchFlags();
        updateLayoutSafely(worldView, worldParams);
        updateLayoutSafely(gooseView, gooseParams);
    }

    private int computeGooseWindowSize() {
        float halfExtentPx = GOOSE_HALF_EXTENT_UNITS * TheGoose.DrawScale * TheGoose.WorldScale;
        int size = Math.round(2f * (halfExtentPx + GOOSE_WINDOW_MARGIN_PX));
        return Math.max(MIN_GOOSE_WINDOW_PX, size);
    }

    /** Mantiene la ventana chica sobre el ganso. */
    private void followGoose() {
        Vec2 pos = TheGoose.getGoosePos();
        if (pos == null || gooseView == null) return;

        int size = computeGooseWindowSize();
        int centerX = getWorldOriginX() + Math.round(pos.x * TheGoose.WorldScale);
        int centerY = getWorldOriginY() + Math.round(pos.y * TheGoose.WorldScale);

        int windowCenterX = gooseParams.x + gooseParams.width / 2;
        int windowCenterY = gooseParams.y + gooseParams.height / 2;
        float slack = GOOSE_WINDOW_MARGIN_PX * RECENTER_MARGIN_FRACTION;

        boolean isSizeChanged = size != gooseParams.width;
        boolean isOffCenter = Math.abs(centerX - windowCenterX) > slack
                || Math.abs(centerY - windowCenterY) > slack;
        if (!isSizeChanged && !isOffCenter) return;

        gooseParams.width = size;
        gooseParams.height = size;
        gooseParams.x = centerX - size / 2;
        gooseParams.y = centerY - size / 2;
        updateLayoutSafely(gooseView, gooseParams);
    }

    private void applyWorldSize() {
        if (worldView == null || worldView.getWidth() == 0) return;
        onWorldLayerSizeChanged(worldView.getWidth(), worldView.getHeight());
    }

    // ============== GooseLayerView.Host ==============

    @Override
    public int getWorldOriginX() {
        return worldLocation[0];
    }

    @Override
    public int getWorldOriginY() {
        return worldLocation[1];
    }

    @Override
    public void onWorldLayerSizeChanged(int widthPx, int heightPx) {
        if (widthPx <= 0 || heightPx <= 0) return;
        int worldWidth = Math.max(1, Math.round(widthPx / TheGoose.WorldScale));
        int worldHeight = Math.max(1, Math.round(heightPx / TheGoose.WorldScale));

        if (!isGooseInitialized) {
            try {
                TheGoose.Init(this, pendingConfig, worldWidth, worldHeight);
                isGooseInitialized = true;
            } catch (RuntimeException e) {
                Log.e(TAG, "Error inicializando el ganso", e);
                stopSelf();
            }
        } else {
            TheGoose.onWorldSizeChanged(worldWidth, worldHeight);
        }
    }

    // ============== LOOP DE FRAMES ==============

    private void startFrameLoop() {
        if (isFrameLoopActive) return;
        isFrameLoopActive = true;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private void stopFrameLoop() {
        isFrameLoopActive = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
    }

    private void onFrame(long frameTimeNanos) {
        if (!isFrameLoopActive) return;

        if (isGooseInitialized && worldView != null) {
            worldView.getLocationOnScreen(worldLocation);
            try {
                Time.tick(frameTimeNanos);
                TheGoose.Tick();
                TheGoose.PrepareFrame();
            } catch (RuntimeException e) {
                tickErrorCount++;
                if (tickErrorCount == 1 || tickErrorCount % 300 == 0) {
                    Log.e(TAG, "Error during tick (x" + tickErrorCount + ")", e);
                }
            }

            boolean isMiniGame = TheGoose.isMiniGamePlaying();
            if (isMiniGame != wasMiniGamePlaying) {
                wasMiniGamePlaying = isMiniGame;
                applyTouchMode();
            }

            followGoose();
            worldView.invalidate();
            gooseView.invalidate();
            refreshWidgetIfDue();
        }

        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private void refreshWidgetIfDue() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastWidgetRefreshMs < WIDGET_REFRESH_INTERVAL_MS) return;
        lastWidgetRefreshMs = now;
        PetWidget.updateAllWidgets(this);
    }

    private void onScreenOff() {
        stopFrameLoop();
        TheGoose.saveState();
    }

    private void onScreenOn() {
        PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (power != null && !power.isInteractive()) return;
        // El tiempo con la pantalla apagada cuenta como tiempo offline
        PetNeeds.get().updateOfflineTime();
        startFrameLoop();
    }

    // ============== NOTIFICACIÓN ==============

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.OverlayChannelName),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.OverlayChannelDescription));
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void startInForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        int immutable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0;

        PendingIntent openApp = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | immutable);

        PendingIntent talk = PendingIntent.getActivity(this, 2,
                com.skyvanguard.goozy.QuickChatActivity.createIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT | immutable);

        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, GooseOverlayService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | immutable);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_goozy)
                .setContentTitle(getString(R.string.OverlayNotificationTitle))
                .setContentText(getString(R.string.OverlayNotificationText))
                .setContentIntent(openApp)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, getString(R.string.OverlayNotificationTalk), talk)
                .addAction(0, getString(R.string.OverlayNotificationStop), stop)
                .build();
    }

    // ============== UTILIDADES ==============

    private static float parseWorldScale(String value) {
        if (value == null || value.isEmpty()) return DEFAULT_WORLD_SCALE;
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_WORLD_SCALE;
        }
    }

    private void updateLayoutSafely(GooseLayerView view, WindowManager.LayoutParams params) {
        if (view == null || !view.isAttachedToWindow()) return;
        try {
            windowManager.updateViewLayout(view, params);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "La ventana ya no está adjunta", e);
        }
    }

    private void removeViewSafely(GooseLayerView view) {
        if (view == null) return;
        try {
            windowManager.removeView(view);
        } catch (IllegalArgumentException e) {
            // Nunca llegó a agregarse
        }
    }
}
