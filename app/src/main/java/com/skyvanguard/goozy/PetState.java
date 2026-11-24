package com.skyvanguard.goozy;

/**
 * Contenedor que agrupa las instancias de estado de la mascota.
 * Permite inyección de dependencias y testeo sin estado global estático.
 */
public class PetState {
    private static PetState instance;

    public final PetNeeds needs;
    public final PetPersonality personality;
    public final PetAppearance appearance;

    public PetState() {
        this.needs = new PetNeeds();
        this.personality = new PetPersonality();
        this.appearance = new PetAppearance();
    }

    public static PetState getInstance() {
        if (instance == null) {
            instance = new PetState();
        }
        return instance;
    }

    /**
     * Permite inyectar un PetState personalizado (para testing).
     */
    public static void setInstance(PetState state) {
        instance = state;
    }
}
