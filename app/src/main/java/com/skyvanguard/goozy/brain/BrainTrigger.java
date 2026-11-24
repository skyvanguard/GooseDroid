package com.skyvanguard.goozy.brain;

/**
 * Por qué el ganso está pensando ahora. Inmutable.
 */
public final class BrainTrigger {
    public enum Kind {
        /** Pensamiento espontáneo, sin que pase nada en particular. */
        IDLE_THOUGHT(false),
        /** El humano lo acarició. */
        PETTED(true),
        /** El humano le dio de comer. */
        FED(true),
        /** El humano jugó con él. */
        PLAYED(true),
        /** El humano lo bañó. */
        CLEANED(true),
        /** El humano le dio un remedio. */
        HEALED(true),
        /** El humano lo agarró y lo arrastró. */
        DRAGGED(true),
        /** Una necesidad llegó a un nivel crítico. */
        NEED_CRITICAL(false),
        /** El humano le escribió. El detalle es el mensaje. */
        CHAT(true),
        /** Pasó algo en el teléfono. El detalle dice qué. */
        PHONE_EVENT(false),
        /** Fin del día: escribir el diario. */
        DIARY(false),
        /** Empezó a dormir: contar un sueño. */
        DREAM(false),
        /** El humano volvió después de un rato. El detalle dice cuánto. */
        GREETING(false),
        /** El humano está probando la configuración del cerebro. */
        TEST(true),
        /** El ganso le trae una nota al humano: escribir qué dice. */
        NOTE(false);

        private final boolean isUserInitiated;

        Kind(boolean isUserInitiated) {
            this.isUserInitiated = isUserInitiated;
        }

        /** Las reacciones al humano tienen prioridad sobre el límite de frecuencia. */
        public boolean isUserInitiated() {
            return isUserInitiated;
        }
    }

    public final Kind kind;
    public final String detail;

    public BrainTrigger(Kind kind, String detail) {
        this.kind = kind;
        this.detail = detail != null ? detail.trim() : "";
    }

    public static BrainTrigger of(Kind kind) {
        return new BrainTrigger(kind, "");
    }
}
