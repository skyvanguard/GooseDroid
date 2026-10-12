package com.cfks.goosedroid;

import android.content.Context;
import android.content.SharedPreferences;
import com.cfks.goosedroid.GooseDesktop.Sound;
import com.cfks.goosedroid.GooseDesktop.TheGoose;

/**
 * Evolution system for the goose.
 * The goose evolves through stages based on care, time, and achievements.
 */
public class GooseEvolution {

    private static final String PREFS_NAME = "GooseEvolutionPrefs";

    // ============== EVOLUTION STAGES ==============

    public enum Stage {
        EGG(0, "Huevo", 0.45f, 0),
        HATCHLING(1, "Polluelo", 0.6f, 1),
        GOSLING(2, "Ganso Joven", 0.8f, 2),
        ADULT(3, "Ganso Adulto", 1.0f, 3),
        ELDER(4, "Ganso Sabio", 1.1f, 4),
        LEGENDARY(5, "Ganso Legendario", 1.2f, 5),
        COSMIC(6, "Ganso Cósmico", 1.3f, 6);

        public final int level;
        public final String displayName;
        public final float sizeMultiplier;
        public final int spriteVariant;

        Stage(int level, String displayName, float sizeMultiplier, int spriteVariant) {
            this.level = level;
            this.displayName = displayName;
            this.sizeMultiplier = sizeMultiplier;
            this.spriteVariant = spriteVariant;
        }

        public static Stage fromLevel(int level) {
            for (Stage s : values()) {
                if (s.level == level) return s;
            }
            return EGG;
        }
    }

    // ============== EVOLUTION REQUIREMENTS ==============

    public static class Requirements {
        public final long minAgeMinutes;      // Minimum age in minutes
        public final int minTotalPets;         // Total times petted
        public final int minTotalFeeds;        // Total times fed
        public final int minTotalPlays;        // Total times played
        public final float minAvgHappiness;    // Average happiness required
        public final int secretsUnlocked;      // Easter eggs found

        public Requirements(long minAge, int pets, int feeds, int plays, float happiness, int secrets) {
            this.minAgeMinutes = minAge;
            this.minTotalPets = pets;
            this.minTotalFeeds = feeds;
            this.minTotalPlays = plays;
            this.minAvgHappiness = happiness;
            this.secretsUnlocked = secrets;
        }
    }

    // Requirements for each evolution
    private static final Requirements[] EVOLUTION_REQUIREMENTS = {
        // EGG -> HATCHLING: 5 minutes, basic care
        new Requirements(5, 3, 2, 1, 0, 0),
        // HATCHLING -> GOSLING: 30 minutes, more care
        new Requirements(30, 15, 10, 5, 30, 0),
        // GOSLING -> ADULT: 2 hours, good care
        new Requirements(120, 50, 30, 20, 50, 0),
        // ADULT -> ELDER: 8 hours, dedicated care
        new Requirements(480, 150, 100, 75, 60, 1),
        // ELDER -> LEGENDARY: 24 hours, excellent care + secrets
        new Requirements(1440, 500, 300, 200, 70, 3),
        // LEGENDARY -> COSMIC: 72 hours, master level + all secrets
        new Requirements(4320, 1000, 600, 400, 80, 5),
    };

    // ============== STATE ==============

    private static Stage currentStage = Stage.EGG;
    private static long birthTimestamp = 0;
    private static long totalPets = 0;
    private static long totalFeeds = 0;
    private static long totalPlays = 0;
    private static float happinessAccumulator = 0;
    private static int happinessSamples = 0;
    private static int secretsFound = 0;
    private static float evolutionProgress = 0f;

    // Experience points for leveling
    private static int experiencePoints = 0;
    private static int currentLevel = 1;

    // Special abilities unlocked
    private static boolean canFly = false;
    private static boolean canSwim = false;
    private static boolean canTeleport = false;
    private static boolean hasGoldenFeathers = false;
    private static boolean hasRainbowTrail = false;

    // Listeners
    public interface EvolutionListener {
        void onEvolution(Stage oldStage, Stage newStage);
        void onLevelUp(int newLevel);
        void onAbilityUnlocked(String abilityName);
    }

    private static EvolutionListener listener;

    // ============== INITIALIZATION ==============

