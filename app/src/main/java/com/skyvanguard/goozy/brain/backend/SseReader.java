package com.skyvanguard.goozy.brain.backend;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Lector mínimo de Server-Sent Events: entrega el contenido de cada línea
 * "data:" y se detiene cuando el consumidor lo pide o el flujo termina.
 */
final class SseReader {
    private static final String DATA_PREFIX = "data:";
    /** Un evento de chat ocupa pocos KB: una línea más larga es un servidor roto u hostil. */
    static final int MAX_LINE_CHARS = 64 * 1024;

    interface DataHandler {
        /**
         * @return false para dejar de leer
         */
        boolean onData(String data) throws IOException;
    }

    private SseReader() {
    }

    static void read(InputStream stream, DataHandler handler) throws IOException {
        Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        String line;
        while ((line = readLine(reader)) != null) {
            if (!line.startsWith(DATA_PREFIX)) continue;
            String data = line.substring(DATA_PREFIX.length()).trim();
            if (data.isEmpty()) continue;
            if (!handler.onData(data)) return;
        }
    }

    /**
     * Como BufferedReader.readLine, pero con un tope de largo para no agotar la
     * memoria.
     *
     * @return la línea sin el salto, o null al final del flujo
     */
    static String readLine(Reader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = reader.read()) >= 0) {
            if (c == '\n') return stripCarriageReturn(line);
            if (line.length() >= MAX_LINE_CHARS) {
                throw new IOException("Línea de streaming demasiado larga");
            }
            line.append((char) c);
        }
        return line.length() > 0 ? stripCarriageReturn(line) : null;
    }

    private static String stripCarriageReturn(StringBuilder line) {
        int length = line.length();
        if (length > 0 && line.charAt(length - 1) == '\r') {
            line.setLength(length - 1);
        }
        return line.toString();
    }
}
