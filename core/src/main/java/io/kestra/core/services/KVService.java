package io.kestra.core.services;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVStoreException;

import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * Server-side administration of the K/V stores: paginated and filtered listing, and purge. These operations
 * need the K/V metadata repository, so they are not available on a worker; a task reads and writes a store
 * through {@link KVStoreService} instead.
 */
@Singleton
@Slf4j
public class KVService {
    private final StorageInterface storage;
    private final Optional<KvMetadataRepositoryInterface> kvMetadataRepository;

    @Inject
    public KVService(StorageInterface storage, Optional<KvMetadataRepositoryInterface> kvMetadataRepository) {
        this.storage = storage;
        this.kvMetadataRepository = kvMetadataRepository;
    }

    public ArrayListTotal<KVEntry> list(Pageable pageable, String tenant, String namespace) throws IOException {
        return this.list(pageable, tenant, namespace, Collections.emptyList());
    }

    public ArrayListTotal<KVEntry> list(Pageable pageable, String tenant, String namespace, List<QueryFilter> queryFilters) throws IOException {
        return this.list(pageable, tenant, namespace, queryFilters, false, false, FetchVersion.LATEST);
    }

    /**
     * Lists K/V store entries with pagination, filtering, and version control.
     *
     * @param pageable The pagination parameters.
     * @param filters The query filters.
     * @param allowDeleted Whether to include deleted entries.
     * @param allowExpired Whether to include expired entries.
     * @param fetchBehavior The version fetch behavior.
     * @return The paginated list of {@link KVEntry}.
     * @throws IOException if an error occurred while executing the operation on the K/V store.
     */
    public ArrayListTotal<KVEntry> list(Pageable pageable, String tenant, String namespace, List<QueryFilter> filters, boolean allowDeleted, boolean allowExpired, FetchVersion fetchBehavior)
        throws IOException {
        if (namespace != null) {
            filters = Stream.concat(
                filters.stream(),
                Stream.of(QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespace).build())
            ).toList();
        }

        return getKvMetadataRepository().find(
            pageable,
            tenant,
            filters,
            allowDeleted,
            allowExpired,
            fetchBehavior
        ).map(throwFunction(KVEntry::from));
    }

    /**
     * Lists all the K/V store entries, expired or not.
     *
     * @param tenant The tenant ID.
     * @param namespace The namespace of the K/V store.
     * @return The list of all {@link KVEntry}.
     * @throws IOException if an error occurred while executing the operation on the K/V store.
     */
    public List<KVEntry> listAll(String tenant, String namespace) throws IOException {
        return this.list(Pageable.UNPAGED, tenant, namespace, Collections.emptyList(), true, true, FetchVersion.ALL);
    }

    /**
     * Purge the provided KV entries.
     *
     * @param kvEntries The entries to purge.
     * @return The number of purged entries.
     * @throws IOException if an error occurred while executing the operation on the K/V store.
     */
    public Integer purge(String tenant, String namespace, List<KVEntry> kvEntries) throws IOException {
        Integer purgedMetadataCount = getKvMetadataRepository().purge(kvEntries.stream().map(kv -> PersistedKvMetadata.from(tenant, kv)).toList());

        long actualDeletedEntries = kvEntries.stream()
            .map(entry -> KVStore.storageUri(entry.key(), namespace, entry.revision()))
            .map(throwFunction(uri ->
            {
                boolean deleted = this.storage.delete(tenant, namespace, uri);
                URI metadataURI = URI.create(uri.getPath() + ".metadata");
                if (this.storage.exists(tenant, namespace, metadataURI)) {
                    this.storage.delete(tenant, namespace, metadataURI);
                }

                return deleted;
            })).filter(Boolean::booleanValue)
            .count();

        if (actualDeletedEntries != purgedMetadataCount) {
            log.warn("KV Metadata purge reported {} deleted entries, but {} values were actually deleted from storage", purgedMetadataCount, actualDeletedEntries);
        }

        return purgedMetadataCount;
    }

    private KvMetadataRepositoryInterface getKvMetadataRepository() {
        return this.kvMetadataRepository.orElseThrow(() -> new KVStoreException("The K/V store metadata repository is not available. This operation cannot be performed."));
    }
}
