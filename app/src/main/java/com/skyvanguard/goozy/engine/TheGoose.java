package com.skyvanguard.goozy.engine;

import static com.skyvanguard.goozy.MainActivity.string2boolean;

import android.content.*;
import android.graphics.*;
import com.skyvanguard.goozy.*;
import com.skyvanguard.goozy.core.*;
import com.skyvanguard.goozy.engine.body.BodyState;
import com.skyvanguard.goozy.engine.body.GooseModel;
import com.skyvanguard.goozy.engine.body.Species;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main coordinator class for the goose virtual pet.
 * Manages all modules, events, lifecycle, achievements, and statistics.
 */
public class TheGoose implements
        GooseAI.AICallback,
        GooseTouchHandler.TouchCallback,
        GoosePhysics.PhysicsCallback,
        GooseTouchHandler.GestureListener,
        MiniGames.GameCallback {

    // ============== LIFECYCLE STATES ==============

    /**
     * Application lifecycle states.
     */
    public enum LifecycleState {
        UNINITIALIZED,
        INITIALIZING,
        RUNNING,
        PAUSED,
        STOPPED,
        DESTROYED
    }

    // ============== EVENT SYSTEM ==============

    /**
     * Types of events that can be broadcast.
     */
    public enum EventType {
        // Lifecycle events
        INITIALIZED,
        PAUSED,
        RESUMED,
        DESTROYED,

        // State events
        TASK_CHANGED,
        MOOD_CHANGED,
        NEEDS_CRITICAL,

        // Interaction events
        TOUCHED,
        PETTED,
        FED,
        PLAYED,

        // Physics events
        JUMPED,
        LANDED,
        BOUNCED,
        HIT_EDGE,
        THROWN,

        // Achievement events
        ACHIEVEMENT_UNLOCKED,
        MILESTONE_REACHED,

        // Special events
        CELEBRATION,
        LEVEL_UP,
        NEW_ACCESSORY
    }

    /**
     * Event data container.
     */
    public static class GameEvent {
        public final EventType type;
        public final Object data;
        public final long timestamp;

        public GameEvent(EventType type, Object data) {
            this.type = type;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Event listener interface.
     */
    public interface EventListener {
        void onEvent(GameEvent event);
    }

    // ============== ACHIEVEMENT SYSTEM ==============

    /**
     * Achievement definitions.
     */
    public enum Achievement {
        // Pet interactions
        FIRST_PET("First Pet", "Pet your goose for the first time", 1, "\uD83D\uDC4B"),
        PET_MASTER("Pet Master", "Pet your goose 100 times", 100, "\uD83E\uDD73"),
        PET_LEGEND("Pet Legend", "Pet your goose 500 times", 500, "\u2728"),
        BOOP_CHAMPION("Boop Champion", "Boop the snoot 50 times", 50, "\uD83D\uDC43"),
        BOOP_MASTER("Boop Master", "Boop 200 times", 200, "\uD83D\uDCA5"),

        // Combos and streaks
        COMBO_KING("Combo King", "Reach a 10x combo", 10, "\uD83D\uDD25"),
        COMBO_LEGEND("Combo Legend", "Reach a 25x combo", 25, "\uD83C\uDF1F"),
        COMBO_GOD("Combo God", "Reach a 50x combo", 50, "\uD83D\uDC51"),

        // Physics interactions
        HIGH_FLYER("High Flyer", "Make your goose jump 50 times", 50, "\uD83E\uDD85"),
        SKY_KING("Sky King", "Make your goose jump 200 times", 200, "\uD83D\uDE80"),
        THROWER("Yeet Master", "Throw your goose 25 times", 25, "\uD83D\uDCA8"),
        LAUNCH_PRO("Launch Pro", "Throw your goose 100 times", 100, "\uD83C\uDFAF"),
        BOUNCY("Bouncy Birb", "Bounce off edges 50 times", 50, "\uD83C\uDFB1"),

        // Care activities
        HAPPY_GOOSE("Happy Goose", "Keep happiness at 100% for 5 minutes", 300, "\uD83D\uDE0A"),
        BLISSFUL("Blissful", "Keep happiness at 100% for 30 minutes", 1800, "\uD83D\uDE07"),
        WELL_FED("Well Fed", "Feed your goose 50 times", 50, "\uD83C\uDF7D\uFE0F"),
        GOURMET("Gourmet", "Feed your goose 200 times", 200, "\uD83D\uDC68\u200D\uD83C\uDF73"),
        SLEEPY_TIME("Sleepy Time", "Let your goose sleep 10 times", 10, "\uD83D\uDE34"),
        SLEEP_KING("Sleep King", "Let your goose sleep 50 times", 50, "\uD83D\uDCA4"),

        // Time-based
        PLAY_TIME("Play Time", "Play for 30 minutes total", 1800, "\u23F1\uFE0F"),
        MARATHON("Marathon", "Play for 5 hours total", 18000, "\uD83C\uDFC3"),
        DEDICATED("Dedicated Owner", "Interact 1000 times", 1000, "\uD83D\uDC96"),
        LOYAL_FRIEND("Loyal Friend", "Interact 5000 times", 5000, "\uD83E\uDD1D"),
        OBSESSED("Obsessed", "Interact 10000 times", 10000, "\uD83E\uDDE0"),

        // Discovery
        EXPLORER("Explorer", "Trigger all random events", 13, "\uD83D\uDDFA\uFE0F"),
        FASHIONISTA("Fashionista", "Try all accessories", 8, "\uD83D\uDC57"),

        // Skills
        SPEEDSTER("Speedster", "Make goose run at max speed", 1, "\u26A1"),
        CIRCLE_MASTER("Circle Master", "Do 20 circle gestures", 20, "\uD83D\uDD04"),
        GESTURE_PRO("Gesture Pro", "Perform 100 gestures total", 100, "\uD83D\uDC4C"),

        // Evolution
        BABY_STEPS("Baby Steps", "Evolve from egg to gosling", 1, "\uD83D\uDC23"),
        GROWING_UP("Growing Up", "Reach adult stage", 1, "\uD83E\uDD86"),
        ELDER_WISDOM("Elder Wisdom", "Reach elder stage", 1, "\uD83E\uDDD3"),
        COSMIC_GOOSE("Cosmic Goose", "Reach cosmic stage", 1, "\uD83C\uDF0C"),

        // Special moments
        NIGHT_OWL("Night Owl", "Play between midnight and 4am", 1, "\uD83E\uDD89"),
        EARLY_BIRD("Early Bird", "Play between 5am and 7am", 1, "\uD83C\uDF05"),
        WEEKEND_WARRIOR("Weekend Warrior", "Play 3 weekends in a row", 3, "\uD83C\uDFAE"),
        DAILY_DEVOTION("Daily Devotion", "Play 7 days in a row", 7, "\uD83D\uDCC6"),
        MONTH_MASTER("Month Master", "Play 30 days in a row", 30, "\uD83C\uDFC6");

        public final String name;
        public final String description;
        public final int requirement;
        public final String icon;

        Achievement(String name, String description, int requirement, String icon) {
            this.name = name;
            this.description = description;
            this.requirement = requirement;
            this.icon = icon;
        }
    }

    // ============== STATISTICS ==============

    /**
     * Game statistics tracker.
     */
    public static class Statistics {
        // Time stats
        public long totalPlayTimeMs = 0;
        public long sessionStartTime = 0;
        public long lastSaveTime = 0;

        // Interaction stats
        public int totalTouches = 0;
        public int totalPets = 0;
        public int totalBoops = 0;
        public int totalThrows = 0;
        public int totalFeedings = 0;
        public int totalPlaySessions = 0;
        public int totalSleepSessions = 0;

        // Physics stats
        public int totalJumps = 0;
        public int totalBounces = 0;
        public int totalEdgeHits = 0;
        public float maxSpeed = 0;
        public float totalDistanceTraveled = 0;

        // Combo stats
        public int highestCombo = 0;
        public int totalCombos = 0;

        // Happiness stats
        public long timeAtMaxHappiness = 0;
        public float averageHappiness = 50f;

        // Misc stats
        public int randomEventsTriggered = 0;
        public int accessoriesUnlocked = 0;
        public int achievementsUnlocked = 0;

        public void updateAverageHappiness(float current) {
            averageHappiness = averageHappiness * 0.99f + current * 0.01f;
        }
    }

    // ============== CONSTANTS ==============

    private static final long AUTO_SAVE_INTERVAL_MS = 60000; // 1 minute
    private static final String ACHIEVEMENT_KEY_PREFIX = "Ach_";
    private static final float DEBUG_TEXT_SIZE = 12f;

    // ============== MODULES ==============

    private static GoosePhysics physics;
    private static GooseAI ai;
    private static GooseRenderer renderer;
    private static GooseTouchHandler touchHandler;
    private static GooseModel body;
    private static final float[] stepBuffer = new float[4];
    private static final float MIN_SMALL_STAGE_SCALE = 2.4f;
    private static GooseBehaviorTree behaviorTree;

    // ============== THOUGHT SYSTEM ==============

    private static String currentThought = "";
    private static float thoughtDisplayTime = 0f;
    private static float timeSinceThought = 0f;
    private static final float THOUGHT_DISPLAY_DURATION = 3.5f;
    private static final float THOUGHT_INTERVAL_MIN = 8f;
    private static final float THOUGHT_INTERVAL_MAX = 20f;
    private static float nextThoughtTime = 10f;

    // ============== ACHIEVEMENT NOTIFICATION SYSTEM ==============

    /**
     * Represents a pending achievement notification.
     */
    public static class AchievementNotification {
        public final Achievement achievement;
        public float displayTime;
        public float animPhase;  // 0 = slide in, 1 = visible, 2 = slide out
        public boolean completed;

        public AchievementNotification(Achievement achievement) {
            this.achievement = achievement;
            this.displayTime = 0f;
            this.animPhase = 0f;
            this.completed = false;
        }
    }

    private static List<AchievementNotification> notificationQueue = new ArrayList<>();
    private static AchievementNotification currentNotification = null;
    private static final float NOTIFICATION_SLIDE_TIME = 0.4f;
    private static final float NOTIFICATION_DISPLAY_TIME = 3.0f;

    // ============== CONTEXT ==============

    private static Canvas canvas;
    private static Context ctx;
    private static PetConfigFile ca;
    private static int screenWidth;
    private static int screenHeight;

    // ============== STATE ==============

    private static LifecycleState lifecycleState = LifecycleState.UNINITIALIZED;
    public static boolean petModeEnabled = true;
    private static boolean debugMode = false;
    private static boolean isPaused = false;

    // ============== COLORS ==============

    public static int FootColor = 0xFFFFA500;
    public static int OutLineColor = 0xFFD3D3D3;
    public static int MouthColor = 0xFFFFA500;
    public static int EyeColor = 0xFF000000;
    private static final int DEFAULT_BODY_COLOR = 0xFFFFFFFF;
    public static int BodyColor = DEFAULT_BODY_COLOR;

    // ============== CONFIGURABLE VALUES ==============

    public static final float BASE_DRAW_SCALE = 2.5f;
    public static final float DEFAULT_WANDER_SPEED = 200f;
    public static float DrawScale = BASE_DRAW_SCALE;
    public static float WanderSpeed = DEFAULT_WANDER_SPEED;

    /** Píxeles de pantalla por unidad de mundo (el "DrawSize" de la configuración). */
    public static float WorldScale = 2.5f;

    // ============== EVENT SYSTEM ==============

    private static Map<EventType, List<EventListener>> eventListeners = new HashMap<>();
    private static List<GameEvent> eventQueue = new ArrayList<>();

    // ============== ACHIEVEMENTS ==============

    private static Map<Achievement, Boolean> unlockedAchievements = new HashMap<>();
    private static Map<Achievement, Integer> achievementProgress = new HashMap<>();

    // ============== STATISTICS ==============

    private static Statistics stats = new Statistics();

    // ============== TRACKING ==============

    private static Vec2 lastPosition = new Vec2(0, 0);
    private static GooseTasks.GooseTask lastTask = null;
    private static PetNeeds.MoodState lastMood = null;
    private static long lastAutoSave = 0;
    private static int frameCount = 0;
    private static float fps = 0;
    private static long lastFpsTime = 0;

    // ============== SINGLETON ==============

    private static TheGoose instance;

    // ============== INITIALIZATION ==============

    /**
     * Initialize the goose system.
     */
    public static void Init(Context context, PetConfigFile config,
                            int worldWidth, int worldHeight) {
        lifecycleState = LifecycleState.INITIALIZING;

        // Cada encendido arranca con el reloj de juego en cero y sin restos
        // del encendido anterior
        Time.reset();
        resetStaticState();

        // Contexto de aplicación: el ganso sobrevive a cualquier Activity
        ctx = context.getApplicationContext();
        ca = config;
        screenWidth = worldWidth;
        screenHeight = worldHeight;

        // Create singleton instance
        instance = new TheGoose();

        // Initialize modules
        initializeModules();

        // Initialize achievements
        initializeAchievements();

        // Load saved state
        loadState();

        // Start statistics tracking
        stats.sessionStartTime = System.currentTimeMillis();

        // Initialize sound
        Sound.init(ctx, string2boolean(ca.get("SilenceSounds")));

        // Initialize trolling system
        GooseTrolling.init(ctx);

        // Initialize evolution system
        com.skyvanguard.goozy.GooseEvolution.init(ctx);
        com.skyvanguard.goozy.GooseEvolution.setListener(new com.skyvanguard.goozy.GooseEvolution.EvolutionListener() {
            @Override
            public void onEvolution(com.skyvanguard.goozy.GooseEvolution.Stage oldStage,
                                   com.skyvanguard.goozy.GooseEvolution.Stage newStage) {
                // Celebrate evolution!
                touchHandler.showEmoji("EVOLVED!");
                Sound.achievement();
                // Update size based on evolution
                DrawScale = 2.5f * newStage.sizeMultiplier;
            }

            @Override
            public void onLevelUp(int newLevel) {
                touchHandler.showEmoji("Lv." + newLevel + "!");
                Sound.happy();
            }

            @Override
            public void onAbilityUnlocked(String abilityName) {
                touchHandler.showEmoji("NEW: " + abilityName);
            }
        });
        // Apply initial size multiplier
        DrawScale = 2.5f * com.skyvanguard.goozy.GooseEvolution.getSizeMultiplier();

        com.skyvanguard.goozy.brain.BrainController.start(ctx);

        // Initialize new systems
        GooseSystemReactions.init(ctx);
        GooseEasterEggs.init(ctx);
        GooseSoundEffects.init(ctx);
        GooseSystemReactions.onAppOpened();

        // Set initial task
        ai.setTask(GooseTasks.GooseTask.Wander, false);

        lifecycleState = LifecycleState.RUNNING;
        broadcastEvent(EventType.INITIALIZED, null);
    }

    private static void initializeModules() {
        // Physics
        physics = new GoosePhysics();
        physics.initPosition(screenWidth / 2f, screenHeight / 2f);
        physics.setScreenBounds(screenWidth, screenHeight);
        physics.setCallback(instance);

        // AI
        ai = new GooseAI();
        ai.setCallback(instance);
        ai.initTaskPicker();

        // Local AI/LLM System
        GooseLLM.initialize(ctx);

        // Renderer
        renderer = new GooseRenderer();
        applyColors();

        // Touch handler
        touchHandler = new GooseTouchHandler();
        touchHandler.setCallback(instance);
        touchHandler.setGestureListener(instance);

        // Cuerpo
        body = new GooseModel();

        // Store initial position
        lastPosition = physics.getPosition();
    }

    private static void initializeAchievements() {
        stats.achievementsUnlocked = 0;
        for (Achievement a : Achievement.values()) {
            unlockedAchievements.put(a, false);
            achievementProgress.put(a, 0);
        }
    }

    // ============== LIFECYCLE ==============

    /**
     * Pause the goose (when app goes to background).
     */
    public static void pause() {
        if (lifecycleState != LifecycleState.RUNNING) return;

        isPaused = true;
        lifecycleState = LifecycleState.PAUSED;

        // Update play time
        stats.totalPlayTimeMs += System.currentTimeMillis() - stats.sessionStartTime;

        // Save state
        saveState();

        broadcastEvent(EventType.PAUSED, null);
    }

    /**
     * Resume the goose (when app comes to foreground).
     */
    public static void resume() {
        if (lifecycleState != LifecycleState.PAUSED) return;

        isPaused = false;
        lifecycleState = LifecycleState.RUNNING;
        stats.sessionStartTime = System.currentTimeMillis();

        broadcastEvent(EventType.RESUMED, null);
    }

    /**
     * Destroy and cleanup.
     */
    public static void destroy() {
        if (lifecycleState == LifecycleState.UNINITIALIZED
                || lifecycleState == LifecycleState.DESTROYED) {
            return;
        }
        accumulatePlayTime();
        saveState();

        lifecycleState = LifecycleState.DESTROYED;
        broadcastEvent(EventType.DESTROYED, null);

        if (touchHandler != null) {
            touchHandler.cancelTouch();
        }
        com.skyvanguard.goozy.brain.BrainController.stop();
        GooseEasterEggs.deactivateMode();
        MiniGames.cancelGame();
        Sound.release();

        // Cleanup trolling
        GooseTrolling.cleanup();

        // Cleanup visual effects
        GooseVisualEffects.clearAll();

        // Stop dreams
        GooseDreams.stopDreaming();

        // Clear listeners
        eventListeners.clear();
        eventQueue.clear();

        // Nullify modules
        physics = null;
        ai = null;
        renderer = null;
        touchHandler = null;
        body = null;
        canvas = null;
    }

    /**
     * Limpia el estado estático que no debe sobrevivir a un reinicio del overlay.
     */
    private static void resetStaticState() {
        isPaused = false;
        NightRoutine.reset();
        isFramePrepared = false;
        notificationQueue.clear();
        currentNotification = null;
        currentThought = "";
        thoughtDisplayTime = 0f;
        timeSinceThought = 0f;
        eventQueue.clear();
        lastTask = null;
        lastMood = null;
        lastAutoSave = System.currentTimeMillis();
        lastPosition = new Vec2(0, 0);
        behaviorTree = null;
        BodyColor = DEFAULT_BODY_COLOR;
        GooseVisualEffects.clearAll();
        GooseNotes.reset();
    }

    static void onNoteTaskStarted() {
        GooseNotes.prepare(ctx, GooseLLM.isSpanish());
    }

    static void onNoteTaskEnded() {
        GooseNotes.place(screenWidth, screenHeight);
    }

    /** La evolución exige una felicidad promedio: se muestrea una vez por minuto. */
    private static void sampleHappinessForEvolution(float deltaTime) {
        happinessSampleTimer += deltaTime;
        if (happinessSampleTimer < HAPPINESS_SAMPLE_INTERVAL_SECONDS) return;
        happinessSampleTimer = 0f;
        com.skyvanguard.goozy.GooseEvolution.recordHappiness(PetNeeds.get().happiness);
        com.skyvanguard.goozy.GooseEvolution.checkEvolution(ctx);
    }

    private static final float HAPPINESS_SAMPLE_INTERVAL_SECONDS = 60f;
    private static float happinessSampleTimer = 0f;

    private static void accumulatePlayTime() {
        long now = System.currentTimeMillis();
        if (stats.sessionStartTime > 0) {
            stats.totalPlayTimeMs += now - stats.sessionStartTime;
        }
        stats.sessionStartTime = now;
    }

    /**
     * Cambia el tamaño del mundo (rotación o cambio de tamaño de la ventana).
     */
    public static void onWorldSizeChanged(int worldWidth, int worldHeight) {
        if (worldWidth <= 0 || worldHeight <= 0) return;
        screenWidth = worldWidth;
        screenHeight = worldHeight;
        if (physics != null) {
            physics.setScreenBounds(worldWidth, worldHeight);
            Vec2 pos = physics.getPosition();
            physics.setPosition(new Vec2(
                    MathUtil.clamp(pos.x, 0f, (float) worldWidth),
                    MathUtil.clamp(pos.y, 0f, (float) worldHeight)));
            physics.setTargetPos(new Vec2(worldWidth / 2f, worldHeight / 2f));
        }
        MiniGames.setScreenSize(worldWidth, worldHeight);
    }

    private static void openQuickChat() {
        if (ctx == null) return;
        try {
            ctx.startActivity(com.skyvanguard.goozy.QuickChatActivity.createIntent(ctx));
        } catch (RuntimeException e) {
            android.util.Log.w("TheGoose", "No se pudo abrir el chat", e);
        }
    }

    /** El toque en curso fue cancelado por el sistema: soltar sin ejecutar gesto. */
    public static void onTouchCancel() {
        if (touchHandler != null) {
            touchHandler.cancelTouch();
        }
        if (physics != null && physics.getState() == GoosePhysics.PhysicsState.DRAGGED) {
            physics.endDrag(Vec2.ZERO);
        }
        if (ai != null) {
            ai.setTask(GooseTasks.GooseTask.Wander, false);
        }
    }

    /** true si el punto (mundo) cae sobre alguna parte del ganso. */
    public static boolean isOnGoose(float x, float y) {
        return getBodyZone(x, y) != BodyState.Zone.NONE;
    }

    /** Parte del cuerpo bajo el punto (mundo). */
    public static BodyState.Zone getBodyZone(float x, float y) {
        return body != null ? body.zoneAt(x, y) : BodyState.Zone.NONE;
    }

    /** Centro de lo que ocupa el ganso en pantalla, en unidades de mundo. */
    public static float getBodyCenterX() {
        return hasBodyLayout() ? (body.minX + body.maxX) * 0.5f : getGoosePos().x;
    }

    public static float getBodyCenterY() {
        return hasBodyLayout() ? (body.minY + body.maxY) * 0.5f : getGoosePos().y;
    }

    /** Mitad del lado mayor de lo que ocupa el ganso, en unidades de mundo (0 si aún no hay). */
    public static float getBodyHalfExtent() {
        if (!hasBodyLayout()) return 0f;
        return Math.max(body.maxX - body.minX, body.maxY - body.minY) * 0.5f;
    }

    private static boolean hasBodyLayout() {
        return body != null && body.hasLayout;
    }

    public static boolean isMiniGamePlaying() {
        return petModeEnabled && MiniGames.isPlaying();
    }

    /**
     * Save current state to config.
     */
    public static void saveState() {
        if (ctx == null) return;

        accumulatePlayTime();

        java.util.Properties extra = new java.util.Properties();
        extra.setProperty("StatTotalPlayTime", String.valueOf(stats.totalPlayTimeMs));
        extra.setProperty("StatTotalPets", String.valueOf(stats.totalPets));
        extra.setProperty("StatTotalBoops", String.valueOf(stats.totalBoops));
        extra.setProperty("StatHighestCombo", String.valueOf(stats.highestCombo));
        for (Achievement a : Achievement.values()) {
            Boolean isUnlocked = unlockedAchievements.get(a);
            Integer progress = achievementProgress.get(a);
            extra.setProperty(ACHIEVEMENT_KEY_PREFIX + a.name(),
                    (isUnlocked != null && isUnlocked ? "1" : "0") + ":"
                            + (progress != null ? progress : 0));
        }

        com.skyvanguard.goozy.PetRepository.save(ctx, extra);
        stats.lastSaveTime = System.currentTimeMillis();
    }

    /**
     * Load state from config.
     */
    public static void loadState() {
        if (ca == null) return;

        // Necesidades, personalidad y apariencia: una sola vez por proceso.
        // Si ya están en memoria son más nuevas que el archivo.
        com.skyvanguard.goozy.PetRepository.ensureLoaded(ctx);

        try {
            for (Achievement a : Achievement.values()) {
                String saved = ca.get(ACHIEVEMENT_KEY_PREFIX + a.name());
                if (saved == null) continue;
                String[] parts = saved.split(":");
                if (parts.length != 2) continue;
                boolean isUnlocked = "1".equals(parts[0]);
                unlockedAchievements.put(a, isUnlocked);
                achievementProgress.put(a, Integer.parseInt(parts[1]));
                if (isUnlocked) stats.achievementsUnlocked++;
            }

            // Load statistics
            String playTime = ca.get("StatTotalPlayTime");
            if (playTime != null) stats.totalPlayTimeMs = Long.parseLong(playTime);

            String totalPets = ca.get("StatTotalPets");
            if (totalPets != null) stats.totalPets = Integer.parseInt(totalPets);

            String totalBoops = ca.get("StatTotalBoops");
            if (totalBoops != null) stats.totalBoops = Integer.parseInt(totalBoops);

            String highestCombo = ca.get("StatHighestCombo");
            if (highestCombo != null) stats.highestCombo = Integer.parseInt(highestCombo);

        } catch (NumberFormatException e) {
            android.util.Log.w("TheGoose", "Estado guardado con formato inválido; se usan valores por defecto", e);
        }
    }

    // ============== MAIN LOOP ==============

    /**
     * Main update loop.
     */
    public static void Tick() {
        if (lifecycleState != LifecycleState.RUNNING || isPaused) return;

        float deltaTime = Time.deltaTime;

        // Update FPS counter
        updateFPS();

        // Update pet needs
        if (petModeEnabled) {
            PetNeeds.get().update(deltaTime);
            ai.checkNeedsAndUpdateState();
            checkMoodChange();
            checkCriticalNeeds();
            updateHappinessTracking(deltaTime);

            // Update thought system
            updateThoughts(deltaTime);

            // Update achievement notifications
            updateAchievementNotifications(deltaTime);

            // Update GooseLLM boredom
            GooseLLM.updateBoredom(deltaTime);

            // Update minigames
            if (MiniGames.isPlaying()) {
                MiniGames.update(deltaTime);
            }

            // Update dreams when sleeping
            if (ai.getCurrentTask() == GooseTasks.GooseTask.Sleeping) {
                if (!GooseDreams.isDreaming()) {
                    GooseDreams.startDreaming(physics.getPosition());
                }
                GooseDreams.update(deltaTime, physics.getPosition());
            } else if (GooseDreams.isDreaming()) {
                GooseDreams.stopDreaming();
            }

            // Check system reactions
            checkSystemReactions();

            sampleHappinessForEvolution(deltaTime);

            // Update easter eggs tracking
            GooseEasterEggs.updateMode();
            NightRoutine.update(deltaTime);
            // Chequeos por reloj de pared: alcanza con cada tanto, no en cada frame
            wallClockCheckTimer += deltaTime;
            if (wallClockCheckTimer >= WALL_CLOCK_CHECK_INTERVAL_SECONDS) {
                wallClockCheckTimer = 0f;
                GooseEasterEggs.checkPatience();
                GooseEasterEggs.checkSpecialDate();
                checkTimeBasedAchievements();
            }

            // Update sound effects
            GooseSoundEffects.update();
        }

        // Update visual effects
        GooseVisualEffects.update(deltaTime, physics.getPosition(),
                physics.getCurrentSpeed() > 10f);

        // Run AI
        ai.runAI();

        // Check task changes
        checkTaskChange();

        // Update physics
        physics.update(ai.isOverrideExtendNeck());

        GooseNotes.update(deltaTime, physics.getPosition());

        // Track distance
        trackDistance();

        // Track max speed
        float currentSpeed = physics.getCurrentSpeed();
        if (currentSpeed > stats.maxSpeed) {
            stats.maxSpeed = currentSpeed;
            checkAchievement(Achievement.SPEEDSTER, 1);
        }

        updateBody();

        // Process event queue
        processEvents();

        // Auto-save check
        checkAutoSave();

        // Update statistics
        stats.updateAverageHappiness(PetNeeds.get().happiness);

        // Check time-based achievements
    }

    /** La medianoche exacta (easter egg) dura un minuto: este intervalo no se la pierde. */
    private static final float WALL_CLOCK_CHECK_INTERVAL_SECONDS = 20f;
    private static float wallClockCheckTimer = 0f;

    /**
     * Check achievements based on time of day and play duration.
     */
    private static void checkTimeBasedAchievements() {
        // Check play time (MARATHON - 5 hours = 18000 seconds)
        long playTimeSeconds = stats.totalPlayTimeMs / 1000;
        checkAchievement(Achievement.MARATHON, (int) playTimeSeconds);

        // Check time of day achievements
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);

        // NIGHT_OWL: Playing between midnight and 4am
        if (hour >= 0 && hour < 4) {
            checkAchievement(Achievement.NIGHT_OWL, 1);
        }

        // EARLY_BIRD: Playing between 5am and 7am
        if (hour >= 5 && hour < 7) {
            checkAchievement(Achievement.EARLY_BIRD, 1);
        }
    }

    private static void updateFPS() {
        frameCount++;
        long now = System.currentTimeMillis();
        if (now - lastFpsTime >= 1000) {
            fps = frameCount * 1000f / (now - lastFpsTime);
            frameCount = 0;
            lastFpsTime = now;
        }
    }

    private static void updateBody() {
        if (body == null) return;
        body.species = currentSpecies();
        // El huevo y el pollito ya son chicos por forma: no se achican más que esto
        body.scale = body.species == Species.EGG || body.species == Species.CHICK
                ? Math.max(DrawScale, MIN_SMALL_STAGE_SCALE) : DrawScale;
        updateBodyFromState();

        boolean extendNeck = ai.isOverrideExtendNeck() || physics.getCurrentSpeed() >= 200f;
        Vec2 pos = physics.getPosition();
        boolean carried = physics.getState() == GoosePhysics.PhysicsState.DRAGGED;
        body.update(Time.deltaTime, pos.x, pos.y, physics.getDirection(),
                physics.getVerticalPosition(), extendNeck ? 1f : 0f, 0f, carried);

        int steps = body.pollSteps(stepBuffer);
        for (int i = 0; i < steps; i++) {
            physics.stampFootprint(stepBuffer[i * 2], stepBuffer[i * 2 + 1]);
            Sound.step();
        }
    }

    private static Species currentSpecies() {
        com.skyvanguard.goozy.GooseEvolution.Stage stage =
                com.skyvanguard.goozy.GooseEvolution.getCurrentStage();
        if (stage == com.skyvanguard.goozy.GooseEvolution.Stage.EGG) return Species.EGG;
        if (stage == com.skyvanguard.goozy.GooseEvolution.Stage.HATCHLING) return Species.CHICK;
        switch (PetAppearance.get().creatureType) {
            case 1: return Species.DUCK;
            case 2: return Species.CHICK;
            default: return Species.GOOSE;
        }
    }

    private static void updateBodyFromState() {
        if (!petModeEnabled) {
            body.pose = BodyState.Pose.NORMAL;
            body.expression = BodyState.Expression.NEUTRAL;
            return;
        }
        switch (ai.getCurrentTask()) {
            case Sleeping: setLook(BodyState.Pose.SLEEPING, BodyState.Expression.SLEEPY); break;
            case Eating: setLook(BodyState.Pose.EATING, BodyState.Expression.JOY); break;
            case Playing:
            case MudDash: setLook(BodyState.Pose.EXCITED, BodyState.Expression.JOY); break;
            case Happy: setLook(BodyState.Pose.EXCITED, BodyState.Expression.HAPPY); break;
            case Sad: setLook(BodyState.Pose.RELAXED, BodyState.Expression.SAD); break;
            case Seeking: setLook(BodyState.Pose.ALERT, BodyState.Expression.SURPRISED); break;
            case BeingPetted: setLook(BodyState.Pose.RELAXED, BodyState.Expression.LOVE); break;
            case BeingDragged: setLook(BodyState.Pose.SCARED, BodyState.Expression.SURPRISED); break;
            default: setLook(BodyState.Pose.NORMAL, expressionFromMood()); break;
        }
    }

    private static void setLook(BodyState.Pose pose, BodyState.Expression expression) {
        body.pose = pose;
        body.expression = expression;
    }

    private static BodyState.Expression expressionFromMood() {
        switch (PetNeeds.get().getMoodState()) {
            case HAPPY: return BodyState.Expression.HAPPY;
            case TIRED: return BodyState.Expression.SLEEPY;
            case SAD:
            case HUNGRY:
            case SICK: return BodyState.Expression.SAD;
            default: return BodyState.Expression.NEUTRAL;
        }
    }

    // ============== RENDER ==============

    /**
     * Render the goose.
     */
    public static boolean isRunning() {
        return lifecycleState == LifecycleState.RUNNING;
    }

    /**
     * Render usando el canvas del frame actual. El canvas de onDraw no está
     * garantizado que sea el mismo objeto entre frames.
     */
    /**
     * Actualiza el estado de dibujo (partículas, colores). Una vez por frame,
     * antes de dibujar cualquier capa.
     */
    public static void PrepareFrame() {
        if (lifecycleState != LifecycleState.RUNNING || renderer == null) return;

        applyEasterEggEffects();
        applyColors();
        renderer.updateFrame(physics, ai, petModeEnabled);
        isFramePrepared = true;
    }

    private static boolean isFramePrepared = false;

    /**
     * Dibuja una capa sobre el canvas del frame actual.
     */
    public static void Render(Canvas frameCanvas, GooseRenderer.Layer layer) {
        if (lifecycleState != LifecycleState.RUNNING || frameCanvas == null
                || renderer == null || physics == null || !isFramePrepared) {
            return;
        }
        canvas = frameCanvas;

        if (layer == GooseRenderer.Layer.WORLD) {
            GooseVisualEffects.render(canvas, physics.getPosition());
            GooseNotes.render(canvas);
        }

        renderer.render(canvas, layer, physics, body, touchHandler, ai, petModeEnabled,
                screenWidth, screenHeight);

        if (layer == GooseRenderer.Layer.WORLD) {
            if (petModeEnabled && GooseDreams.isDreaming()) {
                GooseDreams.render(canvas);
            }
            if (petModeEnabled && MiniGames.isPlaying()) {
                MiniGames.render(canvas);
            }
            if (debugMode) {
                renderDebugOverlay();
            }
        }
    }

    /**
     * Apply visual effects based on active easter egg mode.
     */
    private static void applyEasterEggEffects() {
        GooseEasterEggs.SecretMode mode = GooseEasterEggs.getCurrentMode();

        // Cada flag refleja solo el modo actual: al cambiar de modo no quedan restos
        GooseVisualEffects.setDiscoModeActive(mode == GooseEasterEggs.SecretMode.DISCO_GOOSE);
        GooseVisualEffects.setGhostModeActive(mode == GooseEasterEggs.SecretMode.GHOST_GOOSE);
        GooseVisualEffects.setRainbowTrailActive(mode == GooseEasterEggs.SecretMode.RAINBOW_GOOSE);
        GooseVisualEffects.setGoldenGlowActive(mode == GooseEasterEggs.SecretMode.GOLDEN_GOOSE);

        switch (mode) {
            case DISCO_GOOSE:
                discoBodyColor = GooseVisualEffects.getDiscoColor();
                break;
            case PARTY_GOOSE:
                // Spawn confetti periodically
                if (physics != null && Math.random() < 0.02) {
                    GooseVisualEffects.spawnConfetti(
                            physics.getPosition().x,
                            physics.getPosition().y, 3);
                }
                break;
            default:
                break;
        }
    }

    private static int discoBodyColor = DEFAULT_BODY_COLOR;

    private static void renderDebugOverlay() {
        Paint debugPaint = new Paint();
        debugPaint.setColor(0xFFFFFFFF);
        debugPaint.setTextSize(DEBUG_TEXT_SIZE);
        debugPaint.setAntiAlias(true);

        Paint bgPaint = new Paint();
        bgPaint.setColor(0x88000000);

        float x = 10;
        float y = 20;
        float lineHeight = 14;

        // Background
        canvas.drawRect(5, 5, 200, 180, bgPaint);

        // FPS
        canvas.drawText("FPS: " + String.format("%.1f", fps), x, y, debugPaint);
        y += lineHeight;

        // State
        canvas.drawText("State: " + lifecycleState, x, y, debugPaint);
        y += lineHeight;

        // Task
        canvas.drawText("Task: " + ai.getCurrentTask(), x, y, debugPaint);
        y += lineHeight;

        // Position
        Vec2 pos = physics.getPosition();
        canvas.drawText("Pos: " + String.format("%.0f, %.0f", pos.x, pos.y), x, y, debugPaint);
        y += lineHeight;

        // Speed
        canvas.drawText("Speed: " + String.format("%.1f", physics.getCurrentSpeed()), x, y, debugPaint);
        y += lineHeight;

        // Physics state
        canvas.drawText("Physics: " + physics.getState(), x, y, debugPaint);
        y += lineHeight;

        // Needs
        canvas.drawText("Hunger: " + String.format("%.0f", PetNeeds.get().hunger), x, y, debugPaint);
        y += lineHeight;
        canvas.drawText("Energy: " + String.format("%.0f", PetNeeds.get().energy), x, y, debugPaint);
        y += lineHeight;
        canvas.drawText("Happy: " + String.format("%.0f", PetNeeds.get().happiness), x, y, debugPaint);
        y += lineHeight;

        // Combo
        canvas.drawText("Combo: " + touchHandler.getComboCount() + "x", x, y, debugPaint);
        y += lineHeight;

        // Stats
        canvas.drawText("Pets: " + stats.totalPets + " Boops: " + stats.totalBoops, x, y, debugPaint);
    }

    // ============== EVENT CHANGE DETECTION ==============

    private static void checkTaskChange() {
        GooseTasks.GooseTask currentTask = ai.getCurrentTask();
        if (currentTask != lastTask) {
            broadcastEvent(EventType.TASK_CHANGED, currentTask);
            lastTask = currentTask;
        }
    }

    private static void checkMoodChange() {
        PetNeeds.MoodState currentMood = PetNeeds.get().getMoodState();
        if (currentMood != lastMood) {
            broadcastEvent(EventType.MOOD_CHANGED, currentMood);
            lastMood = currentMood;
        }
    }

    private static void checkCriticalNeeds() {
        if (PetNeeds.get().hunger > 90 || PetNeeds.get().energy < 10 || PetNeeds.get().happiness < 10) {
            broadcastEvent(EventType.NEEDS_CRITICAL, null);
        }
    }

    private static void trackDistance() {
        Vec2 currentPos = physics.getPosition();
        float dist = Vec2.dist(lastPosition, currentPos);
        stats.totalDistanceTraveled += dist;
        lastPosition = new Vec2(currentPos.x, currentPos.y);
    }

    private static void updateHappinessTracking(float deltaTime) {
        if (PetNeeds.get().happiness >= 100) {
            stats.timeAtMaxHappiness += (long)(deltaTime * 1000);
            checkAchievement(Achievement.HAPPY_GOOSE, (int)(stats.timeAtMaxHappiness / 1000));
            checkAchievement(Achievement.BLISSFUL, (int)(stats.timeAtMaxHappiness / 1000));
        }
    }

    private static void checkAutoSave() {
        long now = System.currentTimeMillis();
        if (now - lastAutoSave > AUTO_SAVE_INTERVAL_MS) {
            saveState();
            lastAutoSave = now;
        }
    }

    // ============== BEHAVIOR TREE ==============

    /**
     * Update the behavior tree AI system.
     */
    private static void updateBehaviorTree(float deltaTime) {
        if (behaviorTree == null) return;

        // Calculate time since last interaction
        float timeSinceInteraction = (System.currentTimeMillis() - stats.sessionStartTime) / 1000f;
        if (touchHandler != null && touchHandler.getLastTouchTime() > 0) {
            timeSinceInteraction = (System.currentTimeMillis() - touchHandler.getLastTouchTime()) / 1000f;
        }
        behaviorTree.setTimeSinceInteraction(timeSinceInteraction);

        // Update the tree
        behaviorTree.update(deltaTime);

        // Process requested task from behavior tree
        GooseTasks.GooseTask requestedTask = behaviorTree.getRequestedTask();
        if (requestedTask != null && ai != null) {
            ai.setTask(requestedTask, false);
        }

        // Process requested event from behavior tree
        GooseAI.RandomEvent requestedEvent = behaviorTree.getRequestedEvent();
        if (requestedEvent != null && ai != null) {
            ai.forceRandomEvent(requestedEvent);
        }
    }

    // ============== THOUGHT SYSTEM ==============

    /**
     * Update the thought bubble system.
     */
    private static void updateThoughts(float deltaTime) {
        // Update display timer
        if (thoughtDisplayTime > 0) {
            thoughtDisplayTime -= deltaTime;
        }

        // Update time since last thought
        timeSinceThought += deltaTime;

        // Check for milestone thoughts first
        String milestone = GooseLLM.getMilestoneThought();
        if (milestone != null && thoughtDisplayTime <= 0) {
            setThought(milestone);
            return;
        }

        // Check for favorite time thoughts
        String favTime = GooseLLM.getFavoriteTimeThought();
        if (favTime != null && Math.random() < 0.01f && thoughtDisplayTime <= 0) {
            setThought(favTime);
            return;
        }

        // Generate periodic thoughts
        if (timeSinceThought >= nextThoughtTime && thoughtDisplayTime <= 0) {
            com.skyvanguard.goozy.brain.BrainController.requestThought(
                    com.skyvanguard.goozy.brain.BrainTrigger.Kind.IDLE_THOUGHT, "");
            timeSinceThought = 0;
            nextThoughtTime = MathUtil.randomRange(THOUGHT_INTERVAL_MIN, THOUGHT_INTERVAL_MAX);
        }

        com.skyvanguard.goozy.brain.BrainController.onTick(deltaTime);
    }

    /**
     * Set a new thought to display.
     */
    public static void setThought(String thought) {
        currentThought = thought != null ? thought : "";
        // Las frases largas se quedan más tiempo en pantalla
        thoughtDuration = Math.min(THOUGHT_MAX_DURATION,
                THOUGHT_DISPLAY_DURATION + currentThought.length() * THOUGHT_SECONDS_PER_CHAR);
        thoughtDisplayTime = thoughtDuration;
        timeSinceThought = 0;
    }

    private static final float THOUGHT_SECONDS_PER_CHAR = 0.06f;
    private static final float THOUGHT_MAX_DURATION = 12f;
    private static float thoughtDuration = THOUGHT_DISPLAY_DURATION;

    // ============== ENTRADAS DEL CEREBRO ==============

    /** true si el humano lo está tocando o hay un minijuego: no interrumpir. */
    public static boolean isBusyWithHuman() {
        if (touchHandler == null || ai == null) return true;
        return touchHandler.isBeingPetted() || touchHandler.isBeingDragged()
                || isMiniGamePlaying();
    }

    public static void requestTask(GooseTasks.GooseTask task) {
        if (isRunning() && !isBusyWithHuman() && ai.getCurrentTask() != task) {
            ai.setTask(task, false);
        }
    }

    public static void requestEvent(GooseAI.RandomEvent event) {
        if (isRunning() && !isBusyWithHuman()) {
            ai.forceRandomEvent(event);
        }
    }

    public static void showEmoji(String emoji) {
        if (touchHandler != null && emoji != null && !emoji.isEmpty()) {
            touchHandler.showEmoji(emoji);
        }
    }

    public static String getCurrentTaskName() {
        return ai != null ? ai.getCurrentTask().name() : "";
    }

    /**
     * Get the current thought for rendering.
     */
    public static String getCurrentThought() {
        return thoughtDisplayTime > 0 ? currentThought : "";
    }

    /**
     * Get thought display progress (1 = just appeared, 0 = fading out).
     */
    public static float getThoughtAlpha() {
        if (thoughtDisplayTime <= 0) return 0f;
        if (thoughtDisplayTime > thoughtDuration - 0.5f) {
            // Fade in
            return (thoughtDuration - thoughtDisplayTime) / 0.5f;
        } else if (thoughtDisplayTime < 0.5f) {
            // Fade out
            return thoughtDisplayTime / 0.5f;
        }
        return 1f;
    }

    /**
     * Check and handle system reactions (battery, time, etc).
     */
    private static void checkSystemReactions() {
        long lastSavedTime = stats.lastSaveTime;
        GooseSystemReactions.Reaction reaction = GooseSystemReactions.checkForReaction(lastSavedTime);

        if (reaction != null) {
            // Show emoji
            if (touchHandler != null) {
                touchHandler.showEmoji(reaction.emoji);
            }

            // Apply mood change
            PetNeeds.get().happiness = Math.max(0, Math.min(100, PetNeeds.get().happiness + reaction.moodChange));

            // Play sound if needed
            if (reaction.triggerSound && !Sound.isSilenced()) {
                GooseSoundEffects.honkForMood(PetNeeds.get().happiness);
            }

            // Trigger event if needed
            if (reaction.triggerEvent && reaction.eventToTrigger != null && ai != null) {
                ai.forceRandomEvent(reaction.eventToTrigger);
            }

            // Spawn visual effects based on reaction
            if (physics != null) {
                if (reaction.moodChange > 5) {
                    GooseVisualEffects.spawnHearts(physics.getPosition().x, physics.getPosition().y, 3);
                } else if (reaction.moodChange < -5) {
                    GooseVisualEffects.spawnAnger(physics.getPosition().x, physics.getPosition().y);
                }
            }
        }

        // Check for special date events
        String specialEvent = GooseEasterEggs.getSpecialDateEvent();
        if (specialEvent != null && touchHandler != null && Math.random() < 0.01) {
            touchHandler.showEmoji(specialEvent);
        }
    }

    // ============== EVENT SYSTEM ==============

    /**
     * Register an event listener.
     */
    public static void addEventListener(EventType type, EventListener listener) {
        if (!eventListeners.containsKey(type)) {
            eventListeners.put(type, new ArrayList<>());
        }
        eventListeners.get(type).add(listener);
    }

    /**
     * Remove an event listener.
     */
    public static void removeEventListener(EventType type, EventListener listener) {
        if (eventListeners.containsKey(type)) {
            eventListeners.get(type).remove(listener);
        }
    }

    /**
     * Broadcast an event to all listeners.
     */
    public static void broadcastEvent(EventType type, Object data) {
        eventQueue.add(new GameEvent(type, data));
    }

    private static void processEvents() {
        for (GameEvent event : new ArrayList<>(eventQueue)) {
            if (eventListeners.containsKey(event.type)) {
                for (EventListener listener : eventListeners.get(event.type)) {
                    listener.onEvent(event);
                }
            }
        }
        eventQueue.clear();
    }

    // ============== ACHIEVEMENT SYSTEM ==============

    /**
     * Check and update achievement progress.
     */
    public static void checkAchievement(Achievement achievement, int value) {
        if (unlockedAchievements.get(achievement)) return;

        achievementProgress.put(achievement, Math.max(achievementProgress.get(achievement), value));

        if (achievementProgress.get(achievement) >= achievement.requirement) {
            unlockAchievement(achievement);
        }
    }

    /**
     * Increment achievement progress.
     */
    public static void incrementAchievement(Achievement achievement) {
        if (unlockedAchievements.get(achievement)) return;

        int newValue = achievementProgress.get(achievement) + 1;
        achievementProgress.put(achievement, newValue);

        if (newValue >= achievement.requirement) {
            unlockAchievement(achievement);
        }
    }

    private static void unlockAchievement(Achievement achievement) {
        unlockedAchievements.put(achievement, true);
        stats.achievementsUnlocked++;
        lastAutoSave = 0; // forzar guardado en el próximo tick

        // Queue the achievement notification
        queueAchievementNotification(achievement);

        // Trigger celebration
        triggerCelebration();

        broadcastEvent(EventType.ACHIEVEMENT_UNLOCKED, achievement);
    }

    /**
     * Queue an achievement notification to be displayed.
     */
    private static void queueAchievementNotification(Achievement achievement) {
        notificationQueue.add(new AchievementNotification(achievement));
    }

    /**
     * Update the achievement notification system.
     */
    private static void updateAchievementNotifications(float deltaTime) {
        // Process current notification
        if (currentNotification != null) {
            currentNotification.displayTime += deltaTime;

            // Calculate animation phase
            if (currentNotification.displayTime < NOTIFICATION_SLIDE_TIME) {
                // Slide in
                currentNotification.animPhase = currentNotification.displayTime / NOTIFICATION_SLIDE_TIME;
            } else if (currentNotification.displayTime < NOTIFICATION_SLIDE_TIME + NOTIFICATION_DISPLAY_TIME) {
                // Visible
                currentNotification.animPhase = 1f;
            } else if (currentNotification.displayTime < NOTIFICATION_SLIDE_TIME * 2 + NOTIFICATION_DISPLAY_TIME) {
                // Slide out
                float outProgress = (currentNotification.displayTime - NOTIFICATION_SLIDE_TIME - NOTIFICATION_DISPLAY_TIME)
                        / NOTIFICATION_SLIDE_TIME;
                currentNotification.animPhase = 1f - outProgress;
            } else {
                // Complete
                currentNotification.completed = true;
                currentNotification = null;
            }
        }

        // Process queue if no current notification
        if (currentNotification == null && !notificationQueue.isEmpty()) {
            currentNotification = notificationQueue.remove(0);
        }
    }

    /**
     * Get the current achievement notification for rendering.
     */
    public static AchievementNotification getCurrentAchievementNotification() {
        return currentNotification;
    }

    /**
     * Get the number of pending notifications.
     */
    public static int getPendingNotificationCount() {
        return notificationQueue.size() + (currentNotification != null ? 1 : 0);
    }

    /**
     * Check if achievement is unlocked.
     */
    public static boolean isAchievementUnlocked(Achievement achievement) {
        return unlockedAchievements.getOrDefault(achievement, false);
    }

    /**
     * Get achievement progress.
     */
    public static int getAchievementProgress(Achievement achievement) {
        return achievementProgress.getOrDefault(achievement, 0);
    }

    /**
     * Get achievement progress as percentage (0-100).
     */
    public static float getAchievementProgressPercent(Achievement achievement) {
        int progress = achievementProgress.getOrDefault(achievement, 0);
        return Math.min(100f, (progress * 100f) / achievement.requirement);
    }

    /**
     * Get total number of achievements.
     */
    public static int getTotalAchievementCount() {
        return Achievement.values().length;
    }

    /**
     * Get number of unlocked achievements.
     */
    public static int getUnlockedAchievementCount() {
        return stats.achievementsUnlocked;
    }

    /**
     * Get all achievements with their status.
     */
    public static Map<Achievement, Boolean> getAllAchievements() {
        return new HashMap<>(unlockedAchievements);
    }

    // ============== SPECIAL EFFECTS ==============

    /**
     * Trigger a celebration effect.
     */
    public static void triggerCelebration() {
        if (renderer != null && physics != null) {
            renderer.triggerCelebration(physics.getPosition());
            // Enhanced visual effects
            GooseVisualEffects.spawnConfetti(physics.getPosition().x,
                    physics.getPosition().y, 20);
            GooseVisualEffects.spawnSparkles(physics.getPosition().x,
                    physics.getPosition().y, 10);
        }
        GooseSoundEffects.play(GooseSoundEffects.SoundType.ACHIEVEMENT);
        broadcastEvent(EventType.CELEBRATION, null);
    }

    /**
     * Trigger love effect.
     */
    public static void triggerLoveEffect() {
        if (renderer != null && physics != null) {
            renderer.triggerLoveEffect(physics.getPosition());
            GooseVisualEffects.spawnLoveBurst(physics.getPosition().x,
                    physics.getPosition().y);
        }
    }

    /**
     * Trigger excitement effect.
     */
    public static void triggerExcitement() {
        if (renderer != null && physics != null) {
            renderer.triggerExcitement(physics.getPosition());
            GooseVisualEffects.spawnSparkles(physics.getPosition().x,
                    physics.getPosition().y, 8);
        }
        GooseSoundEffects.play(GooseSoundEffects.SoundType.HONK_EXCITED);
    }

    /**
     * Trigger honk with visual wave effect.
     */
    public static void triggerHonkEffect() {
        if (physics != null) {
            GooseVisualEffects.spawnHonkWave(physics.getPosition().x,
                    physics.getPosition().y);
        }
        GooseSoundEffects.randomHonk();
        GooseEasterEggs.recordHonk();
    }

    // ============== CHEATS / DEBUG COMMANDS ==============

    /**
     * Toggle debug mode.
     */
    public static void toggleDebugMode() {
        debugMode = !debugMode;
    }

    /**
     * Set debug mode.
     */
    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
    }

    /**
     * Cheat: Max happiness.
     */
    public static void cheatMaxHappiness() {
        PetNeeds.get().happiness = 100;
        PetNeeds.get().hunger = 0;
        PetNeeds.get().energy = 100;
        triggerCelebration();
    }

    /**
     * Cheat: Teleport to center.
     */
    public static void cheatTeleportCenter() {
        if (physics != null) {
            physics.teleport(new Vec2(screenWidth / 2f, screenHeight / 2f));
        }
    }

    /**
     * Cheat: Make goose jump.
     */
    public static void cheatJump() {
        if (physics != null) {
            physics.jump(2f);
        }
    }

    /**
     * Cheat: Trigger random event.
     */
    public static void cheatRandomEvent() {
        if (ai != null) {
            ai.forceRandomEvent(GooseAI.RandomEvent.ZOOMIES);
        }
    }

    /**
     * Cheat: Unlock all achievements.
     */
    public static void cheatUnlockAllAchievements() {
        for (Achievement a : Achievement.values()) {
            if (!unlockedAchievements.get(a)) {
                unlockAchievement(a);
            }
        }
    }

    /**
     * Cheat: Add impulse.
     */
    public static void cheatImpulse(float angle, float force) {
        if (physics != null) {
            physics.applyImpulse(angle, force);
        }
    }

    // ============== TOUCH HANDLING ==============

    public static void onTouchStart(float x, float y) {
        if (!petModeEnabled || touchHandler == null || isPaused) return;

        // Handle minigame touches first
        if (MiniGames.isPlaying()) {
            MiniGames.onTouch(x, y);
        }

        touchHandler.onTouchStart(x, y);
        stats.totalTouches++;
        broadcastEvent(EventType.TOUCHED, new Vec2(x, y));

        // Track easter egg taps
        GooseEasterEggs.recordTap();
    }

    public static void onTouchMove(float x, float y) {
        if (!petModeEnabled || touchHandler == null || isPaused) return;
        touchHandler.onTouchMove(x, y);
    }

    public static void onTouchEnd(float x, float y) {
        if (!petModeEnabled || touchHandler == null || isPaused) return;

        // Handle minigame touches
        if (MiniGames.isPlaying()) {
            MiniGames.onTouch(x, y);
        }

        touchHandler.onTouchEnd(x, y);
    }

    // ============== PUBLIC ACTIONS ==============

    public static void startEating() {
        if (ai != null) {
            ai.startEating();
            stats.totalFeedings++;
            incrementAchievement(Achievement.WELL_FED);
            checkAchievement(Achievement.GOURMET, stats.totalFeedings);
            broadcastEvent(EventType.FED, null);
            GooseEasterEggs.recordFeed();
            GooseDreams.recordFoodEaten();
            com.skyvanguard.goozy.GooseEvolution.recordFeed(ctx);  // Track for evolution

            // Visual effect
            if (physics != null) {
                GooseVisualEffects.spawnSparkles(
                        physics.getPosition().x,
                        physics.getPosition().y, 5);
            }
        }
    }

    public static void startPlaying() {
        if (ai != null) {
            ai.startPlaying();
            stats.totalPlaySessions++;
            broadcastEvent(EventType.PLAYED, null);
            GooseDreams.recordGamePlayed();
            com.skyvanguard.goozy.GooseEvolution.recordPlay(ctx);  // Track for evolution

            // Visual effect
            if (physics != null) {
                GooseVisualEffects.spawnMusicalNotes(
                        physics.getPosition().x,
                        physics.getPosition().y);
            }

            // Start a random minigame
            if (instance != null) {
                MiniGames.setCallback(instance);
                MiniGames.setScreenSize(screenWidth, screenHeight);
                MiniGames.GameType[] games = {
                    MiniGames.GameType.FEEDING,
                    MiniGames.GameType.CHASING,
                    MiniGames.GameType.CATCHING
                };
                MiniGames.startGame(games[(int)(Math.random() * games.length)]);
            }
        }
    }

    /** Un baño: queda limpio y contento. */
    public static void startCleaning() {
        if (ai == null) return;
        PetNeeds.get().clean();
        if (physics != null) {
            GooseVisualEffects.spawnSparkles(physics.getPosition().x, physics.getPosition().y, 12);
        }
        com.skyvanguard.goozy.brain.BrainController.requestThought(
                com.skyvanguard.goozy.brain.BrainTrigger.Kind.CLEANED, "");
    }

    /** Un remedio: mejora la salud. */
    public static void startHealing() {
        if (ai == null) return;
        PetNeeds.get().heal();
        if (physics != null) {
            GooseVisualEffects.spawnHearts(physics.getPosition().x, physics.getPosition().y, 4);
        }
        com.skyvanguard.goozy.brain.BrainController.requestThought(
                com.skyvanguard.goozy.brain.BrainTrigger.Kind.HEALED, "");
    }

    public static void startSleeping() {
        if (ai != null) {
            ai.startSleeping();
            stats.totalSleepSessions++;
            incrementAchievement(Achievement.SLEEPY_TIME);
            checkAchievement(Achievement.SLEEP_KING, stats.totalSleepSessions);
        }
    }

    // ============== GETTERS ==============

    public static String getCurrentEmoji() {
        return touchHandler != null ? touchHandler.getCurrentEmoji() : "";
    }

    public static Vec2 getGoosePos() {
        return physics != null ? physics.getPosition() : new Vec2(0, 0);
    }

    public static GoosePhysics getGoosePhysics() {
        return physics;
    }

    public static GooseRenderer getRenderer() {
        return renderer;
    }

    public static GooseAI getAI() {
        return ai;
    }

    public static GooseModel getBody() {
        return body;
    }

    public static Statistics getStatistics() {
        return stats;
    }

    public static LifecycleState getLifecycleState() {
        return lifecycleState;
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    @Override
    public boolean isPetModeEnabled() {
        return petModeEnabled;
    }

    public static float getFPS() {
        return fps;
    }

    public static void setShowShadow(Boolean isShow) {
        if (renderer != null) {
            renderer.setShowShadow(isShow);
        }
    }

    public static void setPetModeEnabled(boolean enabled) {
        petModeEnabled = enabled;
    }

    private static void applyColors() {
        if (renderer != null) {
            renderer.footColor = FootColor;
            renderer.outlineColor = OutLineColor;
            renderer.mouthColor = MouthColor;
            renderer.eyeColor = EyeColor;
            boolean isDisco =
                    GooseEasterEggs.getCurrentMode() == GooseEasterEggs.SecretMode.DISCO_GOOSE;
            renderer.bodyColor = isDisco ? discoBodyColor : BodyColor;
        }
    }

    // ============== AICallback IMPLEMENTATION ==============

    @Override
    public GoosePhysics getPhysics() {
        return physics;
    }

    @Override
    public GooseTouchHandler getTouchHandler() {
        return touchHandler;
    }

    @Override
    public PetConfigFile getConfig() {
        return ca;
    }

    @Override
    public int getScreenWidth() {
        return screenWidth;
    }

    @Override
    public int getScreenHeight() {
        return screenHeight;
    }

    // ============== TouchCallback IMPLEMENTATION ==============

    @Override
    public void onTaskChange(GooseTasks.GooseTask task, boolean honk) {
        if (ai != null) {
            ai.setTask(task, honk);
        }
    }

    @Override
    public void onPositionChange(Vec2 newPos) {
        if (physics != null) {
            physics.setPosition(newPos);
        }
    }

    @Override
    public Vec2 getPosition() {
        return physics != null ? physics.getPosition() : new Vec2(0, 0);
    }

    @Override
    public float getDirection() {
        return physics != null ? physics.getDirection() : 0f;
    }

    // ============== PhysicsCallback IMPLEMENTATION ==============

    @Override
    public void onLanded(float impactVelocity) {
        broadcastEvent(EventType.LANDED, impactVelocity);

        // Dust effect on hard landing
        if (impactVelocity > 200f && renderer != null) {
            renderer.spawnParticleBurst(physics.getPosition(),
                GooseRenderer.ParticleType.DUST, 5);
        }
    }

    @Override
    public void onBounced(float bounceVelocity) {
        stats.totalBounces++;
        checkAchievement(Achievement.BOUNCY, stats.totalBounces);
        broadcastEvent(EventType.BOUNCED, bounceVelocity);
    }

    @Override
    public void onHitEdge(int edge) {
        stats.totalEdgeHits++;
        broadcastEvent(EventType.HIT_EDGE, edge);
    }

    // ============== GestureListener IMPLEMENTATION ==============

    @Override
    public void onGestureRecognized(GooseTouchHandler.GestureType gesture,
                                     GooseTouchHandler.TouchZone zone) {
        // Track evolution
        com.skyvanguard.goozy.GooseEvolution.recordInteraction(ctx);

        // Track specific gestures
        switch (gesture) {
            case PET:
                stats.totalPets++;
                incrementAchievement(Achievement.FIRST_PET);
                checkAchievement(Achievement.PET_MASTER, stats.totalPets);
                checkAchievement(Achievement.PET_LEGEND, stats.totalPets);
                broadcastEvent(EventType.PETTED, zone);
                triggerLoveEffect();
                com.skyvanguard.goozy.GooseEvolution.recordLovingAction(ctx);
                com.skyvanguard.goozy.GooseEvolution.recordPet(ctx);  // Track for evolution
                GooseEasterEggs.recordPet();
                GooseDreams.recordPetReceived();
                com.skyvanguard.goozy.brain.BrainController.requestThought(
                        com.skyvanguard.goozy.brain.BrainTrigger.Kind.PETTED, "");
                break;

            case BOOP:
                stats.totalBoops++;
                incrementAchievement(Achievement.BOOP_CHAMPION);
                checkAchievement(Achievement.BOOP_MASTER, stats.totalBoops);
                break;

            case THROW:
                stats.totalThrows++;
                incrementAchievement(Achievement.THROWER);
                checkAchievement(Achievement.LAUNCH_PRO, stats.totalThrows);
                broadcastEvent(EventType.THROWN, touchHandler.getDragVelocity());
                break;

            case LONG_PRESS:
                // Mantenerlo apretado abre la ventanita para hablarle
                openQuickChat();
                break;

            case DOUBLE_TAP:
            case TRIPLE_TAP:
            case SWIPE_UP:
                stats.totalJumps++;
                incrementAchievement(Achievement.HIGH_FLYER);
                checkAchievement(Achievement.SKY_KING, stats.totalJumps);
                broadcastEvent(EventType.JUMPED, null);
                GooseEasterEggs.recordSwipe(0); // Up
                break;

            case SWIPE_DOWN:
                GooseEasterEggs.recordSwipe(1); // Down
                break;

            case SWIPE_LEFT:
                GooseEasterEggs.recordSwipe(2); // Left
                break;

            case SWIPE_RIGHT:
                GooseEasterEggs.recordSwipe(3); // Right
                break;

            case CIRCLE:
                incrementAchievement(Achievement.CIRCLE_MASTER);
                // Possible easter egg activation on circle
                if (Math.random() < 0.1) {
                    GooseEasterEggs.activateRandomMode();
                }
                break;
        }

        // Track total interactions for DEDICATED achievement
        checkAchievement(Achievement.DEDICATED, stats.totalTouches);
        checkAchievement(Achievement.LOYAL_FRIEND, stats.totalTouches);
        checkAchievement(Achievement.OBSESSED, stats.totalTouches);

        // Track total gestures
        incrementAchievement(Achievement.GESTURE_PRO);
    }

    @Override
    public void onCombo(int count, float multiplier) {
        if (count > stats.highestCombo) {
            stats.highestCombo = count;
        }
        stats.totalCombos++;

        checkAchievement(Achievement.COMBO_KING, count);
        checkAchievement(Achievement.COMBO_LEGEND, count);
        checkAchievement(Achievement.COMBO_GOD, count);

        // Excitement effect on high combos
        if (count >= 5) {
            triggerExcitement();
        }
    }

    // ============== MiniGames.GameCallback IMPLEMENTATION ==============

    @Override
    public void onGameStarted(MiniGames.GameType game) {
        broadcastEvent(EventType.PLAYED, game);
    }

    @Override
    public void onGameEnded(MiniGames.GameType game, int score, boolean won) {
        if (won) {
            triggerCelebration();
            incrementAchievement(Achievement.PLAY_TIME);
        }
    }

    @Override
    public void onScoreChanged(int newScore) {
        // Could trigger particle effects on score milestones
        if (newScore > 0 && newScore % 25 == 0) {
            if (renderer != null) {
                renderer.spawnParticle(physics.getPosition(), GooseRenderer.ParticleType.STAR);
            }
        }
    }

    @Override
    public Vec2 getGoosePosition() {
        return physics != null ? physics.getPosition() : new Vec2(screenWidth / 2, screenHeight / 2);
    }

    @Override
    public void setGooseTarget(Vec2 target) {
        if (ai != null) {
            ai.setTarget(target);
        }
    }

    @Override
    public void triggerHappiness(float amount) {
        PetNeeds.get().happiness = Math.min(100, PetNeeds.get().happiness + amount);
        if (amount >= 10 && renderer != null) {
            renderer.spawnParticleBurst(physics.getPosition(), GooseRenderer.ParticleType.HEART, 5);
        }
    }
}
