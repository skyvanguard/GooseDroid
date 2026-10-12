package com.cfks.goosedroid.GooseDesktop;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.cfks.goosedroid.PetAppearance;
import com.cfks.goosedroid.PetNeeds;
import com.cfks.goosedroid.PetPersonality;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Sistema de Easter Eggs y secretos del ganso.
 * Incluye combinaciones secretas, modos especiales y sorpresas.
 */
public class GooseEasterEggs {

    private static final String TAG = "GooseEasterEggs";

    // ============== SECRETOS DESBLOQUEABLES ==============

    public enum SecretMode {
        NONE,
        DISCO_GOOSE,      // Colores cambiantes
        GIANT_GOOSE,      // Tamaño 3x
        MINI_GOOSE,       // Tamaño 0.5x
        GHOST_GOOSE,      // Semi-transparente
        RAINBOW_GOOSE,    // Trail arcoíris
        GOLDEN_GOOSE,     // Dorado brillante
        INVERTED_GOOSE,   // Camina al revés
        TURBO_GOOSE,      // Velocidad 2x
        SLEEPY_GOOSE,     // Siempre soñoliento
        PARTY_GOOSE       // Confetti constante
    }

    public enum SecretUnlock {
        KONAMI_CODE,           // ↑↑↓↓←→←→ tap pattern
        TRIPLE_HONK,           // 3 honks rápidos
        MIDNIGHT_GOOSE,        // Abrir a medianoche exacta
        BIRTHDAY_GOOSE,        // 1 de abril
        HUNDRED_PETS,          // 100 caricias en una sesión
        SPEED_TAPPER,          // 20 taps en 3 segundos
        PATIENT_OWNER,         // No tocar por 5 minutos
        FULL_BELLY,            // Alimentar 10 veces seguidas
        DANCE_MASTER,          // Ver 5 bailes
        SECRET_WORD            // Escribir palabra secreta
    }

    // ============== ESTADO ==============

    private static Context context;
    private static Random random = new Random();
    private static SecretMode currentMode = SecretMode.NONE;
    private static Map<SecretUnlock, Boolean> unlockedSecrets = new HashMap<>();
    private static List<SecretMode> availableModes = new ArrayList<>();

    // Tracking para desbloqueos
    private static List<Integer> tapPattern = new ArrayList<>(); // 0=up, 1=down, 2=left, 3=right
    private static int rapidTapCount = 0;
    private static long lastTapTime = 0;
    private static long lastInteractionTime = 0;
    private static int petsThisSession = 0;
    private static int feedsThisSession = 0;
    private static int dancesSeenThisSession = 0;
    private static int honksThisSession = 0;
    private static long honkSequenceStart = 0;

    // Konami: ↑↑↓↓←→←→
    private static final int[] KONAMI_CODE = {0, 0, 1, 1, 2, 3, 2, 3};

    // ============== INICIALIZACIÓN ==============

    public static void init(Context appContext) {
        context = appContext.getApplicationContext();

        // Inicializar secretos como no desbloqueados
        for (SecretUnlock unlock : SecretUnlock.values()) {
            unlockedSecrets.put(unlock, false);
        }

        // Modo none siempre disponible (la lista es estática: no duplicar en cada init)
        if (!availableModes.contains(SecretMode.NONE)) {
            availableModes.add(SecretMode.NONE);
        }

        lastInteractionTime = System.currentTimeMillis();
    }

    // ============== DETECCIÓN DE PATRONES ==============

    /**
     * Registrar dirección de swipe.
     * 0=arriba, 1=abajo, 2=izquierda, 3=derecha
     */
    public static void recordSwipe(int direction) {
        tapPattern.add(direction);

        // Mantener solo los últimos 8
        while (tapPattern.size() > 8) {
            tapPattern.remove(0);
        }

        // Verificar Konami code
        if (tapPattern.size() == 8) {
            boolean isKonami = true;
            for (int i = 0; i < 8; i++) {
                if (tapPattern.get(i) != KONAMI_CODE[i]) {
                    isKonami = false;
                    break;
                }
            }

            if (isKonami) {
                unlockSecret(SecretUnlock.KONAMI_CODE);
                tapPattern.clear();
            }
        }

        lastInteractionTime = System.currentTimeMillis();
    }

