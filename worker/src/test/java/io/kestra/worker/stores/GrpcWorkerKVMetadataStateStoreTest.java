package io.kestra.worker.stores;

import io.kestra.controller.grpc.KVMetadataServiceGrpc.KVMetadataServiceBlockingStub;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.runners.KVMetadataStateStore;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class GrpcWorkerKVMetadataStateStoreTest extends AbstractGrpcMetaStoreTest {

    @Inject
    KVMetadataServiceBlockingStub kvMetadataStub;

    @Inject
    KVMetadataStateStore kvStateStore;

    private GrpcWorkerKVMetadataStateStore grpcWorkerKvStore;

    @Override
    protected void initClientStore() {
        grpcWorkerKvStore = new GrpcWorkerKVMetadataStateStore(kvMetadataStub, clientWorkerInfo());
    }

    @Test
    void shouldReturnMetadataWhenFindByNameGivenExistingEntry() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        kvStateStore.save(PersistedKvMetadata.builder()
            .tenantId(tenantId).namespace(namespace).name("myKey").version(1)
            .created(Instant.now()).build());

        // When
        Optional<PersistedKvMetadata> result = grpcWorkerKvStore.findByName(tenantId, namespace, "myKey");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getTenantId()).isEqualTo(tenantId);
        assertThat(result.get().getNamespace()).isEqualTo(namespace);
        assertThat(result.get().getName()).isEqualTo("myKey");
        assertThat(result.get().getVersion()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyWhenFindByNameGivenAbsentEntry() throws IOException {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        // When
        Optional<PersistedKvMetadata> result = grpcWorkerKvStore.findByName(tenantId, namespace, "absent");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnAllEntriesWhenFindGivenNamespaceWithEntries() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        kvStateStore.save(PersistedKvMetadata.builder()
            .tenantId(tenantId).namespace(namespace).name("key1").version(1)
            .created(Instant.now()).build());
        kvStateStore.save(PersistedKvMetadata.builder()
            .tenantId(tenantId).namespace(namespace).name("key2").version(1)
            .created(Instant.now()).build());

        // When
        List<PersistedKvMetadata> result = grpcWorkerKvStore.find(tenantId, namespace);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(PersistedKvMetadata::getName).containsExactlyInAnyOrder("key1", "key2");
    }

    @Test
    void shouldReturnEmptyListWhenFindGivenEmptyNamespace() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        // When
        List<PersistedKvMetadata> result = grpcWorkerKvStore.find(tenantId, namespace);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnTrueWhenExistsByNamespaceGivenActiveEntries() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        kvStateStore.save(PersistedKvMetadata.builder()
            .tenantId(tenantId).namespace(namespace).name("key").version(1)
            .created(Instant.now()).build());

        // When / Then
        assertThat(grpcWorkerKvStore.existsByNamespace(tenantId, namespace)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenExistsByNamespaceGivenEmptyNamespace() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        // When / Then
        assertThat(grpcWorkerKvStore.existsByNamespace(tenantId, namespace)).isFalse();
    }

    @Test
    void shouldRoundTripMetadataWhenSaveGivenValidEntry() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        PersistedKvMetadata input = PersistedKvMetadata.builder()
            .tenantId(tenantId).namespace(namespace).name("saved-key").version(1)
            .created(Instant.now()).build();

        // When
        PersistedKvMetadata result = grpcWorkerKvStore.save(input);

        // Then
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getNamespace()).isEqualTo(namespace);
        assertThat(result.getName()).isEqualTo("saved-key");
    }

    @Test
    void shouldSoftDeleteWhenDeleteGivenActiveEntry() throws IOException {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        PersistedKvMetadata saved = kvStateStore.save(PersistedKvMetadata.builder()
            .tenantId(tenantId).namespace(namespace).name("to-delete").version(1)
            .created(Instant.now()).build());

        // When
        PersistedKvMetadata deleted = grpcWorkerKvStore.delete(saved);

        // Then
        assertThat(deleted).isNotNull();
        assertThat(deleted.getName()).isEqualTo("to-delete");
        assertThat(deleted.isDeleted()).isTrue();
    }

    @Test
    void shouldPreserveExpirationDateWhenSaveGivenEntryWithExpiration() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        Instant expiration = Instant.parse("2026-12-31T23:59:59Z");
        PersistedKvMetadata input = PersistedKvMetadata.builder()
            .tenantId(tenantId).namespace(namespace).name("expiring").version(1)
            .expirationDate(expiration).created(Instant.now()).build();

        // When
        PersistedKvMetadata result = grpcWorkerKvStore.save(input);

        // Then
        assertThat(result.getExpirationDate()).isEqualTo(expiration);
    }
}
