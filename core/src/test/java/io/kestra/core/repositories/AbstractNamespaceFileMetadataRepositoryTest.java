package io.kestra.core.repositories;

import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest(transactional = false)
public abstract class AbstractNamespaceFileMetadataRepositoryTest {
    @Inject
    protected NamespaceFileMetadataRepositoryInterface namespaceFileMetadataRepositoryInterface;

    @Test
    void findNamespaceFileMetadataByPath() throws IOException {
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "test/ns/file";
        NamespaceFileMetadata metadata = NamespaceFileMetadata.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .path(path)
            .version(1)
            .size(1L)
            .build();

        namespaceFileMetadataRepositoryInterface.save(metadata);

        namespaceFileMetadataRepositoryInterface.save(metadata.toBuilder().version(2).build());

        Optional<NamespaceFileMetadata> found = namespaceFileMetadataRepositoryInterface.findByPath(
            tenantId,
            namespace,
            path
        );

        assertThat(found).isPresent();
        assertThat(found.get().getPath()).isEqualTo(path);
        assertThat(found.get().getVersion()).isEqualTo(2);
        assertThat(found.get().isLast()).isTrue();
        assertThat(found.get().isDeleted()).isFalse();
    }

    @Test
    void deleteMetadata() throws IOException {
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "test/ns/file";
        NamespaceFileMetadata metadata = NamespaceFileMetadata.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .path(path)
            .version(1)
            .size(1L)
            .build();

        namespaceFileMetadataRepositoryInterface.save(metadata);

        Optional<NamespaceFileMetadata> found = namespaceFileMetadataRepositoryInterface.findByPath(
            tenantId,
            namespace,
            path
        );

        assertThat(found).isPresent();
        assertThat(found.get().getPath()).isEqualTo(path);
        assertThat(found.get().isLast()).isTrue();
        assertThat(found.get().isDeleted()).isFalse();
        Instant beforeDeleteUpdateDate = found.get().getUpdated();

        namespaceFileMetadataRepositoryInterface.delete(found.get());

        found = namespaceFileMetadataRepositoryInterface.findByPath(
            tenantId,
            namespace,
            path
        );

        assertThat(found).isPresent();
        assertThat(found.get().getPath()).isEqualTo(path);
        // Soft delete
        assertThat(found.get().getVersion()).isEqualTo(1);
        assertThat(found.get().isLast()).isTrue();
        assertThat(found.get().isDeleted()).isTrue();
        assertThat(found.get().getUpdated()).isAfter(beforeDeleteUpdateDate);
    }

    @Test
    void findWithVersionFilters() throws IOException {
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "test/ns/file";
        NamespaceFileMetadata metadata = NamespaceFileMetadata.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .path(path)
            .size(1L)
            .build();

        assertThat(metadata.getVersion()).isNull();
        assertThat(namespaceFileMetadataRepositoryInterface.save(metadata).getVersion()).isEqualTo(1);
        // Resaving will increment version
        metadata = namespaceFileMetadataRepositoryInterface.save(metadata);
        assertThat(metadata.getVersion()).isEqualTo(2);

        assertThat(namespaceFileMetadataRepositoryInterface.find(Pageable.from(1, 1), tenantId, Collections.emptyList(), false).getTotal()).isEqualTo(1);
        assertThat(namespaceFileMetadataRepositoryInterface.find(Pageable.from(1, 1), tenantId, Collections.emptyList(), false, FetchVersion.ALL).getTotal()).isEqualTo(2);
        assertThat(namespaceFileMetadataRepositoryInterface.find(Pageable.from(1, 1), tenantId, Collections.emptyList(), true).getTotal()).isEqualTo(1);
        assertThat(namespaceFileMetadataRepositoryInterface.find(Pageable.from(1, 1), tenantId, Collections.emptyList(), true, FetchVersion.ALL).getTotal()).isEqualTo(2);

        namespaceFileMetadataRepositoryInterface.delete(metadata);

        ArrayListTotal<NamespaceFileMetadata> found = namespaceFileMetadataRepositoryInterface.find(Pageable.from(1, 1), tenantId, Collections.emptyList(), false);
        assertThat(found).hasSize(0);
        assertThat(found.getTotal()).isEqualTo(0);

        found = namespaceFileMetadataRepositoryInterface.find(Pageable.from(1, 1), tenantId, Collections.emptyList(), false, FetchVersion.ALL);
        assertThat(found.getTotal()).isEqualTo(1);
        assertThat(found.getFirst().getVersion()).isEqualTo(1);
        assertThat(found.getFirst().isDeleted()).isEqualTo(false);

        found = namespaceFileMetadataRepositoryInterface.find(Pageable.from(1, 1), tenantId, Collections.emptyList(), true);
        assertThat(found.getTotal()).isEqualTo(1);
        assertThat(found.getFirst().getVersion()).isEqualTo(2);
        assertThat(found.getFirst().isDeleted()).isEqualTo(true);

        found = namespaceFileMetadataRepositoryInterface.find(Pageable.from(1, 1), tenantId, Collections.emptyList(), true, FetchVersion.ALL);
        assertThat(found.getTotal()).isEqualTo(2);
    }