    /**
     * Registrar tap.
     */
    public static void recordTap() {
        long now = System.currentTimeMillis();

        // Rapid tap detection
        if (now - lastTapTime < 150) {
            rapidTapCount++;

            if (rapidTapCount >= 20) {
                unlockSecret(SecretUnlock.SPEED_TAPPER);
                rapidTapCount = 0;
            }
        } else {
            // Reset si pasó mucho tiempo
            if (now - lastTapTime > 3000) {
                rapidTapCount = 0;
            }
        }

        lastTapTime = now;
        lastInteractionTime = now;
    }

    /**
     * Registrar caricia.
     */
    public static void recordPet() {
        petsThisSession++;
        lastInteractionTime = System.currentTimeMillis();

        if (petsThisSession >= 100) {
            unlockSecret(SecretUnlock.HUNDRED_PETS);
        }
    }

    /**
     * Registrar alimentación.
     */
    public static void recordFeed() {
        feedsThisSession++;
        lastInteractionTime = System.currentTimeMillis();

        if (feedsThisSession >= 10) {
            unlockSecret(SecretUnlock.FULL_BELLY);
        }
    }

    /**
     * Registrar baile visto.
     */
    public static void recordDance() {
        dancesSeenThisSession++;

        if (dancesSeenThisSession >= 5) {
            unlockSecret(SecretUnlock.DANCE_MASTER);
        }
    }

    /**
     * Registrar honk.
     */
    public static void recordHonk() {
        long now = System.currentTimeMillis();

        if (now - honkSequenceStart > 2000) {
            // Nueva secuencia
            honksThisSession = 1;
            honkSequenceStart = now;
        } else {
            honksThisSession++;

            if (honksThisSession >= 3) {
                unlockSecret(SecretUnlock.TRIPLE_HONK);
                honksThisSession = 0;
            }
        }
    }

    /**
     * Verificar tiempo sin interacción.
     */
    public static void checkPatience() {
        long now = System.currentTimeMillis();

        // 5 minutos sin tocar
        if (now - lastInteractionTime > 5 * 60 * 1000) {
            unlockSecret(SecretUnlock.PATIENT_OWNER);
        }
    }

    /**
     * Verificar fecha especial.
     */
    public static void checkSpecialDate() {
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        // 1 de abril - April Fools
        if (month == Calendar.APRIL && day == 1) {
            unlockSecret(SecretUnlock.BIRTHDAY_GOOSE);
        }

        // Medianoche exacta (00:00)
        if (hour == 0 && minute == 0) {
            unlockSecret(SecretUnlock.MIDNIGHT_GOOSE);
        }
    }

    // ============== DESBLOQUEO ==============

    /**
     * Desbloquear un secreto.
     */
    private static void unlockSecret(SecretUnlock unlock) {
        if (unlockedSecrets.getOrDefault(unlock, false)) {
            return; // Ya desbloqueado
        }

        unlockedSecrets.put(unlock, true);
        Log.i(TAG, "Secret unlocked: " + unlock.name());

        // Dar recompensa
        SecretMode reward = getRewardForUnlock(unlock);
        if (reward != SecretMode.NONE && !availableModes.contains(reward)) {
            availableModes.add(reward);
            showUnlockMessage(unlock, reward);
        }

        // Bonus de felicidad
        PetNeeds.get().happiness = Math.min(100, PetNeeds.get().happiness + 15);

        // Los secretos cuentan para la evolución
        if (context != null) {
            com.cfks.goosedroid.GooseEvolution.recordSecretFound(context);
        }

        // Sonido especial
        if (!Sound.isSilenced()) {
            Sound.honk();
        }
    }

    /**
     * Obtener recompensa para un desbloqueo.
     */
    private static SecretMode getRewardForUnlock(SecretUnlock unlock) {
        switch (unlock) {
            case KONAMI_CODE:
                return SecretMode.PARTY_GOOSE;
            case TRIPLE_HONK:
                return SecretMode.DISCO_GOOSE;
            case MIDNIGHT_GOOSE:
                return SecretMode.GHOST_GOOSE;
            case BIRTHDAY_GOOSE:
                return SecretMode.RAINBOW_GOOSE;
            case HUNDRED_PETS:
                return SecretMode.GOLDEN_GOOSE;
            case SPEED_TAPPER:
                return SecretMode.TURBO_GOOSE;
            case PATIENT_OWNER:
                return SecretMode.SLEEPY_GOOSE;
            case FULL_BELLY:
                return SecretMode.GIANT_GOOSE;
            case DANCE_MASTER:
                return SecretMode.INVERTED_GOOSE;
            case SECRET_WORD:
                return SecretMode.MINI_GOOSE;
            default:
                return SecretMode.NONE;
        }
    }

