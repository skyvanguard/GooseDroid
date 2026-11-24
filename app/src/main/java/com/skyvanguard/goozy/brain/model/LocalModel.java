package com.skyvanguard.goozy.brain.model;

/**
 * Un modelo que puede descargarse al teléfono. Inmutable.
 */
public final class LocalModel {
    public final String id;
    public final String title;
    public final String summary;
    public final String license;
    public final String fileName;
    public final String downloadUrl;
    public final long sizeBytes;

    LocalModel(String id, String title, String summary, String license, String fileName,
               String downloadUrl, long sizeBytes) {
        this.id = id;
        this.title = title;
        this.summary = summary;
        this.license = license;
        this.fileName = fileName;
        this.downloadUrl = downloadUrl;
        this.sizeBytes = sizeBytes;
    }

    public long getSizeMegabytes() {
        return sizeBytes / (1024L * 1024L);
    }
}
