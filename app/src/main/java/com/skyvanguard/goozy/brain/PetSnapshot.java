package com.skyvanguard.goozy.brain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Foto del estado de la mascota en el momento de pensar. Inmutable: el modelo
 * corre en otro hilo y no debe leer estado vivo del juego.
 */
public final class PetSnapshot {
    public final String petName;
    /** 0 = saciado, 100 = hambriento. */
    public final float hunger;
    /** 0 = agotado, 100 = descansado. */
    public final float energy;
    /** 0 = triste, 100 = feliz. */
    public final float happiness;
    /** 0 = sucio, 100 = limpio. */
    public final float hygiene;
    /** 0 = muy enfermo, 100 = sano. */
    public final float health;

    /** Rasgos de personalidad, de -100 a 100. */
    public final float playfulness;
    public final float affection;
    public final float bravery;
    public final float mischief;

    public final String stageName;
    public final long ageMinutes;
    public final String currentActivity;

    public final int hourOfDay;
    public final String dayOfWeek;
    /** 0 a 100, o -1 si no se conoce. */
    public final int batteryPercent;
    public final boolean isCharging;

    public final List<String> recentEvents;

    private PetSnapshot(Builder builder) {
        this.petName = builder.petName;
        this.hunger = builder.hunger;
        this.energy = builder.energy;
        this.happiness = builder.happiness;
        this.hygiene = builder.hygiene;
        this.health = builder.health;
        this.playfulness = builder.playfulness;
        this.affection = builder.affection;
        this.bravery = builder.bravery;
        this.mischief = builder.mischief;
        this.stageName = builder.stageName;
        this.ageMinutes = builder.ageMinutes;
        this.currentActivity = builder.currentActivity;
        this.hourOfDay = builder.hourOfDay;
        this.dayOfWeek = builder.dayOfWeek;
        this.batteryPercent = builder.batteryPercent;
        this.isCharging = builder.isCharging;
        this.recentEvents = Collections.unmodifiableList(new ArrayList<>(builder.recentEvents));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private static final int UNKNOWN_BATTERY = -1;
        private static final int DEFAULT_HOUR = 12;

        private String petName = "Goozy";
        private float hunger = 50f;
        private float energy = 100f;
        private float happiness = 75f;
        private float hygiene = 100f;
        private float health = 100f;
        private float playfulness = 0f;
        private float affection = 0f;
        private float bravery = 0f;
        private float mischief = 50f;
        private String stageName = "";
        private long ageMinutes = 0L;
        private String currentActivity = "";
        private int hourOfDay = DEFAULT_HOUR;
        private String dayOfWeek = "";
        private int batteryPercent = UNKNOWN_BATTERY;
        private boolean isCharging = false;
        private List<String> recentEvents = new ArrayList<>();

        public Builder petName(String value) {
            if (value != null && !value.trim().isEmpty()) this.petName = value.trim();
            return this;
        }

        public Builder needs(float hunger, float energy, float happiness) {
            this.hunger = hunger;
            this.energy = energy;
            this.happiness = happiness;
            return this;
        }

        public Builder care(float hygiene, float health) {
            this.hygiene = hygiene;
            this.health = health;
            return this;
        }

        public Builder personality(float playfulness, float affection, float bravery,
                                   float mischief) {
            this.playfulness = playfulness;
            this.affection = affection;
            this.bravery = bravery;
            this.mischief = mischief;
            return this;
        }

        public Builder stage(String stageName, long ageMinutes) {
            this.stageName = stageName != null ? stageName : "";
            this.ageMinutes = Math.max(0L, ageMinutes);
            return this;
        }

        public Builder currentActivity(String value) {
            this.currentActivity = value != null ? value : "";
            return this;
        }

        public Builder clock(int hourOfDay, String dayOfWeek) {
            this.hourOfDay = hourOfDay;
            this.dayOfWeek = dayOfWeek != null ? dayOfWeek : "";
            return this;
        }

        public Builder battery(int percent, boolean isCharging) {
            this.batteryPercent = percent;
            this.isCharging = isCharging;
            return this;
        }

        public Builder recentEvents(List<String> events) {
            this.recentEvents = events != null ? new ArrayList<>(events) : new ArrayList<>();
            return this;
        }

        public PetSnapshot build() {
            return new PetSnapshot(this);
        }
    }
}