    /**
     * Mostrar mensaje de desbloqueo.
     */
    private static void showUnlockMessage(SecretUnlock unlock, SecretMode mode) {
        if (context == null) return;

        String message = getUnlockMessage(unlock);
        try {
            Toast.makeText(context, "🔓 " + message, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.w(TAG, "Could not show toast", e);
        }
    }

    /**
     * Obtener mensaje de desbloqueo.
     */
    private static String getUnlockMessage(SecretUnlock unlock) {
        switch (unlock) {
            case KONAMI_CODE:
                return "KONAMI CODE! Party Goose unlocked!";
            case TRIPLE_HONK:
                return "HONK HONK HONK! Disco Goose unlocked!";
            case MIDNIGHT_GOOSE:
                return "Midnight visitor... Ghost Goose unlocked!";
            case BIRTHDAY_GOOSE:
                return "April Fools! Rainbow Goose unlocked!";
            case HUNDRED_PETS:
                return "So much love! Golden Goose unlocked!";
            case SPEED_TAPPER:
                return "Speed demon! Turbo Goose unlocked!";
            case PATIENT_OWNER:
                return "Patience is a virtue... Sleepy Goose unlocked!";
            case FULL_BELLY:
                return "Full tummy! Giant Goose unlocked!";
            case DANCE_MASTER:
                return "Dance party! Inverted Goose unlocked!";
            case SECRET_WORD:
                return "You found it! Mini Goose unlocked!";
            default:
                return "Secret unlocked!";
        }
    }

    // ============== ACTIVACIÓN DE MODOS ==============

    /**
     * Activar modo secreto aleatorio de los disponibles.
     */
    public static void activateRandomMode() {
        // Elegir sobre una lista filtrada: sin bucle que pueda no terminar.
        java.util.List<SecretMode> selectable = new java.util.ArrayList<>();
        for (SecretMode mode : availableModes) {
            if (mode != SecretMode.NONE) {
                selectable.add(mode);
            }
        }
        if (selectable.isEmpty()) {
            return; // Solo NONE disponible
        }

        activateMode(selectable.get(random.nextInt(selectable.size())));
    }

    /**
     * Activar un modo específico.
     */
    public static void activateMode(SecretMode mode) {
        // Un modo a la vez: revertir el anterior antes de aplicar el nuevo
        resetModeEffects();

        currentMode = mode;
        modeEndTimeMs = android.os.SystemClock.elapsedRealtime() + MODE_DURATION_MS;
        Log.i(TAG, "Secret mode activated: " + mode.name());

        // Aplicar efectos del modo
        applyModeEffects(mode);
    }

    /**
     * Apaga el modo activo cuando se cumple su duración. Llamar una vez por frame.
     */
    public static void updateMode() {
        if (currentMode != SecretMode.NONE
                && android.os.SystemClock.elapsedRealtime() >= modeEndTimeMs) {
            deactivateMode();
        }
    }

    /**
     * Desactivar modo actual.
     */
    public static void deactivateMode() {
        currentMode = SecretMode.NONE;
        resetModeEffects();
    }

    /**
     * Aplicar efectos visuales del modo.
     */
    private static void applyModeEffects(SecretMode mode) {
        switch (mode) {
            case GIANT_GOOSE:
                TheGoose.DrawScale *= GIANT_SCALE_FACTOR;
                break;
            case MINI_GOOSE:
                TheGoose.DrawScale *= MINI_SCALE_FACTOR;
                break;
            case GHOST_GOOSE:
                // Se maneja en el renderer
                break;
            case GOLDEN_GOOSE:
                // Solo el brillo dorado del renderer: la apariencia elegida por
                // el usuario se guarda en disco y no se toca.
                break;
            case TURBO_GOOSE:
                TheGoose.WanderSpeed = 400f;
                break;
            case SLEEPY_GOOSE:
                PetNeeds.get().energy = 10;
                break;
            default:
                break;
        }
    }

    /**
     * Resetear efectos de modo.
     */
    private static void resetModeEffects() {
        TheGoose.DrawScale = TheGoose.BASE_DRAW_SCALE
                * com.cfks.goosedroid.GooseEvolution.getSizeMultiplier();
        TheGoose.WanderSpeed = TheGoose.DEFAULT_WANDER_SPEED;
    }

    private static final float GIANT_SCALE_FACTOR = 1.6f;
    private static final float MINI_SCALE_FACTOR = 0.5f;
    private static final long MODE_DURATION_MS = 45_000L;
    private static long modeEndTimeMs = 0L;

    // ============== EVENTOS ESPECIALES ==============

    /**
     * Verificar si hay evento especial de fecha.
     */
    public static String getSpecialDateEvent() {
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);

        // Halloween
        if (month == Calendar.OCTOBER && day == 31) {
            return "BOO!";
        }

        // Navidad
        if (month == Calendar.DECEMBER && day == 25) {
            return "MERRY HONKMAS!";
        }

        // Año Nuevo
        if (month == Calendar.JANUARY && day == 1) {
            return "HAPPY NEW HONK!";
        }

        // San Valentín
        if (month == Calendar.FEBRUARY && day == 14) {
            return "<3 HONK <3";
        }

        // April Fools
        if (month == Calendar.APRIL && day == 1) {
            return "PRANKED!";
        }

        return null;
    }

