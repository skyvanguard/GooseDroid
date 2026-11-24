package com.skyvanguard.goozy.brain;

/**
 * Resultado de una generación. Los métodos se invocan en el hilo del backend;
 * quien necesite el hilo principal debe despachar por su cuenta.
 */
public interface LlmCallback {
    /** Fragmento parcial, para backends con streaming. */
    default void onToken(String token) {
    }

    /** Texto completo. Se llama exactamente una vez si no hubo error. */
    void onDone(String fullText);

    /** Se llama exactamente una vez si la generación falló o fue cancelada. */
    void onError(LlmException error);
}
