package io.kestra.core.storages;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The default {@link NamespaceFileBackend}: the content goes to the internal storage, under the storage path of each
 * revision.
 */
@Singleton
public class StorageNamespaceFileBackend implements NamespaceFileBackend {

    private final StorageInterface storage;

    @Inject
    public StorageNamespaceFileBackend(StorageInterface storage) {
        this.storage = storage;
    }

    @Override
    public InputStream get(String tenant, NamespaceFile file) throws IOException {
        return storage.get(tenant, file.namespace(), readableUri(tenant, file));
    }

    @Override
    public boolean exists(String tenant, NamespaceFile file) throws IOException {
        return storage.exists(tenant, file.namespace(), readableUri(tenant, file));
    }

    @Override
    public long put(String tenant, NamespaceFile file, InputStream content) throws IOException {
        try (content) {
            // Remove Windows letter
            URI uri = URI.create(file.storagePath().toUri().toString().replaceFirst("^file:///[a-zA-Z]:", ""));
            storage.put(tenant, file.namespace(), uri, content);
            return storage.getAttributes(tenant, file.namespace(), uri).getSize();
        }
    }

    @Override
    public void createDirectory(String tenant, NamespaceFile directory) throws IOException {
        storage.createDirectory(tenant, directory.namespace(), directory.storagePath().toUri());
    }

    @Override
    public boolean delete(String tenant, NamespaceFile file) throws IOException {
        return storage.delete(tenant, file.namespace(), file.storagePath().toUri());
    }

    private URI readableUri(String tenant, NamespaceFile file) throws IOException {
        URI uri = file.storagePath().toUri();
        Optional<Path> legacyStoragePath = file.legacyStoragePath();
        if (legacyStoragePath.isEmpty() || storage.exists(tenant, file.namespace(), uri)) {
            return uri;
        }
        return legacyStoragePath.get().toUri();
    }
}