    /**
     * Obtener respuesta secreta a palabra clave.
     */
    public static String getSecretResponse(String input) {
        if (input == null) return null;

        String lower = input.toLowerCase();

        if (lower.contains("honk")) {
            return "HONK HONK!";
        }
        if (lower.contains("goose") || lower.contains("ganso")) {
            return "That's me!";
        }
        if (lower.contains("duck") || lower.contains("pato")) {
            return ">:( I'm a GOOSE!";
        }
        if (lower.contains("bread") || lower.contains("pan")) {
            return "*excited honking*";
        }
        if (lower.contains("untitled")) {
            unlockSecret(SecretUnlock.SECRET_WORD);
            return "~UNTITLED GOOSE GAME~";
        }
        if (lower.contains("peace was never")) {
            return "...an option. >:)";
        }
        if (lower.contains("hello") || lower.contains("hola")) {
            return "HONK! (Hello!)";
        }
        if (lower.contains("love") || lower.contains("amor")) {
            return "<3 <3 <3";
        }

        return null;
    }

    // ============== SORPRESAS ALEATORIAS ==============

    /**
     * Obtener sorpresa aleatoria (baja probabilidad).
     */
    public static String getRandomSurprise() {
        if (random.nextFloat() > 0.02f) { // 2% chance
            return null;
        }

        String[] surprises = {
                "*does a little dance*",
                "*honks the melody of a song*",
                "*pretends to be a statue*",
                "*looks at you suspiciously*",
                "*winks*",
                "*strikes a pose*",
                "*does a backflip* (not really)",
                "*contemplates existence*",
                "*remembers something funny*",
                "*plots world domination*"
        };

        return surprises[random.nextInt(surprises.length)];
    }

    /**
     * Determinar si el ganso debería hacer algo especial ahora.
     */
    public static GooseAI.RandomEvent getSpecialEvent() {
        // Verificar fecha especial
        String dateEvent = getSpecialDateEvent();
        if (dateEvent != null && random.nextFloat() < 0.3f) {
            return GooseAI.RandomEvent.DANCE;
        }

        // Verificar modo activo
        switch (currentMode) {
            case DISCO_GOOSE:
                if (random.nextFloat() < 0.2f) {
                    return GooseAI.RandomEvent.DANCE;
                }
                break;
            case PARTY_GOOSE:
                if (random.nextFloat() < 0.3f) {
                    return random.nextBoolean() ? GooseAI.RandomEvent.ZOOMIES : GooseAI.RandomEvent.DANCE;
                }
                break;
            case SLEEPY_GOOSE:
                if (random.nextFloat() < 0.4f) {
                    return GooseAI.RandomEvent.YAWN;
                }
                break;
            default:
                break;
        }

        return null;
    }

    // ============== GETTERS ==============

    public static SecretMode getCurrentMode() {
        return currentMode;
    }

    public static boolean isModeActive() {
        return currentMode != SecretMode.NONE;
    }

    public static boolean isUnlocked(SecretUnlock unlock) {
        return unlockedSecrets.getOrDefault(unlock, false);
    }

    public static List<SecretMode> getAvailableModes() {
        return new ArrayList<>(availableModes);
    }

    public static int getUnlockedCount() {
        int count = 0;
        for (Boolean unlocked : unlockedSecrets.values()) {
            if (unlocked) count++;
        }
        return count;
    }

    /**
     * Resetear sesión (no los desbloqueos).
     */
    public static void resetSession() {
        rapidTapCount = 0;
        petsThisSession = 0;
        feedsThisSession = 0;
        dancesSeenThisSession = 0;
        honksThisSession = 0;
        tapPattern.clear();
        lastInteractionTime = System.currentTimeMillis();
    }
}
