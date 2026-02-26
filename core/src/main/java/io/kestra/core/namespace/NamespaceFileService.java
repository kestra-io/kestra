package io.kestra.core.namespace;

import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * Server-side service for namespace file operations that require direct repository access.
 * <p>
 * This includes paginated listing with advanced filters, version-aware queries, and purge operations.
 * These operations cannot run on workers (which don't have access to repositories) and must be
 * accessed via {@code DefaultRunContext.services().additionalService(NamespaceFileService.class)}.
 *
 * @see io.kestra.core.storages.Namespace for worker-safe operations
 */
@Singleton
@Slf4j
public class NamespaceFileService {

    private final StorageInterface storage;

    private final Optional<NamespaceFileMetadataRepositoryInterface> namespaceFileMetadataRepository;

    @Inject
    public NamespaceFileService(StorageInterface storage, 
                                Optional<NamespaceFileMetadataRepositoryInterface> namespaceFileMetadataRepository) {
        this.storage = storage;
        this.namespaceFileMetadataRepository = namespaceFileMetadataRepository;
    }

    /**
     * Lists namespace file entries with pagination, filtering, and version control.
     *
     * @param pageable      the pagination parameters.
     * @param tenantId      the tenant ID.
     * @param namespace     the namespace.
     * @param filters       the query filters.
     * @param allowDeleted  whether to include deleted entries.
     * @param fetchBehavior the version fetch behavior.
     * @return the paginated list of {@link NamespaceFile}.
     */
    public ArrayListTotal<NamespaceFile> find(Pageable pageable, String tenantId, String namespace, List<QueryFilter> filters, boolean allowDeleted, FetchVersion fetchBehavior) {
        List<QueryFilter> allFilters = Stream.concat(
            Stream.of(QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespace).build()),
            filters.stream()
        ).toList();

        return getRepository().find(
            pageable,
            tenantId,
            allFilters,
            allowDeleted,
            fetchBehavior
        ).map(NamespaceFile::fromMetadata);
    }

    /**
     * Lists all namespace file entries, including deleted ones, across all versions.
     *
     * @param tenantId  the tenant ID.
     * @param namespace the namespace.
     * @return the list of all {@link NamespaceFile}.
     */
    public List<NamespaceFile> listAll(String tenantId, String namespace) {
        return this.find(Pageable.UNPAGED, tenantId, namespace, Collections.emptyList(), true, FetchVersion.ALL);
    }

    /**
     * Purge (hard-delete) the provided namespace files. Removes both metadata and storage data.
     *
     * @param tenantId       the tenant ID.
     * @param namespace      the namespace.
     * @param namespaceFiles the files to purge.
     * @return the number of purged files.
     * @throws IOException if an error occurred while executing the purge operation.
     */
    public Integer purge(String tenantId, String namespace, List<NamespaceFile> namespaceFiles) throws IOException {
        Integer purgedMetadataCount = getRepository().purge(
            namespaceFiles.stream()
                .map(nsFile -> NamespaceFileMetadata.of(tenantId, nsFile))
                .toList()
        );

        long actualDeletedEntries = namespaceFiles.stream()
            .map(nsFile -> nsFile.storagePath().toUri())
            .map(throwFunction(uri -> storage.delete(tenantId, namespace, uri)))
            .filter(Boolean::booleanValue)
            .count();

        if (actualDeletedEntries != purgedMetadataCount) {
            log.warn(
                "Namespace file metadata purge reported {} deleted entries, but {} values were actually deleted from storage",
                purgedMetadataCount, actualDeletedEntries
            );
        }

        return purgedMetadataCount;
    }

    /**
     * Gets the namespace file revisions for a specific file path.
     *
     * @param tenantId  the tenant ID.
     * @param namespace the namespace.
     * @param path      the file path.
     * @return the list of namespace file metadata for all versions.
     */
    public ArrayListTotal<NamespaceFileMetadata> findRevisions(String tenantId, String namespace, String path) {
        return getRepository().find(Pageable.UNPAGED, tenantId, List.of(
            QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespace).build(),
            QueryFilter.builder().field(QueryFilter.Field.PATH).operation(QueryFilter.Op.EQUALS).value(path).build()
        ), true, FetchVersion.ALL);
    }

    /**
     * Finds namespace file metadata entries with pagination and filters.
     * Used by the controller for deletion zombie-awareness checks.
     *
     * @param pageable     the pagination parameters.
     * @param tenantId     the tenant ID.
     * @param filters      the query filters.
     * @param allowDeleted whether to include deleted entries.
     * @return the paginated list of {@link NamespaceFileMetadata}.
     */
    public ArrayListTotal<NamespaceFileMetadata> findMetadata(Pageable pageable, String tenantId, List<QueryFilter> filters, boolean allowDeleted) {
        return getRepository().find(pageable, tenantId, filters, allowDeleted);
    }

    private NamespaceFileMetadataRepositoryInterface getRepository() {
        return this.namespaceFileMetadataRepository.orElseThrow(() ->
            new IllegalStateException("The namespace file metadata repository is not available. This operation cannot be performed on a worker.")
        );
    }
}
