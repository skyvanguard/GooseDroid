package com.skyvanguard.goozy;

/**
 * Sistema de necesidades tipo Tamagotchi para la mascota virtual.
 * Gestiona hambre, energia y felicidad que decaen con el tiempo.
 */
public class PetNeeds {
    // Valores 0-100
    public float hunger = 50f;      // 100 = muy hambriento
    public float energy = 100f;     // 0 = cansado
    public float happiness = 75f;   // 0 = triste
    public float hygiene = 100f;    // 0 = sucio
    public float health = 100f;     // 0 = muy enfermo

    private static final float SECONDS_PER_HOUR = 3600f;

    // Tasas de decaimiento (por segundo), en escala de horas: una mascota se
    // cuida unas pocas veces al día, no cada tres minutos.
    public static final float HUNGER_RATE = 100f / (8f * SECONDS_PER_HOUR);     // 0 -> 100 en 8 h
    public static final float ENERGY_RATE = 100f / (12f * SECONDS_PER_HOUR);    // 100 -> 0 en 12 h
    public static final float HAPPINESS_RATE = 100f / (6f * SECONDS_PER_HOUR);  // 100 -> 0 en 6 h
    /** Pérdida extra de felicidad por cada necesidad descuidada. */
    public static final float NEGLECT_HAPPINESS_RATE = HAPPINESS_RATE * 0.5f;

    public static final float HYGIENE_RATE = 100f / (24f * SECONDS_PER_HOUR);    // 100 -> 0 en 24 h
    /** Salud: se pierde si alguna necesidad está al límite y se recupera sola si está bien cuidado. */
    public static final float HEALTH_LOSS_RATE = 100f / (12f * SECONDS_PER_HOUR);
    public static final float HEALTH_RECOVERY_RATE = 100f / (24f * SECONDS_PER_HOUR);
    /** Por debajo de esto el ganso está enfermo. */
    public static final float SICK_THRESHOLD = 40f;
    public static final float OFFLINE_MIN_HYGIENE = 20f;
    /** Suciedad que suma embarrarse. */
    public static final float MUD_SOIL_AMOUNT = 15f;
    private static final float BATH_HAPPINESS_BONUS = 5f;
    private static final float MEDICINE_HEALTH = 50f;
    private static final float MEDICINE_HAPPINESS_COST = 5f;

    // Tiempo sin pantalla: el ganso descansa, así que recupera energía, y lo
    // demás decae a media velocidad.
    public static final float OFFLINE_DECAY_FACTOR = 0.5f;
    public static final float OFFLINE_ENERGY_RECOVERY_RATE = 100f / (8f * SECONDS_PER_HOUR);

    // Pisos para el tiempo offline: volver tras una noche nunca encuentra a la
    // mascota en el peor estado posible.
    public static final float OFFLINE_MAX_HUNGER = 85f;
    public static final float OFFLINE_MIN_HAPPINESS = 25f;

    // Timestamp de ultima actualizacion
    private long lastUpdateTime = System.currentTimeMillis();

    // Estado actual de la mascota
    public enum MoodState {
        HUNGRY,
        TIRED,
        SAD,
        HAPPY,
        NEUTRAL,
        DIRTY,
        SICK
    }

    public PetNeeds() {
    }

    /**
     * Retorna la instancia singleton via PetState.
     */
    public static PetNeeds get() {
        return PetState.getInstance().needs;
    }

    /**
     * Actualiza las necesidades basandose en el tiempo transcurrido.
     * Debe ser llamado en cada frame del game loop.
     */
    public void update(float deltaTime) {
        hunger = Math.min(100, hunger + HUNGER_RATE * deltaTime);
        energy = Math.max(0, energy - ENERGY_RATE * deltaTime);
        happiness = Math.max(0, happiness - HAPPINESS_RATE * deltaTime);

        // Efectos cruzados - hambre y cansancio afectan felicidad
        if (hunger > 80) {
            happiness = Math.max(0, happiness - NEGLECT_HAPPINESS_RATE * deltaTime);
        }
        if (energy < 20) {
            happiness = Math.max(0, happiness - NEGLECT_HAPPINESS_RATE * deltaTime);
        }

        hygiene = Math.max(0, hygiene - HYGIENE_RATE * deltaTime);
        if (isNeglected()) {
            health = Math.max(0, health - HEALTH_LOSS_RATE * deltaTime);
        } else if (isWellCaredFor()) {
            health = Math.min(100, health + HEALTH_RECOVERY_RATE * deltaTime);
        }
    }

    /** Alguna necesidad está al límite: la salud se resiente. */
    public boolean isNeglected() {
        return hunger > 90 || energy < 10 || happiness < 10 || hygiene < 15;
    }

    /** Todo razonablemente bien: la salud se recupera sola. */
    public boolean isWellCaredFor() {
        return hunger < 60 && energy > 30 && happiness > 30 && hygiene > 40;
    }

    public boolean isSick() {
        return health < SICK_THRESHOLD;
    }

    /**
     * Actualiza las necesidades considerando el tiempo offline.
     * Util cuando la app se reanuda despues de estar pausada.
     */
    public void updateOfflineTime() {
        long currentTime = System.currentTimeMillis();
        applyOfflineTime((currentTime - lastUpdateTime) / 1000f);
        lastUpdateTime = currentTime;
    }

