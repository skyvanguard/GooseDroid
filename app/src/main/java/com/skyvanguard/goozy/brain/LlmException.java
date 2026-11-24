package com.skyvanguard.goozy.brain;

/**
 * Falla de un backend. El tipo le dice al orquestador si conviene reintentar.
 */
public class LlmException extends Exception {
    public enum Kind {
        /** El backend no está listo: falta modelo, clave o configuración. */
        UNAVAILABLE,
        /** Red caída, timeout o servidor inalcanzable. */
        NETWORK,
        /** Credenciales rechazadas. */
        AUTH,
        /** Límite de uso alcanzado. */
        RATE_LIMITED,
        /** El servidor o el modelo respondieron algo inutilizable. */
        BAD_RESPONSE,
        /** El modelo se negó a responder. */
        REFUSED,
        /** La generación fue cancelada por el llamador. */
        CANCELLED
    }

    private final Kind kind;

    public LlmException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public LlmException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isRetryable() {
        return kind == Kind.NETWORK || kind == Kind.RATE_LIMITED;
    }
}
