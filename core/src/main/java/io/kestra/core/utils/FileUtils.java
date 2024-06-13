package io.kestra.core.utils;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;

public class FileUtils {

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
}
