package io.kestra.webserver.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import io.micronaut.core.annotation.Nullable;

/**
 * Compression helpers for precompressing static UI resources held in memory.
 * <p>
 * Brotli support is opportunistic: it is only active when the optional {@code brotli4j} library (and its
 * platform natives) are present on the runtime classpath; otherwise callers fall back to gzip.
 */
public final class UiResourceCompression {
    private static final boolean BROTLI_AVAILABLE = detectBrotli();

    private UiResourceCompression() {
    }

    public static boolean isBrotliAvailable() {
        return BROTLI_AVAILABLE;
    }

    public static byte[] gzip(byte[] raw, int level) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 3));
        try (GZIPOutputStream gzip = new GZIPOutputStream(out) {
            {
                this.def.setLevel(level);
            }
        }) {
            gzip.write(raw);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * Compresses with brotli, or returns {@code null} when brotli4j is not on the classpath.
     */
    @Nullable
    public static byte[] brotli(byte[] raw) {
        if (!BROTLI_AVAILABLE) {
            return null;
        }
        return brotliCompress(raw);
    }

    // Isolated so the brotli4j classes are only resolved once availability has been confirmed.
    private static byte[] brotliCompress(byte[] raw) {
        try {
            return com.aayushatharva.brotli4j.encoder.Encoder.compress(
                raw,
                // Quality 5 keeps the one-time compression cost per resource low while still beating gzip on size.
                new com.aayushatharva.brotli4j.encoder.Encoder.Parameters().setQuality(5)
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean detectBrotli() {
        try {
            return com.aayushatharva.brotli4j.Brotli4jLoader.isAvailable();
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }
}
