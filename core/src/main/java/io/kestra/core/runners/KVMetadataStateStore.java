package io.kestra.core.runners;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import io.kestra.core.models.kv.PersistedKvMetadata;

import jakarta.annotation.Nullable;

/**
 * Abstraction layer for KV metadata operations, used by {@link io.kestra.core.storages.kv.InternalKVStore}
 * and {@link io.kestra.core.services.KVStoreService}.
 * <p>
 * On controller/webserver/standalone servers, the default implementation delegates directly to
 * {@link io.kestra.core.repositories.KvMetadataRepositoryInterface}.
 * On workers, a gRPC-based implementation communicates with the controller.
 * <p>
 * This interface only exposes worker-safe operations. Server-only operations (paginated listing,
 * version-aware queries, purge) require direct access to
 * {@link io.kestra.core.repositories.KvMetadataRepositoryInterface}.
 */
public interface KVMetadataStateStore {

    /**
     * Find a KV metadata entry by tenant, namespace, and name.
     *
     * @param tenantId the tenant ID
     * @param namespace the namespace
     * @param name the key name
     * @return an optional KV metadata entry
     * @throws IOException if an I/O error occurs
     */
    Optional<PersistedKvMetadata> findByName(String tenantId, String namespace, String name) throws IOException;

    /**
     * Find all non-deleted, non-expired KV metadata entries for a given tenant and namespace.
     *
     * @param tenantId the tenant ID
     * @param namespace the namespace (maybe {@code null})
     * @return list of active KV metadata entries
     */
    List<PersistedKvMetadata> find(String tenantId, @Nullable String namespace);

    /**
     * Check whether any non-deleted, non-expired KV entries exist in the given namespace.
     *
     * @param tenantId the tenant ID
     * @param namespace the namespace
     * @return {@code true} if at least one active KV entry exists
     */
    boolean existsByNamespace(String tenantId, String namespace);

    /**
     * Save a KV metadata entry.
     *
     * @param item the KV metadata entry to save
     * @return the saved KV metadata entry
     */
    PersistedKvMetadata save(PersistedKvMetadata item);

    /**
     * Soft-delete a KV metadata entry.
     *
     * @param persistedKvMetadata the KV metadata entry to delete
     * @return the deleted KV metadata entry
     * @throws IOException if an I/O error occurs
     */
    default PersistedKvMetadata delete(PersistedKvMetadata persistedKvMetadata) throws IOException {
        return this.save(persistedKvMetadata.toDeleted());
    }

    /**
     * Soft-delete a KV metadata entry by tenant, namespace, and name.
     *
     * @param tenantId the tenant ID
     * @param namespace the namespace
     * @param name the key name
     * @throws IOException if an I/O error occurs
     */
    default Optional<PersistedKvMetadata> deleteByName(String tenantId, String namespace, String name) throws IOException {
        Optional<PersistedKvMetadata> existing = this.findByName(tenantId, namespace, name);
        if (existing.isPresent()) {
            return Optional.of(this.delete(existing.get()));
        }
        return Optional.empty();
    }
}