    public static void init(Context context) {
        load(context);
        if (birthTimestamp == 0) {
            birthTimestamp = System.currentTimeMillis();
            save(context);
        }
    }

    public static void setListener(EvolutionListener l) {
        listener = l;
    }

    // ============== GETTERS ==============

    public static Stage getCurrentStage() {
        return currentStage;
    }

    public static String getStageName() {
        return currentStage.displayName;
    }

    public static float getSizeMultiplier() {
        return currentStage.sizeMultiplier;
    }

    public static int getLevel() {
        return currentLevel;
    }

    public static int getExperience() {
        return experiencePoints;
    }

    public static int getExperienceForNextLevel() {
        return currentLevel * 100 + (currentLevel * currentLevel * 10);
    }

    public static float getEvolutionProgress() {
        return evolutionProgress;
    }

    public static long getAgeMinutes() {
        return (System.currentTimeMillis() - birthTimestamp) / 60000;
    }

    public static String getAgeString() {
        long minutes = getAgeMinutes();
        if (minutes < 60) {
            return minutes + " min";
        } else if (minutes < 1440) {
            return (minutes / 60) + "h " + (minutes % 60) + "m";
        } else {
            long days = minutes / 1440;
            long hours = (minutes % 1440) / 60;
            return days + "d " + hours + "h";
        }
    }

    // Abilities
    public static boolean canFly() { return canFly; }
    public static boolean canSwim() { return canSwim; }
    public static boolean canTeleport() { return canTeleport; }
    public static boolean hasGoldenFeathers() { return hasGoldenFeathers; }
    public static boolean hasRainbowTrail() { return hasRainbowTrail; }

    // ============== TRACKING ==============

    /**
     * Record a generic interaction (any gesture/touch).
     */
    public static void recordInteraction(Context context) {
        addExperience(context, 1);
        checkEvolution(context);
    }

    /**
     * Record a loving action (petting, feeding, etc.).
     */
    public static void recordLovingAction(Context context) {
        recordPet(context);
    }

    public static void recordPet(Context context) {
        totalPets++;
        addExperience(context, 5);
        checkEvolution(context);
    }

    public static void recordFeed(Context context) {
        totalFeeds++;
        addExperience(context, 3);
        checkEvolution(context);
    }

    public static void recordPlay(Context context) {
        totalPlays++;
        addExperience(context, 8);
        checkEvolution(context);
    }

    public static void recordHappiness(float happiness) {
        happinessAccumulator += happiness;
        happinessSamples++;
    }

    public static void recordSecretFound(Context context) {
        secretsFound++;
        addExperience(context, 50);
        checkEvolution(context);
    }

    public static void addExperience(Context context, int xp) {
        experiencePoints += xp;

        // Check for level up
        while (experiencePoints >= getExperienceForNextLevel()) {
            experiencePoints -= getExperienceForNextLevel();
            currentLevel++;

            if (listener != null) {
                listener.onLevelUp(currentLevel);
            }

            // Check for ability unlocks
            checkAbilityUnlocks(context);

            Sound.achievement();
        }

        save(context);
    }

    // ============== EVOLUTION CHECK ==============

    public static void checkEvolution(Context context) {
        if (currentStage.level >= Stage.COSMIC.level) {
            evolutionProgress = 1f;
            return; // Max evolution reached
        }

        Requirements req = EVOLUTION_REQUIREMENTS[currentStage.level];
        float avgHappiness = happinessSamples > 0 ?
            happinessAccumulator / happinessSamples : 0;

        // Calculate progress for each requirement
        float ageProgress = Math.min(1f, (float) getAgeMinutes() / req.minAgeMinutes);
        float petProgress = Math.min(1f, (float) totalPets / req.minTotalPets);
        float feedProgress = Math.min(1f, (float) totalFeeds / req.minTotalFeeds);
        float playProgress = Math.min(1f, (float) totalPlays / req.minTotalPlays);
        float happyProgress = req.minAvgHappiness > 0 ?
            Math.min(1f, avgHappiness / req.minAvgHappiness) : 1f;
        float secretProgress = req.secretsUnlocked > 0 ?
            Math.min(1f, (float) secretsFound / req.secretsUnlocked) : 1f;

        // Overall progress is the average
        evolutionProgress = (ageProgress + petProgress + feedProgress +
            playProgress + happyProgress + secretProgress) / 6f;

        // Check if all requirements are met
        if (getAgeMinutes() >= req.minAgeMinutes &&
            totalPets >= req.minTotalPets &&
            totalFeeds >= req.minTotalFeeds &&
            totalPlays >= req.minTotalPlays &&
            avgHappiness >= req.minAvgHappiness &&
            secretsFound >= req.secretsUnlocked) {

            evolve(context);
        }

        save(context);
    }

