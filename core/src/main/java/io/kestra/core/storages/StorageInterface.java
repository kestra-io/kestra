package io.kestra.core.storages;

import io.kestra.core.annotations.Retryable;
import io.kestra.core.models.Plugin;
import io.kestra.core.models.executions.Execution;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.util.List;

/**
 * Interface for internal Kestra storage implementations. It handles file-like operations
 * for storing and retrieving data in a tenant- and namespace-aware way.
 *
 * @implNote Most methods (except lifecycle ones) take a namespace as a parameter.
 *           This namespace parameter MUST NOT be used to denote the path of the storage URI in any form.
 *           The URI must never be modified by a storage implementation. It is only used by
 *           storage implementations that must enforce namespace isolation.
 */
public interface StorageInterface extends AutoCloseable, Plugin {

    /**
     * Opens any resources or performs any pre-checks for initializing this storage.
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

    /**
     * Retrieves an input stream for the given storage URI.
     *
     * @param tenantId  the tenant identifier
     * @param namespace the namespace of the object (may be null)
     * @param uri       the URI of the object to retrieve
     * @return an InputStream to read the object's contents
     * @throws IOException if the object cannot be read
     */
    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class, NoSuchFileException.class})
    InputStream get(String tenantId, @Nullable String namespace, URI uri) throws IOException;

    /**
     * Retrieves an input stream of a instance resource for the given storage URI.
     * An instance resource is a resource stored outside any tenant storage, accessible for the whole instance
     *
     * @param namespace the namespace of the object (may be null)
     * @param uri       the URI of the object to retrieve
     * @return an InputStream to read the object's contents
     * @throws IOException if the object cannot be read
     */
    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class, NoSuchFileException.class})
    InputStream getInstanceResource(@Nullable String namespace, URI uri) throws IOException;

    /**
     * Retrieves a storage object along with its metadata.
     *
     * @param tenantId  the tenant identifier
     * @param namespace the namespace of the object (may be null)
     * @param uri       the URI of the object to retrieve
     * @return the storage object with metadata
     * @throws IOException if the object cannot be retrieved
     */
    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class, NoSuchFileException.class})
    StorageObject getWithMetadata(String tenantId, @Nullable String namespace, URI uri) throws IOException;

    /**
     * Returns all object URIs that start with the given prefix.
     *
     * @param tenantId           the tenant identifier
     * @param namespace          the namespace (may be null)
     * @param prefix             the URI prefix to search
     * @param includeDirectories whether to include directories in the results (directories will have a trailing '/')
     * @return a list of matching object URIs
     * @throws IOException if the listing fails
     */
    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class, NoSuchFileException.class})
    List<URI> allByPrefix(String tenantId, @Nullable String namespace, URI prefix, boolean includeDirectories) throws IOException;

    /**
     * Lists the attributes of all files and directories under the given URI.
     *
     * @param tenantId  the tenant identifier
     * @param namespace the namespace (may be null)
     * @param uri       the URI to list
     * @return a list of file attributes
     * @throws IOException if the listing fails
     */
    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class, NoSuchFileException.class})
    List<FileAttributes> list(String tenantId, @Nullable String namespace, URI uri) throws IOException;

    /**
     * Lists the attributes of all instance files and instance directories under the given URI.
     * An instance resource is a resource stored outside any tenant storage, accessible for the whole instance
     *
     * @param namespace the namespace (may be null)
     * @param uri       the URI to list
     * @return a list of file attributes
     * @throws IOException if the listing fails
     */
    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class, NoSuchFileException.class})
    List<FileAttributes> listInstanceResource(@Nullable String namespace, URI uri) throws IOException;

    /**
     * Checks whether the given URI exists in the internal storage.
     *
     * @param tenantId  the tenant identifier
     * @param namespace the namespace (may be null)
     * @param uri       the URI to check
     * @return true if the URI exists, false otherwise
     */
    @SuppressWarnings("try")
    default boolean exists(String tenantId, @Nullable String namespace, URI uri) {
        try (InputStream ignored = get(tenantId, namespace, uri)) {
            return true;
        } catch (IOException ieo) {
            return false;
        }
    }

    /**
     * Checks whether the given URI exists in the instance internal storage.
     * An instance resource is a resource stored outside any tenant storage, accessible for the whole instance
     *
     * @param namespace the namespace (may be null)
     * @param uri       the URI to check
     * @return true if the URI exists, false otherwise
     */
    @SuppressWarnings("try")
    default boolean existsInstanceResource(@Nullable String namespace, URI uri) {
        try (InputStream ignored = getInstanceResource(namespace, uri)) {
            return true;
        } catch (IOException ieo) {
            return false;
        }
    }

    /**
     * Retrieves the metadata attributes for the given URI.
     *
     * @param tenantId  the tenant identifier
     * @param namespace the namespace (may be null)
     * @param uri       the URI of the object
     * @return the file attributes
     * @throws IOException if the attributes cannot be retrieved
     */
    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class, NoSuchFileException.class})
    FileAttributes getAttributes(String tenantId, @Nullable String namespace, URI uri) throws IOException;

    /**
     * Retrieves the metadata attributes for the given URI.
     * n instance resource is a resource stored outside any tenant storage, accessible for the whole instance
     *
     * @param namespace the namespace (may be null)
     * @param uri       the URI of the object
     * @return the file attributes
     * @throws IOException if the attributes cannot be retrieved
     */
    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class, NoSuchFileException.class})
    FileAttributes getInstanceAttributes(@Nullable String namespace, URI uri) throws IOException;

    /**
     * Stores data at the given URI.
     *
     * @param tenantId  the tenant identifier
     * @param namespace the namespace (may be null)
     * @param uri       the target URI
     * @param data      the input stream containing the data to store
     * @return the URI of the stored object
     * @throws IOException if storing fails
     */
    @Retryable(includes = {IOException.class})
    default URI put(String tenantId, @Nullable String namespace, URI uri, InputStream data) throws IOException {
        return this.put(tenantId, namespace, uri, new StorageObject(null, data));
    }

    /**
     * Stores a storage object at the given URI.
     *
     * @param tenantId      the tenant identifier
     * @param namespace     the namespace (may be null)
     * @param uri           the target URI
     * @param storageObject the storage object to store
     * @return the URI of the stored object
     * @throws IOException if storing fails
     */
    @Retryable(includes = {IOException.class})
    URI put(String tenantId, @Nullable String namespace, URI uri, StorageObject storageObject) throws IOException;

    /**
     * Stores instance data at the given URI.
     * An instance resource is a resource stored outside any tenant storage, accessible for the whole instance
     *
     * @param namespace the namespace (may be null)
     * @param uri       the target URI
     * @param data      the input stream containing the data to store
     * @return the URI of the stored object
     * @throws IOException if storing fails
     */
    @Retryable(includes = {IOException.class})
    default URI putInstanceResource(@Nullable String namespace, URI uri, InputStream data) throws IOException {
        return this.putInstanceResource(namespace, uri, new StorageObject(null, data));
    }

    /**
     * Stores a instance storage object at the given URI.
     * An instance resource is a resource stored outside any tenant storage, accessible for the whole instance
     *
     * @param namespace     the namespace (may be null)
     * @param uri           the target URI
     * @param storageObject the storage object to store
     * @return the URI of the stored object
     * @throws IOException if storing fails
     */
    @Retryable(includes = {IOException.class})
    URI putInstanceResource(@Nullable String namespace, URI uri, StorageObject storageObject) throws IOException;

    /**
     * Deletes the object at the given URI.
     *
     * @param tenantId  the tenant identifier
     * @param namespace the namespace (may be null)
     * @param uri       the URI of the object to delete
     * @return true if deletion was successful
     * @throws IOException if deletion fails
     */
    @Retryable(includes = {IOException.class})
    boolean delete(String tenantId, @Nullable String namespace, URI uri) throws IOException;

    /**
     * Deletes the instance object at the given URI.
     * An instance resource is a resource stored outside any tenant storage, accessible for the whole instance
     *
     * @param namespace the namespace (may be null)
     * @param uri       the URI of the object to delete
     * @return true if deletion was successful
     * @throws IOException if deletion fails
     */
    @Retryable(includes = {IOException.class})
    boolean deleteInstanceResource(@Nullable String namespace, URI uri) throws IOException;

    /**
     * Creates a new directory at the given URI.
     *
     * @param tenantId  the tenant identifier
     * @param namespace the namespace (optional)
     * @param uri       the URI of the directory to create
     * @return the URI of the created directory
     * @throws IOException if creation fails
     */
    @Retryable(includes = {IOException.class})
    URI createDirectory(String tenantId, @Nullable String namespace, URI uri) throws IOException;

    /**
     * Creates a new instance directory at the given URI.
     * An instance resource is a resource stored outside any tenant storage, accessible for the whole instance
     *
     * @param namespace the namespace
     * @param uri       the URI of the directory to create
     * @return the URI of the created directory
     * @throws IOException if creation fails
     */
    @Retryable(includes = {IOException.class})
    URI createInstanceDirectory(String namespace, URI uri) throws IOException;

    /**
     * Moves an object from one URI to another.
     *
     * @param tenantId  the tenant identifier
     * @param namespace the namespace (optional)
     * @param from      the source URI
     * @param to        the destination URI
     * @return the URI of the moved object
     * @throws IOException if moving fails
     */
    @Retryable(includes = {IOException.class}, excludes = {FileNotFoundException.class, NoSuchFileException.class})
    URI move(String tenantId, @Nullable String namespace, URI from, URI to) throws IOException;

    /**
     * Deletes all objects that match the given URI prefix.
     *
     * @param tenantId      the tenant identifier
     * @param namespace     the namespace (may be null)
     * @param storagePrefix the prefix of the storage objects to delete
     * @return the list of URIs that were deleted
     * @throws IOException if deletion fails
     */
    @Retryable(includes = {IOException.class})
    List<URI> deleteByPrefix(String tenantId, @Nullable String namespace, URI storagePrefix) throws IOException;

    /**
     * Stores a file from a local File object into internal storage for an execution input.
     *
     * @param execution the execution context
     * @param input     the input name
     * @param fileName  the name of the file
     * @param file      the file to upload
     * @return the URI of the stored object
     * @throws IOException if uploading fails
     */
    @Retryable(includes = {IOException.class})
    default URI from(Execution execution, String input, String fileName, File file) throws IOException {
        URI uri = StorageContext.forInput(execution, input, fileName).getContextStorageURI();
        return this.put(execution.getTenantId(), execution.getNamespace(), uri, new BufferedInputStream(new FileInputStream(file)));
    }

    /**
     * Validates that the provided URI does not contain relative parent path traversal (i.e., "..").
     *
     * @param uri the URI to validate
     * @throws IllegalArgumentException if the URI attempts to traverse parent directories
     */
    default void parentTraversalGuard(URI uri) {
        if (uri != null && (uri.toString().contains(".." + File.separator) || uri.toString().contains(File.separator + "..") || uri.toString().equals(".."))) {
            throw new IllegalArgumentException("File should be accessed with their full path and not using relative '..' path.");
        }
    }

    /**
     * Builds the internal storage path based on the URI.
     *
     * @param uri      the URI of the object
     * @return a normalized internal path
     */
    default String getPath(URI uri) {
        if (uri == null) {
            uri = URI.create("/");
        }

        parentTraversalGuard(uri);
        String path = uri.getPath();
        path = path.replaceFirst("^/", "");
        return path;
    }

    /**
     * Builds the internal storage path based on tenant ID and URI.
     *
     * @param tenantId the tenant identifier
     * @param uri      the URI of the object
     * @return a normalized internal path
     */
    default String getPath(String tenantId, URI uri) {
        String path = getPath(uri);
        path = tenantId + (path.startsWith("/") ? path :  "/" + path);

        return path;
    }

    /**
     * Ensures the object name length does not exceed the allowed maximum.
     * If it does, the object name is truncated and a short random prefix is added
     * to avoid potential name collisions.
     *
     * @param uri                  the URI of the object
     * @param maxObjectNameLength  the maximum allowed length for the object name
     * @return a normalized URI respecting the length limit
     * @throws IOException if the URI cannot be rebuilt
     */
    default URI limit(URI uri, int maxObjectNameLength) throws IOException {
        if (uri == null) {
            return null;
        }

        String path = uri.getPath();
        String objectName = path.contains("/") ? path.substring(path.lastIndexOf("/") + 1) : path;
        if (objectName.length() > maxObjectNameLength) {
            objectName = objectName.substring(objectName.length() - maxObjectNameLength + 6);
            String prefix = RandomStringUtils.secure()
                .nextAlphanumeric(5)
                .toLowerCase();

            String newPath = (path.contains("/") ? path.substring(0, path.lastIndexOf("/") + 1) : "")
                + prefix + "-" + objectName;

            try {
                return new URI(uri.getScheme(), uri.getHost(), newPath, uri.getFragment());
            } catch (java.net.URISyntaxException e) {
                throw new IOException(e);
            }
        }

        return uri;
    }
}
