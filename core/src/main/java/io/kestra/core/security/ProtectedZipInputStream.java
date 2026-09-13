package io.kestra.core.security;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.kestra.core.security.SecurityConfiguration.ZipBombProtectionConfiguration;

/**
 * A {@link ZipInputStream} that guards against ZIP-bomb attacks by enforcing a maximum
 * number of entries and a maximum uncompressed size per entry.
 * <p>
 * Both limits are checked <em>during</em> decompression rather than after an entry has
 * been fully buffered into memory: the entry count is checked as soon as an entry is
 * opened, and the per-entry size is checked on every {@code read()} call, so an oversized
 * entry fails before it is fully read (e.g. via {@code readAllBytes()}) rather than after.
 */
public class ProtectedZipInputStream extends ZipInputStream {

    private final int maxEntries;
    private final long maxEntrySize;
    private int entryCount = 0;
    private long currentEntryBytes = 0;

    /**
     * Returns a {@link ZipInputStream} enforcing the given ZIP-bomb protection
     * configuration, or a plain {@link ZipInputStream} when the configuration is
     * {@code null} or disabled.
     *
     * @param inputStream the underlying stream to read the ZIP archive from.
     * @param config the ZIP-bomb protection configuration, may be {@code null}.
     * @return a protected or plain {@link ZipInputStream} depending on the configuration.
     */
    public static ZipInputStream of(final InputStream inputStream, final ZipBombProtectionConfiguration config) {
        if (config == null || !Boolean.TRUE.equals(config.enabled())) {
            return new ZipInputStream(inputStream);
        }
        return new ProtectedZipInputStream(inputStream, config.maxNumberOfEntries(), config.maxEntrySize());
    }

    private ProtectedZipInputStream(final InputStream inputStream, final int maxEntries, final int maxEntrySize) {
        super(inputStream);
        this.maxEntries = maxEntries;
        this.maxEntrySize = maxEntrySize;
    }

    /**
     * {@inheritDoc}
     *
     * @throws ZipBombDetectedException if opening this entry would exceed the configured
     *         maximum number of entries.
     */
    @Override
    public ZipEntry getNextEntry() throws IOException {
        ZipEntry entry = super.getNextEntry();
        if (entry != null) {
            entryCount++;
            if (entryCount > maxEntries) {
                throw ZipBombDetectedException.tooManyEntries(maxEntries);
            }
            currentEntryBytes = 0;
        }
        return entry;
    }

    /**
     * {@inheritDoc}
     *
     * @throws ZipBombDetectedException if the current entry's uncompressed size exceeds
     *         the configured maximum entry size.
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) {
            currentEntryBytes += n;
            if (currentEntryBytes > maxEntrySize) {
                throw ZipBombDetectedException.entryTooLarge(maxEntrySize);
            }
        }
        return n;
    }

    /**
     * Exception thrown when a ZIP archive violates the configured ZIP-bomb protection
     * limits. Extends {@link IllegalArgumentException} so it is handled the same way as
     * other invalid-user-input errors (e.g. mapped to an HTTP 422 response by the webserver).
     */
    public static final class ZipBombDetectedException extends IllegalArgumentException {
        private ZipBombDetectedException(String message) {
            super(message);
        }

        private static ZipBombDetectedException tooManyEntries(int maxEntries) {
            return new ZipBombDetectedException(
                "Cannot decompress the archive because it contains more than %d entries. This limit is enforced by ZIP-bomb protection (kestra.security.zip-bomb-protection.max-number-of-entries)."
                    .formatted(maxEntries)
            );
        }

        private static ZipBombDetectedException entryTooLarge(long maxEntrySize) {
            return new ZipBombDetectedException(
                "Cannot decompress this archive entry because its uncompressed size exceeds %d bytes. This limit is enforced by ZIP-bomb protection (kestra.security.zip-bomb-protection.max-entry-size)."
                    .formatted(maxEntrySize)
            );
        }
    }
}
