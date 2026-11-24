package com.skyvanguard.goozy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Escritura de archivos que nunca deja el destino a medio escribir: se escribe
 * en un temporal y después se reemplaza el original.
 */
public final class AtomicFiles {
    private static final String TEMP_SUFFIX = ".tmp";

    private AtomicFiles() {
    }

    public static void write(File target, byte[] content) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("No se pudo crear " + parent);
        }

        File temp = new File(target.getPath() + TEMP_SUFFIX);
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(content);
            out.flush();
            out.getFD().sync();
        } catch (IOException e) {
            temp.delete();
            throw e;
        }
        replace(temp, target);
    }

    /**
     * En Android el rename pisa el destino de forma atómica. Hay sistemas de
     * archivos donde no pisa: ahí se borra el destino y se reintenta.
     */
    private static void replace(File temp, File target) throws IOException {
        if (temp.renameTo(target)) return;
        if (target.exists() && target.delete() && temp.renameTo(target)) return;
        temp.delete();
        throw new IOException("No se pudo reemplazar " + target);
    }
}
