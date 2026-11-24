package com.skyvanguard.goozy;

/**
 * Sistema de personalidad evolutiva para la mascota virtual.
 * Los rasgos evolucionan segun el trato del usuario.
 */
public class PetPersonality {
    // Rasgos (-100 a +100)
    public float playfulness = 0f;   // -100=serio, +100=jugueton
    public float affection = 0f;     // -100=distante, +100=carinoso
    public float bravery = 0f;       // -100=timido, +100=valiente
    public float mischief = 50f;     // -100=obediente, +100=travieso

    // Contador de interacciones
    private int totalPets = 0;
    private int totalPlays = 0;
    private int totalFeeds = 0;
    private long lastInteractionTime = System.currentTimeMillis();

    public PetPersonality() {
    }

    /**
     * Retorna la instancia singleton via PetState.
     */
    public static PetPersonality get() {
        return PetState.getInstance().personality;
    }

    /**
     * Llamado cuando el usuario acaricia a la mascota.
     */
    public void onPetted() {
        affection = clamp(affection + 2f);
        playfulness = clamp(playfulness + 0.5f);
        mischief = clamp(mischief - 0.3f);
        totalPets++;
        lastInteractionTime = System.currentTimeMillis();
    }

    /**
     * Llamado cuando el usuario juega con la mascota.
     */
    public void onPlayed() {
        playfulness = clamp(playfulness + 3f);
        bravery = clamp(bravery + 1f);
        affection = clamp(affection + 1f);
        totalPlays++;
        lastInteractionTime = System.currentTimeMillis();
    }

    /**
     * Llamado cuando el usuario alimenta a la mascota.
     */
    public void onFed() {
        affection = clamp(affection + 1f);
        totalFeeds++;
        lastInteractionTime = System.currentTimeMillis();
    }

    /**
     * Llamado cuando el usuario regana a la mascota (tap fuerte).
     */
    public void onScolded() {
        mischief = clamp(mischief - 5f);
        affection = clamp(affection - 3f);
        bravery = clamp(bravery - 1f);
        lastInteractionTime = System.currentTimeMillis();
    }

    /**
     * Procesa el tiempo ignorado (sin interaccion).
     * @param hours Horas sin interaccion
     */
    public void onIgnored(float hours) {
        affection = clamp(affection - hours * 2f);
        playfulness = clamp(playfulness - hours * 0.5f);
    }

    /**
     * Verifica si la mascota ha sido ignorada y ajusta personalidad.
     */
    public void checkIgnored() {
        long currentTime = System.currentTimeMillis();
        float hoursSinceInteraction = (currentTime - lastInteractionTime) / (1000f * 60f * 60f);

        if (hoursSinceInteraction > 1) {
            onIgnored(Math.min(hoursSinceInteraction, 24f));
            lastInteractionTime = currentTime;
        }
    }

    // --- Metodos que influyen en el comportamiento ---

    /**
     * Multiplicador para la duracion del vagabundeo.
     * Mascotas mas juguetonas vagan mas tiempo.
     */
    public float getWanderDurationMultiplier() {
        return 1f + (playfulness / 200f); // 0.5x a 1.5x
    }

    /**
     * Probabilidad de que la mascota se acerque al usuario.
     */
    public boolean shouldApproachUser() {
        return affection > 30 && Math.random() * 100 < affection;
    }

    /**
     * Probabilidad de comportamiento travieso.
     */
    public boolean shouldBeMischievous() {
        return Math.random() * 100 < (mischief + 50) / 2;
    }

    /**
     * Probabilidad de explorar areas nuevas.
     */
    public boolean shouldExplore() {
        return Math.random() * 100 < (bravery + 100) / 2;
    }

    /**
     * Velocidad de movimiento influenciada por personalidad.
     * Mascotas timidas se mueven mas lento.
     */
    public float getSpeedMultiplier() {
        float braveBonus = bravery / 200f;      // -0.5 a +0.5
        float playBonus = playfulness / 400f;   // -0.25 a +0.25
        return 1f + braveBonus + playBonus;     // 0.25x a 1.75x
    }

    /**
     * Tiempo de reaccion a interacciones.
     * Mascotas carinosas reaccionan mas rapido.
     */
    public float getReactionSpeed() {
        return 1f + (affection / 100f); // 0x a 2x
    }

    // --- Getters para tipo de personalidad ---

    /**
     * Obtiene el tipo de personalidad dominante.
     */
    public String getPersonalityType() {
        float maxTrait = Math.max(Math.max(Math.abs(playfulness), Math.abs(affection)),
                                  Math.max(Math.abs(bravery), Math.abs(mischief)));

        if (Math.abs(playfulness) == maxTrait) {
            return playfulness > 0 ? "Playful" : "Serious";
        } else if (Math.abs(affection) == maxTrait) {
            return affection > 0 ? "Affectionate" : "Distant";
        } else if (Math.abs(bravery) == maxTrait) {
            return bravery > 0 ? "Brave" : "Timid";
        } else {
            return mischief > 0 ? "Mischievous" : "Obedient";
        }
    }

    /**
     * Obtiene el nivel de experiencia basado en interacciones totales.
     */
    public int getLevel() {
        int totalInteractions = totalPets + totalPlays + totalFeeds;
        if (totalInteractions < 10) return 1;
        if (totalInteractions < 50) return 2;
        if (totalInteractions < 100) return 3;
        if (totalInteractions < 250) return 4;
        if (totalInteractions < 500) return 5;
        return 6;
    }

    /**
     * Obtiene el titulo basado en nivel y personalidad.
     */
    public String getTitle() {
        int level = getLevel();
        String type = getPersonalityType();

        String[] prefixes = {"Baby", "Young", "Growing", "Mature", "Elder", "Legendary"};
        return prefixes[level - 1] + " " + type + " Goose";
    }

    // --- Persistencia ---

    /**
     * Resetea la personalidad a valores por defecto.
     */
    public void reset() {
        playfulness = 0f;
        affection = 0f;
        bravery = 0f;
        mischief = 50f;
        totalPets = 0;
        totalPlays = 0;
        totalFeeds = 0;
        lastInteractionTime = System.currentTimeMillis();
    }

    /**
     * Carga el estado desde valores guardados.
     */
    public void loadState(float savedPlayfulness, float savedAffection,
                                  float savedBravery, float savedMischief,
                                  int savedPets, int savedPlays, int savedFeeds,
                                  long savedInteractionTime) {
        playfulness = clamp(savedPlayfulness);
        affection = clamp(savedAffection);
        bravery = clamp(savedBravery);
        mischief = clamp(savedMischief);
        totalPets = savedPets;
        totalPlays = savedPlays;
        totalFeeds = savedFeeds;
        lastInteractionTime = savedInteractionTime > 0 ? savedInteractionTime : System.currentTimeMillis();
    }

    /**
     * Limita un valor al rango -100 a 100.
     */
    private static float clamp(float value) {
        return Math.max(-100, Math.min(100, value));
    }

    // --- Getters para estadisticas ---

    public int getTotalPets() { return totalPets; }
    public int getTotalPlays() { return totalPlays; }
    public int getTotalFeeds() { return totalFeeds; }
    public long getLastInteractionTime() { return lastInteractionTime; }
}
