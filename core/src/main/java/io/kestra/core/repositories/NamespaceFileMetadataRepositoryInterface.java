package io.kestra.core.repositories;

import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.micronaut.data.model.Pageable;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface NamespaceFileMetadataRepositoryInterface extends SaveRepositoryInterface<NamespaceFileMetadata> {
    Optional<NamespaceFileMetadata> findByPath(
        String tenantId,
        String namespace,
        String path
    ) throws IOException;

    default ArrayListTotal<NamespaceFileMetadata> find(
        Pageable pageable,
        String tenantId,
        List<QueryFilter> filters,
        boolean allowDeleted
    ) {
        return this.find(pageable, tenantId, filters, allowDeleted, FetchVersion.LATEST);
    }

    ArrayListTotal<NamespaceFileMetadata> find(
        Pageable pageable,
        String tenantId,
        List<QueryFilter> filters,
        boolean allowDeleted,
        FetchVersion fetchBehavior
    );

    default NamespaceFileMetadata delete(NamespaceFileMetadata namespaceFileMetadata) throws IOException {
        return this.save(namespaceFileMetadata.toBuilder().deleted(true).build());
    }

    /**
     * Purge (hard delete) a list of namespace files metadata. If no version is specified, all versions are purged.
     * @param namespaceFilesMetadata the list of namespace files metadata to purge
     * @return the number of purged namespace files metadata
     */
    Integer purge(List<NamespaceFileMetadata> namespaceFilesMetadata);
}
