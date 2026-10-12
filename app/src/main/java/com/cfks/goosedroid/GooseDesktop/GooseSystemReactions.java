package com.cfks.goosedroid.GooseDesktop;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import com.cfks.goosedroid.PetNeeds;
import com.cfks.goosedroid.PetPersonality;
import com.cfks.goosedroid.core.Time;

import java.util.Calendar;
import java.util.Random;

/**
 * Sistema de reacciones del ganso al estado del sistema.
 * Reacciona a batería, hora del día, tiempo sin usar la app, etc.
 */
public class GooseSystemReactions {

    private static final String TAG = "GooseSystemReactions";

    // ============== ESTADO DEL SISTEMA ==============

    public enum BatteryState {
        CRITICAL,    // < 10%
        LOW,         // 10-20%
        MEDIUM,      // 20-50%
        GOOD,        // 50-80%
        FULL         // > 80%
    }

    public enum TimeOfDay {
        DAWN,        // 5-7
        MORNING,     // 7-12
        AFTERNOON,   // 12-17
        EVENING,     // 17-20
        NIGHT,       // 20-23
        LATE_NIGHT   // 23-5
    }

    public enum AbsenceReaction {
        NONE,
        MISSED_YOU,      // 1-3 días
        WORRIED,         // 3-7 días
        ABANDONED,       // 7-14 días
        FORGOTTEN,       // 14+ días
        ECSTATIC_RETURN  // Cuando vuelves después de ausencia
    }

    // ============== REACCIONES ==============

    public static class Reaction {
        public String emoji;
        public String message;
        public float moodChange;
        public boolean triggerSound;
        public boolean triggerEvent;
        public GooseAI.RandomEvent eventToTrigger;

        public Reaction(String emoji, String message, float moodChange) {
            this.emoji = emoji;
            this.message = message;
            this.moodChange = moodChange;
            this.triggerSound = false;
            this.triggerEvent = false;
        }

        public Reaction withSound() {
            this.triggerSound = true;
            return this;
        }

        public Reaction withEvent(GooseAI.RandomEvent event) {
            this.triggerEvent = true;
            this.eventToTrigger = event;
            return this;
        }
    }

    // ============== ESTADO ==============

    private static Context context;
    private static Random random = new Random();
    private static long lastOpenTime = 0;
    private static long lastReactionTime = 0;
    private static BatteryState lastBatteryState = BatteryState.GOOD;
    private static TimeOfDay lastTimeOfDay = TimeOfDay.AFTERNOON;
    private static boolean hasReactedToReturn = false;

    private static final long REACTION_COOLDOWN = 60000; // 1 minuto entre reacciones

    // ============== INICIALIZACIÓN ==============

    public static void init(Context appContext) {
        context = appContext.getApplicationContext();
        lastOpenTime = System.currentTimeMillis();
    }

    public static void onAppOpened() {
        long now = System.currentTimeMillis();
        long timeSinceLastOpen = now - lastOpenTime;
        lastOpenTime = now;
        hasReactedToReturn = false;
    }

    // ============== DETECTORES DE ESTADO ==============