    /**
     * Aplica el efecto de un período sin pantalla.
     * Un reloj del sistema atrasado da un tiempo negativo, que se ignora.
     */
    public void applyOfflineTime(float elapsedSeconds) {
        if (elapsedSeconds <= 0) return;

        // Los pisos solo frenan el decaimiento offline: no mejoran un valor que
        // ya estaba peor.
        float hungerCap = Math.max(hunger, OFFLINE_MAX_HUNGER);
        float happinessFloor = Math.min(happiness, OFFLINE_MIN_HAPPINESS);

        hunger = Math.min(hungerCap,
                hunger + HUNGER_RATE * OFFLINE_DECAY_FACTOR * elapsedSeconds);
        happiness = Math.max(happinessFloor,
                happiness - HAPPINESS_RATE * OFFLINE_DECAY_FACTOR * elapsedSeconds);
        energy = Math.min(100, energy + OFFLINE_ENERGY_RECOVERY_RATE * elapsedSeconds);

        float hygieneFloor = Math.min(hygiene, OFFLINE_MIN_HYGIENE);
        hygiene = Math.max(hygieneFloor,
                hygiene - HYGIENE_RATE * OFFLINE_DECAY_FACTOR * elapsedSeconds);
        // La salud no cambia sin pantalla: nadie puede cuidarlo ni descuidarlo
    }

    /**
     * Alimenta a la mascota, reduciendo hambre y aumentando felicidad.
     */
    public void feed() {
        hunger = Math.max(0, hunger - 40);
        happiness = Math.min(100, happiness + 10);
        PetPersonality.get().onFed();
    }

    /**
     * Acaricia a la mascota, aumentando felicidad.
     */
    public void pet() {
        happiness = Math.min(100, happiness + 15);
        PetPersonality.get().onPetted();
    }

    /**
     * La mascota duerme, recuperando energia.
     */
    public void sleep() {
        energy = Math.min(100, energy + 30);
        happiness = Math.min(100, happiness + 5);
    }

    /** Se embarra (por ejemplo al dejar huellas de barro). */
    public void soil(float amount) {
        hygiene = Math.max(0, hygiene - Math.max(0, amount));
    }

    /** Un baño: queda limpio y a los gansos les encanta el agua. */
    public void clean() {
        hygiene = 100f;
        happiness = Math.min(100, happiness + BATH_HAPPINESS_BONUS);
    }

    /** Un remedio: mejora la salud, aunque no le guste el gusto. */
    public void heal() {
        health = Math.min(100, health + MEDICINE_HEALTH);
        happiness = Math.max(0, happiness - MEDICINE_HAPPINESS_COST);
    }

    /**
     * Jugar con la mascota, aumenta felicidad pero gasta energia.
     */
    public void play() {
        happiness = Math.min(100, happiness + 20);
        energy = Math.max(0, energy - 10);
        hunger = Math.min(100, hunger + 5);
        PetPersonality.get().onPlayed();
    }

    /**
     * Obtiene el estado de animo actual basado en las necesidades.
     */
    public MoodState getMoodState() {
        if (isSick()) return MoodState.SICK;
        if (hunger > 80) return MoodState.HUNGRY;
        if (energy < 20) return MoodState.TIRED;
        if (hygiene < 25) return MoodState.DIRTY;
        if (happiness < 30) return MoodState.SAD;
        if (happiness > 80) return MoodState.HAPPY;
        return MoodState.NEUTRAL;
    }

    /**
     * Obtiene el estado de animo como string para el config.
     */
    public String getMoodStateString() {
        return getMoodState().name().toLowerCase();
    }

    /**
     * Verifica si la mascota necesita atencion urgente.
     */
    public boolean needsUrgentAttention() {
        return hunger > 90 || energy < 10 || happiness < 20 || hygiene < 10 || health < 30;
    }

    /**
     * Verifica si la mascota esta en buen estado general.
     */
    public boolean isHealthy() {
        return hunger < 50 && energy > 50 && happiness > 50;
    }

    /**
     * Obtiene el porcentaje general de bienestar (0-100).
     */
    public float getOverallWellbeing() {
        float hungerScore = 100 - hunger;  // Invertir porque 0 hambre = bueno
        return (hungerScore + energy + happiness) / 3f;
    }

    /**
     * Resetea las necesidades a valores por defecto.
     */
    public void reset() {
        hunger = 50f;
        energy = 100f;
        happiness = 75f;
        hygiene = 100f;
        health = 100f;
        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Carga el estado desde valores guardados.
     */
    public void loadState(float savedHunger, float savedEnergy, float savedHappiness, long savedTimestamp) {
        hunger = Math.max(0, Math.min(100, savedHunger));
        energy = Math.max(0, Math.min(100, savedEnergy));
        happiness = Math.max(0, Math.min(100, savedHappiness));
        lastUpdateTime = savedTimestamp > 0 ? savedTimestamp : System.currentTimeMillis();
        updateOfflineTime();
    }

    /** Carga higiene y salud guardadas (se aplican antes del tiempo offline). */
    public void loadCare(float savedHygiene, float savedHealth) {
        hygiene = Math.max(0, Math.min(100, savedHygiene));
        health = Math.max(0, Math.min(100, savedHealth));
    }

    /**
     * Obtiene el timestamp de ultima actualizacion.
     */
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * Actualiza el timestamp (llamar despues de guardar).
     */
    public void markUpdated() {
        lastUpdateTime = System.currentTimeMillis();
    }
}
