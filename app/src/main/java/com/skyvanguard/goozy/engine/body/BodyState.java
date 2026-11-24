package com.skyvanguard.goozy.engine.body;

/** Postura y cara que el juego le pide al cuerpo. */
public final class BodyState {
    public enum Pose { NORMAL, ALERT, RELAXED, SLEEPING, EATING, EXCITED, SCARED }

    /** HAPPY: ojos abiertos y rubor. JOY: ojos cerrados de felicidad (^^). */
    public enum Expression { NEUTRAL, HAPPY, JOY, SAD, SLEEPY, SURPRISED, ANGRY, LOVE }

    /** Zonas del cuerpo que distingue el tacto. */
    public enum Zone { NONE, HEAD, BEAK, BODY, BELLY, WING_LEFT, WING_RIGHT, FEET, TAIL }

    private BodyState() {
    }
}
