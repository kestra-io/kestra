package io.kestra.core.storages;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.util.List;

import jakarta.annotation.Nullable;

/**
 * Minimal {@link StorageInterface} test double: every method throws or returns an empty result. Tests
 * subclass this and override only the method(s) and capability interface(s) they exercise, instead of
 * restating every {@link StorageInterface} method for each new test double.
 */
abstract class NoopStorageInterface implements StorageInterface {
    @Override
    public InputStream get(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        throw new NoSuchFileException(uri.toString());
    }

    @Override
    public InputStream getInstanceResource(@Nullable String namespace, URI uri) throws IOException {
        throw new NoSuchFileException(uri.toString());
    }

    @Override
    public StorageObject getWithMetadata(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        throw new NoSuchFileException(uri.toString());
    }

    @Override
    public List<URI> allByPrefix(String tenantId, @Nullable String namespace, URI prefix, boolean includeDirectories) {
        return List.of();
    }

    @Override
    public List<FileAttributes> list(String tenantId, @Nullable String namespace, URI uri) {
        return List.of();
    }

    @Override
    public List<FileAttributes> listInstanceResource(@Nullable String namespace, URI uri) {
        return List.of();
    }

    @Override
    public FileAttributes getAttributes(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        throw new NoSuchFileException(uri.toString());
    }

    @Override
    public FileAttributes getInstanceAttributes(@Nullable String namespace, URI uri) throws IOException {
        throw new NoSuchFileException(uri.toString());
    }

    @Override
    public URI put(String tenantId, @Nullable String namespace, URI uri, StorageObject storageObject) {
        return uri;
    }

    @Override
    public URI putInstanceResource(@Nullable String namespace, URI uri, StorageObject storageObject) {
        return uri;
    }

    @Override
    public boolean delete(String tenantId, @Nullable String namespace, URI uri) {
        return false;
    }

    @Override
    public boolean deleteInstanceResource(@Nullable String namespace, URI uri) {
        return false;
    }

    @Override
    public URI createDirectory(String tenantId, @Nullable String namespace, URI uri) {
        return uri;
    }

    @Override
    public URI createInstanceDirectory(String namespace, URI uri) {
        return uri;
    }

    @Override
    public URI move(String tenantId, @Nullable String namespace, URI from, URI to) {
        return to;
    }

    @Override
    public List<URI> deleteByPrefix(String tenantId, @Nullable String namespace, URI storagePrefix) {
        return List.of();
    }
}
