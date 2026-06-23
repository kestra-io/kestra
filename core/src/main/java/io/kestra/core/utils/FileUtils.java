package io.kestra.core.utils;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Utility methods for manipulating files.
 */
public final class FileUtils {

    /**
     * Default number of attempts made by {@link #deleteWithRetry(Path)} before giving up.
     */
    static final int DEFAULT_DELETE_MAX_ATTEMPTS = 5;

    /**
     * Default delay between two deletion attempts in {@link #deleteWithRetry(Path)}.
     */
    static final Duration DEFAULT_DELETE_RETRY_DELAY = Duration.ofMillis(50);

    /**
     * Retry settings for {@link #deleteWithRetry(Path)}. These are mutable so they can be tuned from
     * Micronaut configuration at startup (see {@code TempFileDeletionConfigurer}); they default to
     * sensible values for callers that run without the application context (e.g. tests).
     */
    private static volatile int deleteMaxAttempts = DEFAULT_DELETE_MAX_ATTEMPTS;
    private static volatile Duration deleteRetryDelay = DEFAULT_DELETE_RETRY_DELAY;

    /**
     * Overrides the retry settings used by {@link #deleteWithRetry(Path)}.
     * <p>
     * Intended to be called once at startup from the configuration layer. Falls back to the defaults
     * for non-positive attempts or a {@code null} delay.
     *
     * @param maxAttempts the number of deletion attempts before giving up.
     * @param retryDelay  the delay between two attempts.
     */
    public static void configureDeletionRetry(final int maxAttempts, final Duration retryDelay) {
        deleteMaxAttempts = maxAttempts > 0 ? maxAttempts : DEFAULT_DELETE_MAX_ATTEMPTS;
        deleteRetryDelay = retryDelay != null ? retryDelay : DEFAULT_DELETE_RETRY_DELAY;
    }

    /**
     * Get the file extension prefixed the '.' from the given file URI.
     *
     * @param file the name or path of the file.
     * @return the file extension prefixed with the '.' or {@code null}.
     */
    public static String getExtension(final URI file) {
        return file == null ? null : getExtension(file.toString());
    }

    /**
     * Get the file extension prefixed the '.' from the given file name or file path.
     *
     * @param file the name or path of the file.
     * @return the file extension prefixed with the '.' or {@code null}.
     */
    public static String getExtension(final String file) {
        if (file == null) return null;
        String extension = FilenameUtils.getExtension(file);
        return StringUtils.isEmpty(extension) ? null : "." + extension;
    }

    /**
     * Creates a new {@link URI} from the given string path.
     *
     * @param path the string path - may be {@code null}.
     * @return an optional URI, or {@link Optional#empty()} if the given path represent an invalid URI.
     */
    public static Optional<URI> getURI(final String path) {
        if (path == null) return Optional.empty();
        try {
            return Optional.of(URI.create(path));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Extracts the file name from the given URI.
     *
     * @param uri the file URI.
     * @return the string file name.
     */
    public static String getFileName(final URI uri) {
        String path = uri.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * Best-effort deletion of a file, tolerant of transient file locks.
     * <p>
     * On Windows a file that has just been closed can stay briefly locked — the OS releases the
     * handle asynchronously and antivirus/indexing may hold it open for a few milliseconds — which
     * makes {@link Files#delete(Path)} fail with an {@code AccessDeniedException}. Retrying after a
     * short pause clears this in virtually all cases. On Unix-like systems the first attempt
     * succeeds and this method returns immediately.
     *
     * <p>
     * The number of attempts and the delay between them are tunable via
     * {@link #configureDeletionRetry(int, Duration)}.
     *
     * @param path the file to delete.
     * @return the last {@link IOException} if the file could still not be deleted after all
     *         attempts, otherwise {@link Optional#empty()}.
     */
    public static Optional<IOException> deleteWithRetry(final Path path) {
        int attempts = Math.max(1, deleteMaxAttempts);
        long delayMillis = Math.max(0, deleteRetryDelay.toMillis());
        IOException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                Files.deleteIfExists(path);
                return Optional.empty();
            } catch (IOException e) {
                last = e;
                if (attempt < attempts && delayMillis > 0) {
                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return Optional.ofNullable(last);
    }
}
