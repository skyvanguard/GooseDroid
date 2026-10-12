package com.skyvanguard.goozy.engine;

import com.skyvanguard.goozy.core.MathUtil;
import com.skyvanguard.goozy.core.Time;

/** Tareas del ganso y el estado de las que duran varios pasos. */
public final class GooseTasks {

    public enum SpeedTier { Walk, Run, Charge }

    public enum GooseTask {
        Wander,
        /** Sale por un borde y vuelve arrastrando una nota. */
        FetchNote,
        /** Se embarra y corre en zigzag dejando huellas. */
        MudDash,
        Sleeping,
        Eating,
        Playing,
        Sad,
        Happy,
        Seeking,
        BeingPetted,
        BeingDragged
    }

    /** Borde por el que sale el ganso a buscar una nota. */
    public enum Edge { LEFT, TOP, RIGHT }

    private GooseTasks() {
    }

    /** Paseo: tramos cortos hacia puntos al azar, con paradas a mirar alrededor. */
    static final class Stroll {
        static final float FIRST_SECONDS = 12f;
        static final float MIN_SECONDS = 18f;
        static final float MAX_SECONDS = 40f;
        static final float ARRIVED_DISTANCE = 20f;
        private static final float MIN_LEG_SECONDS = 1.2f;
        private static final float MAX_LEG_SECONDS = 4.5f;
        private static final float MIN_PAUSE_SECONDS = 0.8f;
        private static final float MAX_PAUSE_SECONDS = 2.4f;

        final double endsAt;
        /** Momento en que termina la parada actual; negativo = caminando. */
        double pauseUntil = -1;

        Stroll(boolean isFirst, float durationScale) {
            float seconds = isFirst ? FIRST_SECONDS : MathUtil.randomRange(MIN_SECONDS, MAX_SECONDS);
            endsAt = Time.time + seconds * durationScale;
        }

        static float legSeconds() {
            return MathUtil.randomRange(MIN_LEG_SECONDS, MAX_LEG_SECONDS);
        }

        static float pauseSeconds() {
            return MathUtil.randomRange(MIN_PAUSE_SECONDS, MAX_PAUSE_SECONDS);
        }
    }

    /** Buscar una nota: ir a un borde, desaparecer un rato y volver con ella. */
    static final class FetchNote {
        enum Step { TO_EDGE, AWAY, BRING_BACK }

        private static final float MIN_AWAY_SECONDS = 2f;
        private static final float MAX_AWAY_SECONDS = 4f;

        Step step = Step.TO_EDGE;
        Edge edge = Edge.LEFT;
        double awayUntil;

        static float awaySeconds() {
            return MathUtil.randomRange(MIN_AWAY_SECONDS, MAX_AWAY_SECONDS);
        }
    }

    /** Correr embarrado: salir disparado por un borde y volver en zigzag. */
    static final class MudDash {
        enum Step { RUN_OUT, ZIGZAG }

        static final float ZIGZAG_SECONDS = 2.5f;
        static final float TURN_SECONDS = 0.6f;
        static final float MUDDY_FEET_SECONDS = 15f;

        Step step = Step.RUN_OUT;
        double nextTurnAt;
        double endsAt;
    }
}
