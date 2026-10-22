package io.kestra.core.plugins.endpoint;

import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

public class ScopedExecutionStorage implements ScopedStorage {
    private final StorageInterface storage;
    private final String tenantId;
    private final String namespace;
    private final String prefixPath;

    public ScopedExecutionStorage(StorageInterface storage, String tenantId, String namespace, String flowId, String executionId) {
        this.storage = storage;
        this.tenantId = tenantId;
        this.namespace = namespace;
        URI prefix = StorageContext.forExecution(tenantId, namespace, flowId, executionId).getExecutionStorageURI();
        this.prefixPath = normalize(prefix);
    }

    private static String normalize(URI uri) {
        String path = uri.getPath();
        return path == null ? "" : path.replaceFirst("^/", "");
    }

    private URI scoped(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("A storage URI is required.");
        }
        if (!StorageContext.KESTRA_SCHEME.equals(uri.getScheme())) {
            throw new IllegalArgumentException("The storage URI '%s' must use the '%s' scheme.".formatted(uri, StorageContext.KESTRA_SCHEME));
        }
        if (FileUtils.isParentTraversal(uri)) {
            throw new IllegalArgumentException("The storage URI '%s' must not use a relative '..' path.".formatted(uri));
        }
        String path = normalize(uri);
        if (!path.equals(prefixPath) && !path.startsWith(prefixPath + "/")) {
            throw new IllegalArgumentException("The storage URI '%s' is outside the execution storage scope.".formatted(uri));
        }
        return uri;
    }

    @Override
    public InputStream getFile(URI uri) throws IOException {
        return storage.get(tenantId, namespace, scoped(uri));
    }

    @Override
    public List<FileAttributes> list(URI uri) throws IOException {
        return storage.list(tenantId, namespace, scoped(uri));
    }

    @Override
    public boolean exists(URI uri) {
        return storage.exists(tenantId, namespace, scoped(uri));
    }

    @Override
    public URI putFile(URI uri, InputStream data) throws IOException {
        return storage.put(tenantId, namespace, scoped(uri), data);
    }

    @Override
    public boolean deleteFile(URI uri) throws IOException {
        return storage.delete(tenantId, namespace, scoped(uri));
    }
}