    private static Stream<Arguments> filtersSource() {
        String path = "/test/ns/file";

        NamespaceFileMetadata namespaceFileMetadataNamespace = nsFileMetadata(path);
        NamespaceFileMetadata namespaceFileMetadataNotNamespace = nsFileMetadata(path);
        NamespaceFileMetadata namespaceFileMetadataQuery = nsFileMetadata(path);
        NamespaceFileMetadata namespaceFileMetadataNotQuery = nsFileMetadata(path);
        NamespaceFileMetadata namespaceFileMetadataPath = nsFileMetadata(path);
        NamespaceFileMetadata namespaceFileMetadataNotPath = nsFileMetadata(path);
        NamespaceFileMetadata namespaceFileMetadataParentPath = nsFileMetadata(path);
        NamespaceFileMetadata namespaceFileMetadataParentNotPath = nsFileMetadata(path);
        NamespaceFileMetadata namespaceFileMetadataVersion = nsFileMetadata(path);
        NamespaceFileMetadata namespaceFileMetadataVersion2 = nsFileMetadata(path);
        NamespaceFileMetadata namespaceFileMetadataVersion3 = nsFileMetadata(path);
        NamespaceFileMetadata namespaceFileMetadataVersion4 = nsFileMetadata(path);
        NamespaceFileMetadata namespaceFileMetadataNotVersion = nsFileMetadata(path);
        NamespaceFileMetadata namespaceFileMetadataUpdated = nsFileMetadata(path);
        NamespaceFileMetadata namespaceFileMetadataNotUpdated = nsFileMetadata(path);
        return Stream.of(
            // region NAMESPACE
            Arguments.of(
                namespaceFileMetadataNamespace.getTenantId(),
                List.of(namespaceFileMetadataNamespace),
                List.of(QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespaceFileMetadataNamespace.getNamespace()).build()),
                List.of(namespaceFileMetadataNamespace.toBuilder().version(1).last(true).build()),
                FetchVersion.ALL
            ),
            Arguments.of(
                namespaceFileMetadataNotNamespace.getTenantId(),
                List.of(namespaceFileMetadataNotNamespace),
                List.of(QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.NOT_EQUALS).value(namespaceFileMetadataNotNamespace.getNamespace()).build()),
                Collections.emptyList(),
                FetchVersion.ALL
            ),
            // endregion
            // region QUERY
            Arguments.of(
                namespaceFileMetadataQuery.getTenantId(),
                List.of(namespaceFileMetadataQuery),
                List.of(QueryFilter.builder().field(QueryFilter.Field.QUERY).operation(QueryFilter.Op.EQUALS).value("tes").build()),
                List.of(namespaceFileMetadataQuery.toBuilder().version(1).last(true).build()),
                FetchVersion.ALL
            ),
            // endregion
            // region PATH
            Arguments.of(
                namespaceFileMetadataPath.getTenantId(),
                List.of(namespaceFileMetadataPath),
                List.of(QueryFilter.builder().field(QueryFilter.Field.PATH).operation(QueryFilter.Op.EQUALS).value("/test/ns/file").build()),
                List.of(namespaceFileMetadataPath.toBuilder().version(1).last(true).build()),
                FetchVersion.ALL
            ),
            Arguments.of(
                namespaceFileMetadataNotPath.getTenantId(),
                List.of(namespaceFileMetadataNotPath),
                List.of(QueryFilter.builder().field(QueryFilter.Field.PATH).operation(QueryFilter.Op.NOT_EQUALS).value("/test/ns/file").build()),
                Collections.emptyList(),
                FetchVersion.ALL
            ),
            // endregion
            // region PARENT PATH
            Arguments.of(
                namespaceFileMetadataParentPath.getTenantId(),
                List.of(namespaceFileMetadataParentPath),
                List.of(QueryFilter.builder().field(QueryFilter.Field.PARENT_PATH).operation(QueryFilter.Op.EQUALS).value("/test/ns/").build()),
                List.of(namespaceFileMetadataParentPath.toBuilder().version(1).last(true).build()),
                FetchVersion.ALL
            ),
            Arguments.of(
                namespaceFileMetadataParentNotPath.getTenantId(),
                List.of(namespaceFileMetadataParentNotPath),
                List.of(QueryFilter.builder().field(QueryFilter.Field.PARENT_PATH).operation(QueryFilter.Op.NOT_EQUALS).value("/test/ns/").build()),
                Collections.emptyList(),
                FetchVersion.ALL
            ),
            // endregion
            // region VERSION
            Arguments.of(
                namespaceFileMetadataVersion.getTenantId(),
                List.of(namespaceFileMetadataVersion, namespaceFileMetadataVersion),
                List.of(QueryFilter.builder().field(QueryFilter.Field.VERSION).operation(QueryFilter.Op.EQUALS).value(1).build()),
                List.of(namespaceFileMetadataVersion.toBuilder().version(1).last(false).build()),
                FetchVersion.ALL
            ),
            Arguments.of(
                namespaceFileMetadataVersion2.getTenantId(),
                List.of(namespaceFileMetadataVersion2, namespaceFileMetadataVersion2),
                List.of(QueryFilter.builder().field(QueryFilter.Field.VERSION).operation(QueryFilter.Op.EQUALS).value(2).build()),
                List.of(namespaceFileMetadataVersion2.toBuilder().version(2).last(true).build()),
                FetchVersion.ALL
            ),
            Arguments.of(
                namespaceFileMetadataVersion3.getTenantId(),
                List.of(namespaceFileMetadataVersion3, namespaceFileMetadataVersion3),
                Collections.emptyList(),
                List.of(namespaceFileMetadataVersion3.toBuilder().version(2).last(true).build()),
                // FetchVersion null should default to latest
                null
            ),
            Arguments.of(
                namespaceFileMetadataVersion4.getTenantId(),
                List.of(namespaceFileMetadataVersion4, namespaceFileMetadataVersion4),
                Collections.emptyList(),
                List.of(namespaceFileMetadataVersion4.toBuilder().version(1).last(false).build()),
                FetchVersion.OLD
            ),
            Arguments.of(
                namespaceFileMetadataNotVersion.getTenantId(),
                List.of(namespaceFileMetadataNotVersion, namespaceFileMetadataNotVersion),
                List.of(QueryFilter.builder().field(QueryFilter.Field.VERSION).operation(QueryFilter.Op.NOT_EQUALS).value(2).build()),
                List.of(namespaceFileMetadataNotVersion.toBuilder().version(1).last(false).build()),
                FetchVersion.ALL
            ),
            // endregion
            // region UPDATED
            Arguments.of(
                namespaceFileMetadataUpdated.getTenantId(),
                List.of(namespaceFileMetadataUpdated),
                List.of(QueryFilter.builder().field(QueryFilter.Field.UPDATED).operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO).value(Instant.now()).build()),
                List.of(namespaceFileMetadataUpdated.toBuilder().version(1).last(true).build()),
                FetchVersion.ALL
            ),
            Arguments.of(
                namespaceFileMetadataNotUpdated.getTenantId(),
                List.of(namespaceFileMetadataNotUpdated),
                List.of(QueryFilter.builder().field(QueryFilter.Field.UPDATED).operation(QueryFilter.Op.LESS_THAN_OR_EQUAL_TO).value(Instant.now()).build()),
                Collections.emptyList(),
                FetchVersion.ALL
            )
            // endregion
        );
    }

    private static NamespaceFileMetadata nsFileMetadata(String path) {
        return NamespaceFileMetadata.builder()
            .tenantId(TestsUtils.randomTenant())
            .namespace(TestsUtils.randomNamespace())
            .path(path)
            .size(1L)
            .last(false)
            .build();
    }

    @ParameterizedTest
    @MethodSource("filtersSource")
    void findWithFilters(String tenantId, List<NamespaceFileMetadata> initial, List<QueryFilter> queryFilters, List<NamespaceFileMetadata> expected, FetchVersion fetchVersion) {
        initial.forEach(namespaceFileMetadataRepositoryInterface::save);

        ArrayListTotal<NamespaceFileMetadata> result;
        if (fetchVersion == null) {
            result = namespaceFileMetadataRepositoryInterface.find(
                Pageable.unpaged(),
                tenantId,
                queryFilters,
                true
            );
        } else {
            result = namespaceFileMetadataRepositoryInterface.find(
                Pageable.unpaged(),
                tenantId,
                queryFilters,
                true,
                fetchVersion
            );
        }

        Instant now = Instant.now();
        assertThat(result.stream().map(nsFileMetadata -> nsFileMetadata.toBuilder().created(now).updated(null).build()).toList())
            .containsExactlyInAnyOrderElementsOf(expected.stream().map(nsFileMetadata -> nsFileMetadata.toBuilder().created(now).updated(null).build()).toList());
    }

    @Test
    void purgeAllVersions() throws IOException {
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "test/ns/file";
        NamespaceFileMetadata metadata = NamespaceFileMetadata.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .path(path)
            .size(1L)
            .build();

        assertThat(metadata.getVersion()).isNull();
        assertThat(namespaceFileMetadataRepositoryInterface.save(metadata).getVersion()).isEqualTo(1);
        metadata = namespaceFileMetadataRepositoryInterface.save(metadata);
        assertThat(metadata.getVersion()).isEqualTo(2);

        Integer purgedAmount = namespaceFileMetadataRepositoryInterface.purge(List.of(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId)
                .namespace(namespace)
                .path(path).build()
        ));

        assertThat(purgedAmount).isEqualTo(2);

        assertThat(namespaceFileMetadataRepositoryInterface.findByPath(tenantId, namespace, path).isPresent()).isFalse();
    }
}