    /**
     * Obtener nivel de batería.
     */
    public static int getBatteryLevel() {
        Intent batteryStatus = readBatteryStatus();
        if (batteryStatus == null) return 100;
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) return 100;
        return (int) ((level / (float) scale) * 100);
    }

    private static final long BATTERY_CACHE_MS = 60_000L;
    private static Intent cachedBatteryStatus = null;
    private static long batteryStatusTimeMs = 0L;

    /**
     * Estado de la batería, leído del sistema como mucho una vez por minuto:
     * cada lectura es una llamada entre procesos y esto se consulta seguido.
     */
    private static Intent readBatteryStatus() {
        if (context == null) return null;
        long now = android.os.SystemClock.elapsedRealtime();
        if (cachedBatteryStatus != null && now - batteryStatusTimeMs < BATTERY_CACHE_MS) {
            return cachedBatteryStatus;
        }
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            cachedBatteryStatus = context.registerReceiver(null, filter);
            batteryStatusTimeMs = now;
        } catch (RuntimeException e) {
            Log.w(TAG, "Error reading battery status", e);
        }
        return cachedBatteryStatus;
    }

    /**
     * Obtener estado de batería.
     */
    public static BatteryState getBatteryState() {
        int level = getBatteryLevel();

        if (level < 10) return BatteryState.CRITICAL;
        if (level < 20) return BatteryState.LOW;
        if (level < 50) return BatteryState.MEDIUM;
        if (level < 80) return BatteryState.GOOD;
        return BatteryState.FULL;
    }

    /**
     * Verificar si está cargando.
     */
    public static boolean isCharging() {
        Intent batteryStatus = readBatteryStatus();
        if (batteryStatus == null) return false;
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
               status == BatteryManager.BATTERY_STATUS_FULL;
    }

    /**
     * Obtener hora del día.
     */
    public static TimeOfDay getTimeOfDay() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        if (hour >= 5 && hour < 7) return TimeOfDay.DAWN;
        if (hour >= 7 && hour < 12) return TimeOfDay.MORNING;
        if (hour >= 12 && hour < 17) return TimeOfDay.AFTERNOON;
        if (hour >= 17 && hour < 20) return TimeOfDay.EVENING;
        if (hour >= 20 && hour < 23) return TimeOfDay.NIGHT;
        return TimeOfDay.LATE_NIGHT;
    }

    /**
     * Obtener días desde última vez que abrió la app.
     */
    public static int getDaysSinceLastOpen(long lastSavedTime) {
        if (lastSavedTime == 0) return 0;
        long diff = System.currentTimeMillis() - lastSavedTime;
        return (int) (diff / (24 * 60 * 60 * 1000));
    }

    /**
     * Obtener reacción de ausencia.
     */
    public static AbsenceReaction getAbsenceReaction(long lastSavedTime) {
        int days = getDaysSinceLastOpen(lastSavedTime);

        if (days < 1) return AbsenceReaction.NONE;
        if (days < 3) return AbsenceReaction.MISSED_YOU;
        if (days < 7) return AbsenceReaction.WORRIED;
        if (days < 14) return AbsenceReaction.ABANDONED;
        return AbsenceReaction.FORGOTTEN;
    }

    // ============== GENERADORES DE REACCIONES ==============

    /**
     * Verificar y generar reacción si corresponde.
     */
    public static Reaction checkForReaction(long lastSavedTime) {
        long now = System.currentTimeMillis();

        // Cooldown entre reacciones
        if (now - lastReactionTime < REACTION_COOLDOWN) {
            return null;
        }

        // Prioridad 1: Reacción a ausencia (solo una vez al volver)
        if (!hasReactedToReturn) {
            Reaction absenceReaction = getAbsenceReactionResponse(lastSavedTime);
            if (absenceReaction != null) {
                hasReactedToReturn = true;
                lastReactionTime = now;
                return absenceReaction;
            }
        }

        // Prioridad 2: Reacción a batería
        BatteryState currentBattery = getBatteryState();
        if (currentBattery != lastBatteryState) {
            Reaction batteryReaction = getBatteryReaction(currentBattery);
            lastBatteryState = currentBattery;
            if (batteryReaction != null && random.nextFloat() < 0.5f) {
                lastReactionTime = now;
                return batteryReaction;
            }
        }

        // Prioridad 3: Reacción a hora del día
        TimeOfDay currentTime = getTimeOfDay();
        if (currentTime != lastTimeOfDay) {
            Reaction timeReaction = getTimeReaction(currentTime);
            lastTimeOfDay = currentTime;
            if (timeReaction != null && random.nextFloat() < 0.3f) {
                lastReactionTime = now;
                return timeReaction;
            }
        }

        // Prioridad 4: Comentarios aleatorios
        if (random.nextFloat() < 0.01f) { // 1% chance per check
            lastReactionTime = now;
            return getRandomComment();
        }

        return null;
    }

    /**
     * Reacción a ausencia.
     */
    private static Reaction getAbsenceReactionResponse(long lastSavedTime) {
        AbsenceReaction absence = getAbsenceReaction(lastSavedTime);
        int days = getDaysSinceLastOpen(lastSavedTime);

        switch (absence) {
            case MISSED_YOU:
                PetNeeds.get().happiness = Math.min(100, PetNeeds.get().happiness + 10);
                return new Reaction("!!!", "You're back! (" + days + " days)", 10)
                        .withSound()
                        .withEvent(GooseAI.RandomEvent.ZOOMIES);

            case WORRIED:
                PetNeeds.get().happiness = Math.max(30, PetNeeds.get().happiness - 10);
                return new Reaction(":(", days + " days... I was worried!", -5)
                        .withSound();

            case ABANDONED:
                PetNeeds.get().happiness = Math.max(20, PetNeeds.get().happiness - 20);
                PetNeeds.get().hunger = Math.min(100, PetNeeds.get().hunger + 30);
                return new Reaction("T_T", days + " days alone...", -15)
                        .withSound();

            case FORGOTTEN:
                PetNeeds.get().happiness = 10;
                PetNeeds.get().hunger = 100;
                PetNeeds.get().energy = 20;
                return new Reaction("...", "Did you forget me? (" + days + " days)", -30)
                        .withEvent(GooseAI.RandomEvent.PLAY_DEAD);

            default:
                return null;
        }
    }

    /**
     * Reacción a nivel de batería.
     */
    private static Reaction getBatteryReaction(BatteryState state) {
        boolean charging = isCharging();

        switch (state) {
            case CRITICAL:
                if (charging) {
                    return new Reaction("PHEW", "Just in time!", 0);
                }
                return new Reaction("!!!", "BATTERY DYING!", -5)
                        .withSound()
                        .withEvent(GooseAI.RandomEvent.SHAKE);

            case LOW:
                if (charging) {
                    return new Reaction(":)", "Good, charging!", 5);
                }
                return new Reaction("!", "Low battery...", -2);

            case FULL:
                if (!charging) {
                    return new Reaction("FULL!", "Full power!", 5)
                            .withEvent(GooseAI.RandomEvent.DANCE);
                }
                return new Reaction("100%", "Fully charged!", 3);

            default:
                return null;
        }
    }

    /**
     * Reacción a hora del día.
     */
    private static Reaction getTimeReaction(TimeOfDay time) {
        switch (time) {
            case DAWN:
                return new Reaction("*yawn*", "Early bird...", 0)
                        .withEvent(GooseAI.RandomEvent.STRETCH);

            case MORNING:
                return new Reaction(":D", "Good morning!", 5)
                        .withSound();

            case AFTERNOON:
                if (random.nextFloat() < 0.5f) {
                    return new Reaction("HONK", "Afternoon honk!", 0)
                            .withSound();
                }
                return null;

            case EVENING:
                return new Reaction("~", "Evening time...", 0)
                        .withEvent(GooseAI.RandomEvent.YAWN);

            case NIGHT:
                PetNeeds.get().energy = Math.max(0, PetNeeds.get().energy - 5);
                return new Reaction("ZZZ?", "Getting sleepy...", 0);

            case LATE_NIGHT:
                if (PetNeeds.get().energy < 30) {
                    return new Reaction("ZZZ", "So late... so tired...", -3)
                            .withEvent(GooseAI.RandomEvent.YAWN);
                }
                // Still up late
                return new Reaction(">:)", "Night owl mode!", 2)
                        .withEvent(GooseAI.RandomEvent.RANDOM_HONK);

            default:
                return null;
        }
    }

    /**
     * Comentario aleatorio.
     */
    private static Reaction getRandomComment() {
        String[] comments = {
                "HONK|Just because|0",
                "...|Thinking...|0",
                "?|What's that?|0",
                ":3|Feeling cute|3",
                "BREAD?|Hungry...|0",
                "*poke*|Hey!|0",
                "LA LA|Music time!|2",
                ">:D|Mischief time!|0",
                "PET ME|I need pets|0",
                "BORED|Do something!|0"
        };

        String[] parts = comments[random.nextInt(comments.length)].split("\\|");
        return new Reaction(parts[0], parts[1], Float.parseFloat(parts[2]));
    }

    // ============== REACCIONES ESPECIALES ==============

    /**
     * Reacción a ser lanzado.
     */
    public static Reaction getThrowReaction() {
        String[] reactions = {
                "WHEEE!|That was fun!|5",
                "AGAIN!|Do it again!|3",
                "HONK!|Wooooo!|2",
                "X_X|Dizzy...|0",
                ">:(|Hey!|-2"
        };
        String[] parts = reactions[random.nextInt(reactions.length)].split("\\|");
        return new Reaction(parts[0], parts[1], Float.parseFloat(parts[2]));
    }

    /**
     * Reacción a ser acariciado mucho.
     */
    public static Reaction getPetOverloadReaction() {
        return new Reaction("<3<3<3", "So much love!", 10)
                .withSound()
                .withEvent(GooseAI.RandomEvent.DANCE);
    }

    /**
     * Reacción a combo alto.
     */
    public static Reaction getComboReaction(int combo) {
        if (combo >= 10) {
            return new Reaction("WOW!", combo + "x COMBO!", 5)
                    .withSound()
                    .withEvent(GooseAI.RandomEvent.ZOOMIES);
        } else if (combo >= 5) {
            return new Reaction("NICE!", combo + "x!", 2).withSound();
        }
        return null;
    }

    /**
     * Reacción al alcanzar máxima felicidad.
     */
    public static Reaction getMaxHappinessReaction() {
        return new Reaction("PERFECT!", "Maximum happiness!", 0)
                .withSound()
                .withEvent(GooseAI.RandomEvent.DANCE);
    }

    /**
     * Reacción a primera interacción del día.
     */
    public static Reaction getFirstInteractionReaction() {
        TimeOfDay time = getTimeOfDay();
        String greeting;

        switch (time) {
            case DAWN:
            case MORNING:
                greeting = "Good morning!";
                break;
            case AFTERNOON:
                greeting = "Good afternoon!";
                break;
            case EVENING:
                greeting = "Good evening!";
                break;
            default:
                greeting = "Hello night owl!";
        }

        return new Reaction("HI!", greeting, 5).withSound();
    }
}
