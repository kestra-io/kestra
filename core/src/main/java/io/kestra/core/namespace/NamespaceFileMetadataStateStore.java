package io.kestra.core.namespace;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;

import jakarta.annotation.Nullable;

/**
 * Abstraction layer for namespace file metadata operations, used by {@link io.kestra.core.storages.InternalNamespace}.
 * <p>
 * On controller/webserver/standalone servers, the default implementation delegates directly to
 * {@link io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface}.
 * On workers, a gRPC-based implementation communicates with the controller.
 * <p>
 * This interface only exposes worker-safe operations. Server-only operations (paginated listing with
 * advanced filters, version-aware queries, purge) require direct access to
 * {@link io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface}.
 */
public interface NamespaceFileMetadataStateStore {

    /**
     * Find a namespace file metadata entry by tenant, namespace, and path.
     * Optionally filter by a specific version and/or include deleted entries.
     *
     * @param tenantId the tenant ID
     * @param namespace the namespace
     * @param path the file path
     * @param version optional version number (if {@code null}, returns the latest non-deleted entry)
     * @param allowDeleted whether to include deleted entries
     * @return an optional namespace file metadata entry
     * @throws IOException if an I/O error occurs
     */
    Optional<NamespaceFileMetadata> findByPath(String tenantId, String namespace, String path, @Nullable Integer version, boolean allowDeleted) throws IOException;

    /**
     * Find all non-deleted namespace file metadata entries for a given tenant and namespace,
     * optionally filtered by path prefix and containing text.
     *
     * @param tenantId the tenant ID
     * @param namespace the namespace
     * @param parentPath optional parent path prefix filter (for children queries)
     * @param recursive whether to search recursively under parentPath
     * @return list of active namespace file metadata entries
     */
    List<NamespaceFileMetadata> findChildren(String tenantId, String namespace, @Nullable String parentPath, boolean recursive);

    /**
     * Find all non-deleted namespace file metadata entries for a given tenant and namespace,
     * optionally filtered by a containing substring.
     *
     * @param tenantId the tenant ID
     * @param namespace the namespace
     * @param containing optional path substring filter (can be {@code null})
     * @return list of active namespace file metadata entries
     */
    List<NamespaceFileMetadata> findAll(String tenantId, String namespace, @Nullable String containing);

    /**
     * Find namespace file metadata entries by tenant, namespace, and a list of paths.
     * Used for move and delete operations.
     *
     * @param tenantId the tenant ID
     * @param namespace the namespace
     * @param paths the list of paths to search for
     * @param allowDeleted whether to include deleted entries
     * @return list of matching namespace file metadata entries
     */
    List<NamespaceFileMetadata> findByPaths(String tenantId, String namespace, List<String> paths, boolean allowDeleted);

    /**
     * Find all versions of namespace file metadata entries by tenant, namespace, and a list of paths.
     * Used for move operations that need to access version history.
     *
     * @param tenantId the tenant ID
     * @param namespace the namespace
     * @param paths the list of paths to search for
     * @return list of all versions of matching namespace file metadata entries
     */
    List<NamespaceFileMetadata> findAllVersionsByPaths(String tenantId, String namespace, List<String> paths);

    /**
     * Check whether any non-deleted namespace file entries exist in the given namespace.
     *
     * @param tenantId the tenant ID
     * @param namespace the namespace
     * @return {@code true} if at least one active namespace file entry exists
     */
    boolean existsByNamespace(String tenantId, String namespace);

    /**
     * Save a namespace file metadata entry.
     *
     * @param item the namespace file metadata entry to save
     * @return the saved namespace file metadata entry
     */
    NamespaceFileMetadata save(NamespaceFileMetadata item);

    /**
     * Soft-delete a namespace file metadata entry.
     *
     * @param item the namespace file metadata entry to delete
     * @return the deleted namespace file metadata entry
     * @throws IOException if an I/O error occurs
     */
    default NamespaceFileMetadata delete(NamespaceFileMetadata item) throws IOException {
        return this.save(item.toDeleted());
    }
}
