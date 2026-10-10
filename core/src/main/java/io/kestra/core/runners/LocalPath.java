package io.kestra.core.runners;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Get access to local paths of the host machine.
 * <p>
 * All methods of this class check allowed paths and protect against path traversal.
 * All paths must be allowed via the {@link #ALLOWED_PATHS_CONFIG} configuration property or via plugin configuration.
 */
public interface LocalPath {
    String FILE_SCHEME = "file";
    String FILE_PROTOCOL = FILE_SCHEME + "://";

    String LOCAL_FILES_CONFIG = "kestra.localFiles";
    String ALLOWED_PATHS_CONFIG = LocalPath.LOCAL_FILES_CONFIG + ".allowedPaths";
    String ENABLE_FILE_FUNCTIONS_CONFIG = LocalPath.LOCAL_FILES_CONFIG + ".enableFileFunctions";
    String ENABLE_PREVIEW_CONFIG = LocalPath.LOCAL_FILES_CONFIG + ".enablePreview";

    /**
     * Get an InputStream of a local file denoted by this URI.
     *
     * @param uri a file URI
     * @throws SecurityException if the file is not allowed globally or specifically for this plugin.
     */
    InputStream get(URI uri) throws IOException;

    /**
     * Return true if the local file denoted by this URI exists.
     *
     * @param uri a file URI
     * @throws SecurityException if the file is not allowed globally or specifically for this plugin.
     */
    boolean exists(URI uri) throws IOException;

    /**
     * Get a local file attributes.
     *
     * @param uri a file URI
     * @throws SecurityException if the file is not allowed globally or specifically for this plugin.
     */
    BasicFileAttributes getAttributes(URI uri) throws IOException;
}
