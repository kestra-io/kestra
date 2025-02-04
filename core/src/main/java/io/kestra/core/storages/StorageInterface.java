package io.kestra.core.storages;

import io.kestra.core.annotations.Retryable;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.Plugin;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

/**
 * @implNote Most methods (except lifecycle on) took a namespace as parameter, this namespace parameter MUST NOT BE USED to denote the path of the storage URI in any sort,
 *           the URI must never be modified by a storage implementation.
 *           This is only used by storage implementation that must enforce namespace isolation.
 */
public interface StorageInterface extends AutoCloseable, Plugin {

    /**
     * Opens any resources or perform any pre-checks for initializing this storage.
     *
     * @throws IOException if an error happens during initialization.
     */
    default void init() throws IOException {
        // no-op
    }

    /**
     * Closes any resources used by this class.
     */
    @Override
    default void close() {
        // no-op
    }

    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class})
    InputStream get(String tenantId, @Nullable String namespace, URI uri) throws IOException;

    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class})
    StorageObject getWithMetadata(String tenantId, @Nullable String namespace, URI uri) throws IOException;

    /**
     * Returns all objects that start with the given prefix
     *
     * @param includeDirectories whether to include directories in the given results or not. If true, directories' uri will have a trailing '/'
     * @return Kestra's internal storage uris of the found objects
     */
    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class})
    List<URI> allByPrefix(String tenantId, @Nullable String namespace, URI prefix, boolean includeDirectories) throws IOException;

    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class})
    List<FileAttributes> list(String tenantId, @Nullable String namespace, URI uri) throws IOException;

    /**
     * Whether the uri points to a file/object that exist in the internal storage.
     *
     * @param uri      the URI of the file/object in the internal storage.
     * @param tenantId the tenant identifier.
     * @return true if the uri points to a file/object that exist in the internal storage.
     */
    @SuppressWarnings("try")
    default boolean exists(String tenantId, @Nullable String namespace, URI uri) {
        try (InputStream ignored = get(tenantId, namespace, uri)) {
            return true;
        } catch (IOException ieo) {
            return false;
        }
    }

    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class})
    FileAttributes getAttributes(String tenantId, @Nullable String namespace, URI uri) throws IOException;

    @Retryable(includes = {IOException.class})
    default URI put(String tenantId, @Nullable String namespace, URI uri, InputStream data) throws IOException {
        return this.put(tenantId, namespace, uri, new StorageObject(null, data));
    }

    @Retryable(includes = {IOException.class})
    URI put(String tenantId, @Nullable String namespace, URI uri, StorageObject storageObject) throws IOException;

    @Retryable(includes = {IOException.class})
    boolean delete(String tenantId, @Nullable String namespace, URI uri) throws IOException;

    @Retryable(includes = {IOException.class})
    URI createDirectory(String tenantId, @Nullable String namespace, URI uri) throws IOException;

    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class})
    URI move(String tenantId, @Nullable String namespace, URI from, URI to) throws IOException;

    @Retryable(includes = {IOException.class})
    List<URI> deleteByPrefix(String tenantId, @Nullable String namespace, URI storagePrefix) throws IOException;

    @Retryable(includes = {IOException.class})
    default URI from(Execution execution, String input, File file) throws IOException {
        URI uri = StorageContext.forInput(execution, input, file.getName()).getContextStorageURI();
        return this.put(execution.getTenantId(), execution.getNamespace(), uri, new BufferedInputStream(new FileInputStream(file)));
    }

    /**
     * Throws an IllegalArgumentException if the URI is not absolute: a.k.a., if it contains <code>".." + File.separator</code>.
     */
    default void parentTraversalGuard(URI uri) {
        if (uri != null && (uri.toString().contains(".." + File.separator) || uri.toString().contains(File.separator + "..") || uri.toString().equals(".."))) {
            throw new IllegalArgumentException("File should be accessed with their full path and not using relative '..' path.");
        }
    }
}
