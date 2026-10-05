package io.kestra.core.plugins.endpoint;

import io.kestra.core.storages.FileAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

/**
 * Internal-storage access handed to a plugin endpoint, confined by construction to the storage of
 * a single execution. Every method rejects a URI that resolves outside that execution's storage
 * prefix, so a plugin holding this handle cannot reach another execution's, flow's or tenant's files.
 */
public interface ScopedStorage {
    InputStream getFile(URI uri) throws IOException;

    List<FileAttributes> list(URI uri) throws IOException;

    boolean exists(URI uri);

    URI putFile(URI uri, InputStream data) throws IOException;

    boolean deleteFile(URI uri) throws IOException;
}
