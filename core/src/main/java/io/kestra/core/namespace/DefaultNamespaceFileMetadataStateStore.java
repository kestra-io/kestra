package io.kestra.core.namespace;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Default implementation of {@link NamespaceFileMetadataStateStore} that delegates
 * directly to {@link NamespaceFileMetadataRepositoryInterface}.
 * <p>
 * This implementation is active on controller, webserver, and standalone servers
 * where the repository is available. Workers use a gRPC-based implementation instead.
 */
@Singleton
@Requires(beans = NamespaceFileMetadataRepositoryInterface.class)
public class DefaultNamespaceFileMetadataStateStore implements NamespaceFileMetadataStateStore {

    private final NamespaceFileMetadataRepositoryInterface repository;

    @Inject
    public DefaultNamespaceFileMetadataStateStore(NamespaceFileMetadataRepositoryInterface repository) {
        this.repository = repository;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<NamespaceFileMetadata> findByPath(String tenantId, String namespace, String path, @Nullable Integer version, boolean allowDeleted) throws IOException {
        if (version != null) {
            return repository.find(
                Pageable.from(1, 1), tenantId, List.of(
                    QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespace).build(),
                    QueryFilter.builder().field(QueryFilter.Field.PATH).operation(QueryFilter.Op.EQUALS).value(path).build(),
                    QueryFilter.builder().field(QueryFilter.Field.VERSION).operation(QueryFilter.Op.EQUALS).value(version).build()
                ), allowDeleted, FetchVersion.ALL
            ).stream().findFirst();
        }

        return repository.findByPath(tenantId, namespace, path).filter(m -> allowDeleted || !m.isDeleted());
    }

    /** {@inheritDoc} */
    @Override
    public List<NamespaceFileMetadata> findChildren(String tenantId, String namespace, @Nullable String parentPath, boolean recursive) {
        String normalizedParentPath = parentPath;
        if (normalizedParentPath != null && !normalizedParentPath.endsWith("/")) {
            normalizedParentPath = normalizedParentPath + "/";
        }

        List<QueryFilter> filters = new java.util.ArrayList<>(
            List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespace).build()
            )
        );

        if (normalizedParentPath != null) {
            filters.add(
                QueryFilter.builder()
                    .field(QueryFilter.Field.PARENT_PATH)
                    .operation(recursive ? QueryFilter.Op.STARTS_WITH : QueryFilter.Op.EQUALS)
                    .value(normalizedParentPath)
                    .build()
            );
        }

        return repository.find(Pageable.UNPAGED, tenantId, filters, false);
    }

    /** {@inheritDoc} */
    @Override
    public List<NamespaceFileMetadata> findAll(String tenantId, String namespace, @Nullable String containing) {
        List<QueryFilter> filters = Stream.concat(
            Stream.of(QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespace).build()),
            Optional.ofNullable(containing).flatMap(p ->
            {
                if (p.equals("/")) {
                    return Optional.empty();
                }
                return Optional.of(QueryFilter.builder().field(QueryFilter.Field.QUERY).operation(QueryFilter.Op.EQUALS).value(p).build());
            }).stream()
        ).toList();

        return repository.find(Pageable.UNPAGED, tenantId, filters, false);
    }

    /** {@inheritDoc} */
    @Override
    public List<NamespaceFileMetadata> findByPaths(String tenantId, String namespace, List<String> paths, boolean allowDeleted) {
        return repository.find(
            Pageable.UNPAGED, tenantId, List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespace).build(),
                QueryFilter.builder().field(QueryFilter.Field.PATH).operation(QueryFilter.Op.IN).value(paths).build()
            ), allowDeleted
        );
    }

    /** {@inheritDoc} */
    @Override
    public List<NamespaceFileMetadata> findAllVersionsByPaths(String tenantId, String namespace, List<String> paths) {
        return repository.find(
            Pageable.UNPAGED, tenantId, List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespace).build(),
                QueryFilter.builder().field(QueryFilter.Field.PATH).operation(QueryFilter.Op.IN).value(paths).build()
            ), true, FetchVersion.ALL
        );
    }

    /** {@inheritDoc} */
    @Override
    public boolean existsByNamespace(String tenantId, String namespace) {
        return !repository.find(
            Pageable.from(1, 1),
            tenantId,
            List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.NAMESPACE)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(namespace)
                    .build()
            ),
            false
        ).isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    public NamespaceFileMetadata save(NamespaceFileMetadata item) {
        return repository.save(item);
    }

    /** {@inheritDoc} */
    @Override
    public NamespaceFileMetadata delete(NamespaceFileMetadata item) throws IOException {
        return repository.delete(item);
    }
}