    private static void evolve(Context context) {
        Stage oldStage = currentStage;
        int newLevel = currentStage.level + 1;

        if (newLevel <= Stage.COSMIC.level) {
            currentStage = Stage.fromLevel(newLevel);

            // Reset happiness tracking for new stage
            happinessAccumulator = 0;
            happinessSamples = 0;
            evolutionProgress = 0;

            // Bonus XP for evolving
            addExperience(context, 100 * newLevel);

            // Check evolution achievements
            checkEvolutionAchievements(currentStage);

            // Notify listener
            if (listener != null) {
                listener.onEvolution(oldStage, currentStage);
            }

            // Play evolution sound
            Sound.achievement();

            save(context);
        }
    }

    /**
     * Check and unlock evolution-related achievements.
     */
    private static void checkEvolutionAchievements(Stage stage) {
        switch (stage) {
            case HATCHLING:
            case GOSLING:
                TheGoose.checkAchievement(TheGoose.Achievement.BABY_STEPS, 1);
                break;
            case ADULT:
                TheGoose.checkAchievement(TheGoose.Achievement.GROWING_UP, 1);
                break;
            case ELDER:
                TheGoose.checkAchievement(TheGoose.Achievement.ELDER_WISDOM, 1);
                break;
            case COSMIC:
                TheGoose.checkAchievement(TheGoose.Achievement.COSMIC_GOOSE, 1);
                break;
        }
    }

    // ============== ABILITIES ==============

    private static void checkAbilityUnlocks(Context context) {
        // Level-based ability unlocks
        if (currentLevel >= 5 && !canSwim) {
            canSwim = true;
            if (listener != null) listener.onAbilityUnlocked("Nadar");
        }
        if (currentLevel >= 10 && !canFly) {
            canFly = true;
            if (listener != null) listener.onAbilityUnlocked("Volar");
        }
        if (currentLevel >= 20 && !hasRainbowTrail) {
            hasRainbowTrail = true;
            if (listener != null) listener.onAbilityUnlocked("Estela Arcoíris");
        }
        if (currentLevel >= 30 && !hasGoldenFeathers) {
            hasGoldenFeathers = true;
            if (listener != null) listener.onAbilityUnlocked("Plumas Doradas");
        }
        if (currentLevel >= 50 && !canTeleport) {
            canTeleport = true;
            if (listener != null) listener.onAbilityUnlocked("Teletransporte");
        }

        // Stage-based unlocks
        if (currentStage.level >= Stage.LEGENDARY.level && !hasGoldenFeathers) {
            hasGoldenFeathers = true;
            if (listener != null) listener.onAbilityUnlocked("Plumas Doradas");
        }
        if (currentStage.level >= Stage.COSMIC.level && !canTeleport) {
            canTeleport = true;
            if (listener != null) listener.onAbilityUnlocked("Teletransporte");
        }

        save(context);
    }

    // ============== SPECIAL FORMS ==============

    /**
     * Get special visual modifiers based on evolution and abilities.
     */
    public static VisualModifiers getVisualModifiers() {
        VisualModifiers mods = new VisualModifiers();

        mods.sizeMultiplier = currentStage.sizeMultiplier;
        mods.hasGlow = currentStage.level >= Stage.ELDER.level;
        mods.glowColor = getGlowColorForStage();
        mods.hasRainbowTrail = hasRainbowTrail;
        mods.hasGoldenFeathers = hasGoldenFeathers;
        mods.particleType = getParticleTypeForStage();

        return mods;
    }

    private static int getGlowColorForStage() {
        switch (currentStage) {
            case ELDER: return 0x4400FF00;      // Green glow
            case LEGENDARY: return 0x44FFD700;  // Gold glow
            case COSMIC: return 0x44FF00FF;     // Purple/cosmic glow
            default: return 0x00000000;          // No glow
        }
    }

