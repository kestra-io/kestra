package io.kestra.core.utils;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.Optional;

/**
 * Utility methods for manipulating files.
 */
public final class FileUtils {

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
        if (file == null)
            return null;
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
        if (path == null)
            return Optional.empty();
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
     * Check if the provided URI contains a relative parent path traversal segment (i.e., "..").
     *
     * @param uri the URI to validate
     * @return true if there is a relative parent path traversal
     */
    public static boolean isParentTraversal(URI uri) {
        return uri != null && isParentTraversal(uri.getPath());
    }

    /**
     * Check if the provided path contains a relative parent path traversal segment (i.e., "..").
     * <p>
     * Both {@code /} and {@code \} are treated as path separators so that Windows-style backslash
     * payloads (e.g. {@code "..\..\"}) cannot bypass the check. Only forward slashes were previously
     * matched, allowing backslash payloads to slip through on Linux/containers before being
     * canonicalized (GHSA-qw4v-6w32-xx9h).
     * <p>
     * The {@code path} argument must already be percent-decoded. {@link URI#getPath()} performs this
     * decoding automatically, so callers that obtain the path via a URI do not need to decode it
     * manually. Callers that pass a raw URL string should decode it first.
     *
     * @param path the path to validate (must be percent-decoded)
     * @return true if there is a relative parent path traversal
     */
    public static boolean isParentTraversal(String path) {
        if (path == null) {
            return false;
        }
        // Normalize both separators to '/' so the check is platform- and payload-agnostic.
        String normalized = path.replace('\\', '/');
        return normalized.equals("..")
            || normalized.startsWith("../")
            || normalized.endsWith("/..")
            || normalized.contains("/../");
    }
}
