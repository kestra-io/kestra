package io.kestra.webserver.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPOutputStream;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.Encoder;

import io.micronaut.core.annotation.Nullable;

/**
 * Compression helpers for precompressing static UI resources held in memory.
 * <p>
 * Brotli is only active when the brotli4j native library loads for the current platform; otherwise
 * callers fall back to gzip only.
 */
public final class UiResourceCompression {
    // Both resolved once at class-loading time: the native library probe and the encoder settings.
    private static final boolean BROTLI_AVAILABLE = Brotli4jLoader.isAvailable();
    // Quality 5 keeps the one-time compression cost per resource low while still beating gzip on size.
    private static final Encoder.Parameters BROTLI_PARAMETERS = new Encoder.Parameters().setQuality(5);

    private UiResourceCompression() {
    }

    public static boolean isBrotliAvailable() {
        return BROTLI_AVAILABLE;
    }

    public static byte[] gzip(byte[] raw, int level) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 3));
             GZIPOutputStream gzip = new GZIPOutputStream(out) {
                 {
                     this.def.setLevel(level);
                 }
             }) {
            gzip.write(raw);
            gzip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Compresses with brotli, or returns {@code null} when the brotli native library is unavailable.
     */
    @Nullable
    public static byte[] brotli(byte[] raw) {
        if (!BROTLI_AVAILABLE) {
            return null;
        }
        try {
            return Encoder.compress(raw, BROTLI_PARAMETERS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
