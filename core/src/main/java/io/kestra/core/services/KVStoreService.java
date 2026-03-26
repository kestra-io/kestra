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
import io.kestra.core.runners.KVMetadataStateStore;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVStoreException;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Singleton
@Slf4j
public class KVStoreService {
    @Inject
    private KVMetadataStateStore kvMetadataStateStore;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private NamespaceService namespaceService;

    @Inject
    private StorageInterface storage;

    @Inject
    private Optional<KvMetadataRepositoryInterface> kvMetadataRepository;

    /**
     * Gets access to the Key-Value store for the given namespace.
     *
     * @param tenant The tenant ID.
     * @param namespace The namespace of the K/V store.
     * @return The {@link KVStore}.
     */
    public KVStore get(String tenant, String namespace) {
        checkAccessNamespaceIsAllowed(tenant, namespace, namespace);
        return new InternalKVStore(tenant, namespace, storageInterface, kvMetadataStateStore);
    }

    /**
     * Gets access to the Key-Value store for the given namespace.
     *
     * @param tenant The tenant ID.
     * @param namespace The namespace of the K/V store.
     * @param fromNamespace The namespace from which the K/V store is accessed.
     * @return The {@link KVStore}.
     */
    public KVStore get(String tenant, String namespace, @Nullable String fromNamespace) {
        checkAccessNamespaceIsAllowed(tenant, namespace, fromNamespace);
        return new InternalKVStore(tenant, namespace, storageInterface, kvMetadataStateStore);
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

    private KvMetadataRepositoryInterface getKvMetadataRepository() {
        return this.kvMetadataRepository.orElseThrow(() -> new KVStoreException("The K/V store metadata repository is not available. This operation cannot be performed."));
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
            .map(entry -> KVStore.storageUri(entry.key(), namespace, entry.version()))
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

    /**
     * Checks if access to the given namespace is allowed from the specified namespace and if the namespace exists.
     *
     * @param tenant The tenant ID.
     * @param namespace The namespace of the K/V store.
     * @param fromNamespace The namespace from which the K/V store is accessed.
     */
    public void checkAccessNamespaceIsAllowed(String tenant, String namespace, @Nullable String fromNamespace) {
        boolean isNotSameNamespace = fromNamespace != null && !namespace.equals(fromNamespace);
        if (isNotSameNamespace && isNotParentNamespace(namespace, fromNamespace)) {
            try {
                namespaceService.checkAllowedNamespace(tenant, namespace, tenant, fromNamespace);
            } catch (IllegalArgumentException e) {
                throw new KVStoreException(
                    String.format(
                        "Cannot access the KV store. Access to '%s' namespace is not allowed from '%s'.", namespace, fromNamespace
                    )
                );
            }
        }

        // Only check namespace existence if not a descendant
        boolean checkIfNamespaceExists = fromNamespace == null || isNotParentNamespace(namespace, fromNamespace);
        if (checkIfNamespaceExists && !namespaceService.isNamespaceExists(tenant, namespace)) {
            // if it didn't exist, we still check if there are KV as you can add KV without creating a namespace in DB or having flows in it
            if (!kvMetadataStateStore.existsByNamespace(tenant, namespace)) {
                throw new KVStoreException(
                    String.format(
                        "Cannot access the KV store. The namespace '%s' does not exist.",
                        namespace
                    )
                );
            }
        }
    }

    private static boolean isNotParentNamespace(final String parentNamespace, final String childNamespace) {
        return !childNamespace.startsWith(parentNamespace);
    }
}