    private static String getParticleTypeForStage() {
        switch (currentStage) {
            case LEGENDARY: return "sparkles";
            case COSMIC: return "stars";
            default: return "none";
        }
    }

    public static class VisualModifiers {
        public float sizeMultiplier = 1f;
        public boolean hasGlow = false;
        public int glowColor = 0;
        public boolean hasRainbowTrail = false;
        public boolean hasGoldenFeathers = false;
        public String particleType = "none";
    }

    // ============== EGG HATCHING ==============

    /**
     * Check if egg is ready to hatch.
     */
    public static boolean isEggReadyToHatch() {
        if (currentStage != Stage.EGG) return false;
        return getAgeMinutes() >= 5 && totalPets >= 3;
    }

    /**
     * Get egg crack level (0-5) for animation.
     */
    public static int getEggCrackLevel() {
        if (currentStage != Stage.EGG) return 5;
        int cracks = (int) Math.min(5, totalPets);
        return cracks;
    }

    /**
     * Attempt to hatch the egg.
     */
    public static boolean tryHatch(Context context) {
        if (isEggReadyToHatch()) {
            checkEvolution(context);
            return currentStage != Stage.EGG;
        }
        return false;
    }

    // ============== PERSISTENCE ==============

    public static void save(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putInt("stage", currentStage.level);
        editor.putLong("birthTimestamp", birthTimestamp);
        editor.putLong("totalPets", totalPets);
        editor.putLong("totalFeeds", totalFeeds);
        editor.putLong("totalPlays", totalPlays);
        editor.putFloat("happinessAccum", happinessAccumulator);
        editor.putInt("happinessSamples", happinessSamples);
        editor.putInt("secretsFound", secretsFound);
        editor.putInt("experiencePoints", experiencePoints);
        editor.putInt("currentLevel", currentLevel);

        editor.putBoolean("canFly", canFly);
        editor.putBoolean("canSwim", canSwim);
        editor.putBoolean("canTeleport", canTeleport);
        editor.putBoolean("hasGoldenFeathers", hasGoldenFeathers);
        editor.putBoolean("hasRainbowTrail", hasRainbowTrail);

        editor.apply();
    }

    public static void load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        currentStage = Stage.fromLevel(prefs.getInt("stage", 0));
        birthTimestamp = prefs.getLong("birthTimestamp", 0);
        totalPets = prefs.getLong("totalPets", 0);
        totalFeeds = prefs.getLong("totalFeeds", 0);
        totalPlays = prefs.getLong("totalPlays", 0);
        happinessAccumulator = prefs.getFloat("happinessAccum", 0);
        happinessSamples = prefs.getInt("happinessSamples", 0);
        secretsFound = prefs.getInt("secretsFound", 0);
        experiencePoints = prefs.getInt("experiencePoints", 0);
        currentLevel = prefs.getInt("currentLevel", 1);

        canFly = prefs.getBoolean("canFly", false);
        canSwim = prefs.getBoolean("canSwim", false);
        canTeleport = prefs.getBoolean("canTeleport", false);
        hasGoldenFeathers = prefs.getBoolean("hasGoldenFeathers", false);
        hasRainbowTrail = prefs.getBoolean("hasRainbowTrail", false);
    }

    /**
     * Reset evolution (for testing or new game).
     */
    public static void reset(Context context) {
        currentStage = Stage.EGG;
        birthTimestamp = System.currentTimeMillis();
        totalPets = 0;
        totalFeeds = 0;
        totalPlays = 0;
        happinessAccumulator = 0;
        happinessSamples = 0;
        secretsFound = 0;
        evolutionProgress = 0;
        experiencePoints = 0;
        currentLevel = 1;
        canFly = false;
        canSwim = false;
        canTeleport = false;
        hasGoldenFeathers = false;
        hasRainbowTrail = false;

        save(context);
    }

    // ============== DEBUG ==============

    public static String getDebugInfo() {
        return String.format(
            "Stage: %s (Lv.%d)\nAge: %s\nXP: %d/%d\n" +
            "Pets: %d | Feeds: %d | Plays: %d\n" +
            "Secrets: %d | Progress: %.0f%%",
            currentStage.displayName, currentLevel,
            getAgeString(),
            experiencePoints, getExperienceForNextLevel(),
            totalPets, totalFeeds, totalPlays,
            secretsFound, evolutionProgress * 100
        );
    }
}
