package com.terraformers.modernization.storage;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "terraformers.storage")
public class StorageRuntimeProperties {
    private String readerProvider = "disabled";
    private String writerProvider = "metadata-only";

    public String getReaderProvider() { return readerProvider; }
    public void setReaderProvider(String readerProvider) { this.readerProvider = readerProvider; }
    public String getWriterProvider() { return writerProvider; }
    public void setWriterProvider(String writerProvider) { this.writerProvider = writerProvider; }
    public String resolvedReaderProvider() { return normalize(readerProvider, "reader-provider", "disabled", "s3"); }
    public String resolvedWriterProvider() { return normalize(writerProvider, "writer-provider", "metadata-only", "s3"); }

    private String normalize(String value, String property, String... supported) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        for (String candidate : supported) if (candidate.equals(normalized)) return normalized;
        throw new IllegalStateException("Unsupported terraformers.storage." + property + ": " + value);
    }
}
