package io.kestra.core.repositories;

import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.micronaut.data.model.Pageable;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface KvMetadataRepositoryInterface extends SaveRepositoryInterface<PersistedKvMetadata> {
    Optional<PersistedKvMetadata> findByName(
        String tenantId,
        String namespace,
        String name
    ) throws IOException;

    default ArrayListTotal<PersistedKvMetadata> find(
        Pageable pageable,
        String tenantId,
        List<QueryFilter> filters,
        boolean allowDeleted,
        boolean allowExpired
    ) {
        return this.find(pageable, tenantId, filters, allowDeleted, allowExpired, FetchVersion.LATEST);
    }

    ArrayListTotal<PersistedKvMetadata> find(
        Pageable pageable,
        String tenantId,
        List<QueryFilter> filters,
        boolean allowDeleted,
        boolean allowExpired,
        FetchVersion fetchBehavior
    );

    default PersistedKvMetadata delete(PersistedKvMetadata persistedKvMetadata) throws IOException {
        return this.save(persistedKvMetadata.toBuilder().deleted(true).build());
    }

    /**
     * Purge (hard delete) a list of persisted kv metadata. If no version is specified, all versions are purged.
     * @param persistedKvsMetadata the list of persisted kv metadata to purge
     * @return the number of purged persisted kv metadata
     */
    Integer purge(List<PersistedKvMetadata> persistedKvsMetadata);
}
