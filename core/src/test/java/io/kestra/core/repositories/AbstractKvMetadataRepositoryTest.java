package io.kestra.core.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@MicronautTest
public abstract class AbstractKvMetadataRepositoryTest {
    @Inject
    protected KvMetadataRepositoryInterface kvMetadataRepositoryInterface;

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void should_find_all_with_source(QueryFilter filter) {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        PersistedKvMetadata metadata = buildTestKvDescription(tenant, "namespace", "key");
        kvMetadataRepositoryInterface.save(metadata);

        ArrayListTotal<PersistedKvMetadata> persistedMetadata = kvMetadataRepositoryInterface.find(
            Pageable.UNPAGED, tenant, List.of(filter), false, true);

        assertThat(persistedMetadata).hasSize(1);
        assertThat(persistedMetadata.getFirst().getName()).isEqualTo(metadata.getName());
    }

    static Stream<QueryFilter> filterCombinations() {
        return Stream.of(
            QueryFilter.builder().field(Field.QUERY).value("key").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.NAMESPACE).value("namespace").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.UPDATED).value(Instant.now().plusSeconds(10)).operation(Op.LESS_THAN_OR_EQUAL_TO).build(),
            QueryFilter.builder().field(Field.EXPIRATION_DATE).value(Instant.now()).operation(Op.GREATER_THAN_OR_EQUAL_TO).build()
        );
    }

    @Test
    void findKvMetadataByName() throws IOException {
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String key = "test-kv";
        PersistedKvMetadata metadata = buildTestKvDescription(tenantId, namespace, key);

        kvMetadataRepositoryInterface.save(metadata);

        String changedDescription = "Changed description";
        kvMetadataRepositoryInterface.save(metadata.toBuilder().description(changedDescription).version(2).build());

        Optional<PersistedKvMetadata> found = kvMetadataRepositoryInterface.findByName(
            tenantId,
            namespace,
            key
        );

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo(key);
        assertThat(found.get().getDescription()).isEqualTo(changedDescription);
        assertThat(found.get().getVersion()).isEqualTo(2);
        assertThat(found.get().isLast()).isTrue();
        assertThat(found.get().isDeleted()).isFalse();
    }

    @Test
    void deleteMetadata() throws IOException {
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String key = "test-kv";
        PersistedKvMetadata metadata = PersistedKvMetadata.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .name(key)
            .version(1)
            .build();

        kvMetadataRepositoryInterface.save(metadata);

        Optional<PersistedKvMetadata> found = kvMetadataRepositoryInterface.findByName(
            tenantId,
            namespace,
            key
        );

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo(key);
        assertThat(found.get().isLast()).isTrue();
        assertThat(found.get().isDeleted()).isFalse();
        Instant beforeDeleteUpdateDate = found.get().getUpdated();

        kvMetadataRepositoryInterface.delete(found.get());

        found = kvMetadataRepositoryInterface.findByName(
            tenantId,
            namespace,
            key
        );

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo(key);
        // Soft delete
        assertThat(found.get().getVersion()).isEqualTo(1);
        assertThat(found.get().isLast()).isTrue();
        assertThat(found.get().isDeleted()).isTrue();
        assertThat(found.get().getUpdated()).isAfter(beforeDeleteUpdateDate);
    }

    @Test
    void findWithFilters() throws IOException {
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String key = "test-kv";
        String originalDescription = "Some description";
        PersistedKvMetadata metadata = PersistedKvMetadata.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .name(key)
            .description(originalDescription)
            .build();

        assertThat(metadata.getVersion()).isNull();
        assertThat(kvMetadataRepositoryInterface.save(metadata).getVersion()).isEqualTo(1);
        String changedDescription = "Changed description";
        metadata = kvMetadataRepositoryInterface.save(metadata.toBuilder().description(changedDescription).build());
        assertThat(metadata.getVersion()).isEqualTo(2);

        String anotherNamespace = TestsUtils.randomNamespace();
        String anotherNamespaceDeletedKey = "test-another-kv";
        String anotherNamespaceDescription = "Another namespace description";
        PersistedKvMetadata anotherMetadata = PersistedKvMetadata.builder()
            .tenantId(tenantId)
            .namespace(anotherNamespace)
            .name(anotherNamespaceDeletedKey)
            .description(anotherNamespaceDescription)
            .build();

        kvMetadataRepositoryInterface.save(anotherMetadata);
        kvMetadataRepositoryInterface.delete(anotherMetadata);

        String anotherNamespaceExpiredKey = "test-another-expired-kv";
        String anotherNamespaceExpiredDescription = "Another namespace expired description";
        PersistedKvMetadata anotherExpiredMetadata = PersistedKvMetadata.builder()
            .tenantId(tenantId)
            .namespace(anotherNamespace)
            .name(anotherNamespaceExpiredKey)
            .description(anotherNamespaceExpiredDescription)
            .expirationDate(Instant.now().minus(1, ChronoUnit.HOURS))
            .build();

        kvMetadataRepositoryInterface.save(anotherExpiredMetadata);

        // It will only retrieve latest versions by default
        ArrayListTotal<PersistedKvMetadata> found = kvMetadataRepositoryInterface.find(Pageable.from(1, 1), tenantId, Collections.emptyList(), true, true);
        assertThat(found).hasSize(1);
        assertThat(found.getTotal()).isEqualTo(3);

        // We get all versions if we put FetchVersion.ALL
        found = kvMetadataRepositoryInterface.find(Pageable.from(1, 10), tenantId, Collections.emptyList(), true, true, FetchVersion.ALL);
        assertThat(found).hasSize(4);
        assertThat(found.getTotal()).isEqualTo(4);
        List<PersistedKvMetadata> versionsForKey = found.stream().filter(kv -> kv.getName().equals(key)).toList();
        assertThat(versionsForKey.size()).isEqualTo(2);
        assertThat(versionsForKey.stream().map(PersistedKvMetadata::getVersion)).containsExactlyInAnyOrder(1, 2);
        assertThat(versionsForKey.stream().map(PersistedKvMetadata::getDescription)).containsExactlyInAnyOrder(originalDescription, changedDescription);

        // We get all versions but latest if we put FetchVersion.OLD
        found = kvMetadataRepositoryInterface.find(Pageable.from(1, 10), tenantId, Collections.emptyList(), true, true, FetchVersion.OLD);
        assertThat(found).hasSize(1);
        assertThat(found.getTotal()).isEqualTo(1);
        assertThat(found.getFirst().getDescription()).isEqualTo(originalDescription);
        assertThat(found.getFirst().getVersion()).isEqualTo(1);
        assertThat(found.getFirst().isLast()).isFalse();


        found = kvMetadataRepositoryInterface.find(
            Pageable.unpaged(),
            tenantId,
            List.of(QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(anotherNamespace).build()),
            true,
            true
        );
        assertThat(found.getTotal()).isEqualTo(2);
        assertThat(found.map(PersistedKvMetadata::getName)).containsExactlyInAnyOrder(anotherNamespaceDeletedKey, anotherNamespaceExpiredKey);

        found = kvMetadataRepositoryInterface.find(
            Pageable.unpaged(),
            tenantId,
            List.of(QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(anotherNamespace).build()),
            false,
            true
        );
        assertThat(found.getTotal()).isEqualTo(1);
        assertThat(found.getFirst().getName()).isEqualTo(anotherNamespaceExpiredKey);

        found = kvMetadataRepositoryInterface.find(
            Pageable.unpaged(),
            tenantId,
            List.of(QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(anotherNamespace).build()),
            true,
            false
        );
        assertThat(found.getTotal()).isEqualTo(1);
        assertThat(found.getFirst().getName()).isEqualTo(anotherNamespaceDeletedKey);

        found = kvMetadataRepositoryInterface.find(
            Pageable.unpaged(),
            tenantId,
            Collections.emptyList(),
            false,
            false
        );
        assertThat(found.getTotal()).isEqualTo(1);
        assertThat(found.getFirst().getName()).isEqualTo(key);
    }

    @Test
    void purgeAllVersions() throws IOException {
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String key = "test-kv";
        PersistedKvMetadata metadata = PersistedKvMetadata.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .name(key)
            .description("Some description")
            .build();

        assertThat(metadata.getVersion()).isNull();
        assertThat(kvMetadataRepositoryInterface.save(metadata).getVersion()).isEqualTo(1);
        String changedDescription = "Changed description";
        metadata = kvMetadataRepositoryInterface.save(metadata.toBuilder().description(changedDescription).build());
        assertThat(metadata.getVersion()).isEqualTo(2);

        Integer purgedAmount = kvMetadataRepositoryInterface.purge(List.of(
            PersistedKvMetadata.builder()
                .tenantId(tenantId)
                .namespace(namespace)
                .name(key).build()
        ));

        assertThat(purgedAmount).isEqualTo(2);

        assertThat(kvMetadataRepositoryInterface.findByName(tenantId, namespace, key).isPresent()).isFalse();
    }


    protected static PersistedKvMetadata buildTestKvDescription(String tenantId, String namespace, String key) {
        return PersistedKvMetadata.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .name(key)
            .description("Test kv description")
            .version(1)
            .expirationDate(Instant.now().plus(5, ChronoUnit.MINUTES))
            .build();
    }
}
