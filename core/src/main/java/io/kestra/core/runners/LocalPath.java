package io.kestra.core.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * Get access to local paths of the host machine.
 * <p>
 * All methods of this class check allowed paths and protect against path traversal.
 * All paths must be allowed via the `kestra.plugins.allowed-paths` configuration property or via plugin configuration.
 */
public interface LocalPath {
    String FILE_SCHEME = "file";

    /**
     * Get an InputStream of a local file denoted by this URI.
     *
     * @param uri a file URI
     * @throws SecurityException if the file is not allowed globally or specifically for this plugin.
     */
    InputStream get(URI uri) throws IOException;
}
