package io.kestra.core.storages;

import io.kestra.core.storages.kv.KVStore;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for accessing the Kestra's storage.
 */
public interface Storage {
    /**
     * Gets access to the namespace files for the contextual namespace.
     *
     * @return The {@link Namespace}.
     */
    Namespace namespace();

    /**
     * Gets access to the namespace files for the given namespace.
     *
     * @return The {@link Namespace}.
     */
    Namespace namespace(String namespace);

    /**
     * Checks whether the given URI points to an exiting file/object in the internal storage.
     *
     * @param uri the URI of the file/object in the internal storage.
     * @return {@code true} if the URI points to a file/object that exists in the internal storage.
     */
    boolean isFileExist(URI uri);

    /**
     * Retrieve an {@link InputStream} for the given file URI.
     *
     * @param uri the file URI.
     * @return the {@link InputStream}.
     * @throws IllegalArgumentException if the given {@link URI} is {@code null} or invalid.
     * @throws IOException              if an error happens while accessing the file.
     */
    InputStream getFile(URI uri) throws IOException;

    /**
     * Deletes all the files for the current execution.
     *
     * @return The URIs of the deleted files.
     * @throws IOException if an error happened while deleting files.
     */
    List<URI> deleteExecutionFiles() throws IOException;

    /**
     * Gets the storage base URI for the current context.
     *
     * @return the URI.
     */
    URI getContextBaseURI();

    /**
     * Stores a file with the given name for the given {@link InputStream} into Kestra's storage.
     *
     * @param inputStream the {@link InputStream} of the file content.
     * @param name        the name of the file on the Kestra's storage.
     * @return the URI of the file/object in the internal storage.
     * @throws IOException if an error occurs while storing the file.
     */
    URI putFile(InputStream inputStream, String name) throws IOException;

    /**
     * Stores a file with the given name for the given {@link InputStream} into Kestra's storage.
     *
     * @param inputStream the {@link InputStream} of the file content.
     * @param uri         the target URI of the file to be stored in the storage.
     * @return the URI of the file/object in the internal storage.
     * @throws IOException if an error occurs while storing the file.
     */
    URI putFile(InputStream inputStream, URI uri) throws IOException;

    /**
     * Stores a copy of the given file into Kestra's storage, and deletes the original file.
     *
     * @param file the file to be store.
     * @return the URI of the stored object.
     * @throws IOException if an error occurs while storing the file.
     */
    URI putFile(File file) throws IOException;

    /**
     * Stores a copy of the given file into Kestra's storage with the specified name, and deletes the original file.
     *
     * @param file the file to be store.
     * @param name the name of the file on the Kestra's storage.
     * @return the URI of the stored object.
     * @throws IOException if an error occurs while storing the file.
     */
    URI putFile(File file, String name) throws IOException;

    // ==============================================================
    //  STATE STORE
    // ==============================================================
    InputStream getTaskStateFile(String state, String name) throws IOException;

    InputStream getTaskStateFile(String state, String name, Boolean isNamespace, Boolean useTaskRun) throws IOException;

    URI putTaskStateFile(byte[] content, String state, String name) throws IOException;

    URI putTaskStateFile(byte[] content, String state, String name, Boolean namespace, Boolean useTaskRun) throws IOException;

    URI putTaskStateFile(File file, String state, String name) throws IOException;

    URI putTaskStateFile(File file, String state, String name, Boolean isNamespace, Boolean useTaskRun) throws IOException;

    boolean deleteTaskStateFile(String state, String name) throws IOException;

    boolean deleteTaskStateFile(String state, String name, Boolean isNamespace, Boolean useTaskRun) throws IOException;


    // ==============================================================
    // CACHING
    // ==============================================================

    /**
     * Gets the cache file from the Kestra's storage for the given cacheID and objectID.
     * If the cache file didn't exist, an empty Optional is returned.
     *
     * @param cacheId  the ID of the cache.
     * @param objectId the ID object cached object (optional).
     * @return an Optional with the cache input stream or empty.
     * @throws IOException if an error occurs during the operation.
     */
    default Optional<InputStream> getCacheFile(String cacheId, @Nullable String objectId) throws IOException {
        return getCacheFile(cacheId, objectId, null);
    }

    /**
     * Gets the cache file from the Kestra's storage for the given cacheID and objectID.
     * If the cache file didn't exist or has expired based on the given TTL, an empty Optional is returned.
     *
     * @param cacheId  the ID of the cache.
     * @param objectId the ID object cached object (optional).
     * @param ttl      the time-to-live duration of the cache.
     * @return an Optional with the cache input stream or empty.
     * @throws IOException if an error occurs during the operation.
     */
    Optional<InputStream> getCacheFile(String cacheId, @Nullable String objectId, @Nullable Duration ttl) throws IOException;

    /**
     * Caches the given file into Kestra's storage with the given cache ID.
     *
     * @param file     the cache as a ZIP archive
     * @param cacheId  the ID of the cache.
     * @param objectId the ID object cached object (optional).
     * @return the URI of the file inside the internal storage.
     * @throws IOException if an error occurs during the operation.
     */
    URI putCacheFile(File file, String cacheId, @Nullable String objectId) throws IOException;

    /**
     * Deletes the cache file.
     *
     * @param cacheId  the ID of the cache.
     * @param objectId the ID object cached object (optional).
     * @return {@code true} if the cache file was removed/. Otherwise {@code false}.
     * @throws IOException if an error occurs during the operation.
     */
    Optional<Boolean> deleteCacheFile(String cacheId, @Nullable String objectId) throws IOException;
}
