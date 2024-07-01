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
}
