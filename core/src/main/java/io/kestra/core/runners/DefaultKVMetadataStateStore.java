package io.kestra.core.runners;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Default implementation of {@link KVMetadataStateStore} that delegates
 * directly to {@link KvMetadataRepositoryInterface}.
 * <p>
 * This implementation is active on controller, webserver, and standalone servers
 * where the repository is available. Workers use a gRPC-based implementation instead.
 */
@Singleton
@Requires(property = "kestra.server-type", notEquals = "WORKER")
public class DefaultKVMetadataStateStore implements KVMetadataStateStore {

    private final KvMetadataRepositoryInterface kvMetadataRepository;

    @Inject
    public DefaultKVMetadataStateStore(KvMetadataRepositoryInterface kvMetadataRepository) {
        this.kvMetadataRepository = kvMetadataRepository;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PersistedKvMetadata> findByName(String tenantId, String namespace, String name) throws IOException {
        return kvMetadataRepository.findByName(tenantId, namespace, name);
    }

    /** {@inheritDoc} */
    @Override
    public List<PersistedKvMetadata> find(String tenantId, @Nullable String namespace) {
        List<QueryFilter> filters = namespace == null ? List.of()
            : List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.NAMESPACE)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(namespace)
                    .build()
            );
        return kvMetadataRepository.find(
            Pageable.UNPAGED,
            tenantId,
            filters,
            false,
            false
        );
    }

    /** {@inheritDoc} */
    @Override
    public boolean existsByNamespace(String tenantId, String namespace) {
        return !kvMetadataRepository.find(
            Pageable.from(1, 1),
            tenantId,
            List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.NAMESPACE)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(namespace)
                    .build()
            ),
            false,
            false
        ).isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    public PersistedKvMetadata save(PersistedKvMetadata item) {
        return kvMetadataRepository.save(item);
    }

    /** {@inheritDoc} */
    @Override
    public PersistedKvMetadata delete(PersistedKvMetadata persistedKvMetadata) throws IOException {
        return kvMetadataRepository.delete(persistedKvMetadata);
    }
}
