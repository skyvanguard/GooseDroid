package com.skyvanguard.goozy.brain;

/**
 * Un motor capaz de generar texto: plantillas, un modelo en el teléfono, un
 * servidor en la red local o una API en la nube.
 *
 * Contrato: una sola generación a la vez por instancia.
 */
public interface LlmBackend {
    /** Identificador estable, usado en la configuración. */
    String getId();

    /** true si el contenido sale del teléfono. */
    boolean isRemote();

    /** true si puede generar ahora mismo (modelo presente, clave cargada...). */
    boolean isAvailable();

    /**
     * Genera de forma asíncrona. Nunca bloquea al llamador y nunca lanza: los
     * errores llegan por el callback.
     */
    void generate(LlmRequest request, LlmCallback callback);

    /** Cancela la generación en curso, si la hay. */
    void cancel();

    /** Libera memoria y conexiones. El backend puede volver a usarse después. */
    void release();
}
