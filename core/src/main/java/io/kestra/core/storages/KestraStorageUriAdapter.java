package io.kestra.core.storages;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;

import io.kestra.core.utils.FileUtils;

import jakarta.annotation.Nullable;

/**
 * Shows storage plugins the legacy {@code kestra:///} form they resolve with {@link URI#getPath()},
 * and returns the canonical {@code kestra://} form to callers.
 */
final class KestraStorageUriAdapter implements StorageInterface {

    private final StorageInterface delegate;

    KestraStorageUriAdapter(StorageInterface delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getType() {
        return delegate.getType();
    }

    @Override
    public void init() throws IOException {
        delegate.init();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public InputStream get(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        return delegate.get(tenantId, namespace, legacy(uri));
    }

    @Override
    public InputStream getInstanceResource(@Nullable String namespace, URI uri) throws IOException {
        return delegate.getInstanceResource(namespace, legacy(uri));
    }

    @Override
    public StorageObject getWithMetadata(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        return delegate.getWithMetadata(tenantId, namespace, legacy(uri));
    }

    @Override
    public List<URI> allByPrefix(String tenantId, @Nullable String namespace, URI prefix, boolean includeDirectories) throws IOException {
        return userUris(delegate.allByPrefix(tenantId, namespace, legacy(prefix), includeDirectories));
    }

    @Override
    public List<FileAttributes> list(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        return delegate.list(tenantId, namespace, legacy(uri));
    }

    @Override
    public List<FileAttributes> listInstanceResource(@Nullable String namespace, URI uri) throws IOException {
        return delegate.listInstanceResource(namespace, legacy(uri));
    }

    @Override
    public FileAttributes getAttributes(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        return delegate.getAttributes(tenantId, namespace, legacy(uri));
    }

    @Override
    public FileAttributes getInstanceAttributes(@Nullable String namespace, URI uri) throws IOException {
        return delegate.getInstanceAttributes(namespace, legacy(uri));
    }

    @Override
    public boolean exists(String tenantId, @Nullable String namespace, URI uri) {
        return delegate.exists(tenantId, namespace, legacy(uri));
    }

    @Override
    public boolean existsInstanceResource(@Nullable String namespace, URI uri) {
        return delegate.existsInstanceResource(namespace, legacy(uri));
    }

    @Override
    public URI put(String tenantId, @Nullable String namespace, URI uri, StorageObject storageObject) throws IOException {
        return StorageContext.toKestraUri(delegate.put(tenantId, namespace, legacy(uri), storageObject));
    }

    @Override
    public URI putInstanceResource(@Nullable String namespace, URI uri, StorageObject storageObject) throws IOException {
        return StorageContext.toKestraUri(delegate.putInstanceResource(namespace, legacy(uri), storageObject));
    }

    @Override
    public boolean delete(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        return delegate.delete(tenantId, namespace, legacy(uri));
    }

    @Override
    public boolean deleteInstanceResource(@Nullable String namespace, URI uri) throws IOException {
        return delegate.deleteInstanceResource(namespace, legacy(uri));
    }

    @Override
    public URI createDirectory(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        return StorageContext.toKestraUri(delegate.createDirectory(tenantId, namespace, legacy(uri)));
    }

    @Override
    public URI createInstanceDirectory(String namespace, URI uri) throws IOException {
        return StorageContext.toKestraUri(delegate.createInstanceDirectory(namespace, legacy(uri)));
    }

    @Override
    public URI move(String tenantId, @Nullable String namespace, URI from, URI to) throws IOException {
        return StorageContext.toKestraUri(
            delegate.move(
                tenantId,
                namespace,
                legacy(from),
                legacy(to)
            )
        );
    }

    @Override
    public List<URI> deleteByPrefix(String tenantId, @Nullable String namespace, URI storagePrefix) throws IOException {
        return userUris(delegate.deleteByPrefix(tenantId, namespace, legacy(storagePrefix)));
    }

    @Override
    public List<URI> purgeByLastModified(
        String tenantId,
        @Nullable String namespace,
        URI prefix,
        @Nullable Instant startDate,
        @Nullable Instant endDate,
        boolean dryRun) throws IOException {
        return userUris(delegate.purgeByLastModified(
            tenantId,
            namespace,
            legacy(prefix),
            startDate,
            endDate,
            dryRun
        ));
    }

    @Override
    public String getPath(URI uri) {
        return delegate.getPath(legacy(uri));
    }

    @Override
    public String getPath(String tenantId, URI uri) {
        return delegate.getPath(tenantId, legacy(uri));
    }

    private static URI legacy(URI uri) {
        if (FileUtils.isParentTraversal(uri)) {
            throw new IllegalArgumentException("File should be accessed with their full path and not using relative '..' path.");
        }
        return StorageContext.legacyKestraUri(uri);
    }

    private static List<URI> userUris(List<URI> uris) {
        if (uris == null) {
            return List.of();
        }
        return uris.stream().map(StorageContext::toKestraUri).toList();
    }
}
