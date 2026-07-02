package io.kestra.worker.stores;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.controller.grpc.NamespaceFileMetadataServiceGrpc.NamespaceFileMetadataServiceBlockingStub;
import io.kestra.core.junit.annotations.KestraTest;
import io.micronaut.context.annotation.Property;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.namespace.NamespaceFileMetadataStateStore;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@Property(name = "test.context.id", value = "grpc-ns-file")
class GrpcWorkerNamespaceFileMetadataStateStoreTest extends AbstractGrpcMetaStoreTest {

    @Inject
    NamespaceFileMetadataServiceBlockingStub nsMetadataStub;

    @Inject
    NamespaceFileMetadataStateStore nsStateStore;

    private GrpcWorkerNamespaceFileMetadataStateStore grpcWorkerNsStore;

    @Override
    protected void initClientStore() {
        grpcWorkerNsStore = new GrpcWorkerNamespaceFileMetadataStateStore(nsMetadataStub, clientWorkerInfo());
    }

    @Test
    void shouldReturnMetadataWhenFindByPathGivenExistingEntry() throws IOException {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/scripts/main.py").size(42L).build()
        );

        // When
        Optional<NamespaceFileMetadata> result = grpcWorkerNsStore.findByPath(tenantId, namespace, "/scripts/main.py", null, false);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getTenantId()).isEqualTo(tenantId);
        assertThat(result.get().getNamespace()).isEqualTo(namespace);
        assertThat(result.get().getPath()).isEqualTo("/scripts/main.py");
        assertThat(result.get().getSize()).isEqualTo(42L);
        assertThat(result.get().getRevision()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyWhenFindByPathGivenAbsentEntry() throws IOException {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        // When
        Optional<NamespaceFileMetadata> result = grpcWorkerNsStore.findByPath(tenantId, namespace, "/absent.py", null, false);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnSpecificVersionWhenFindByPathGivenVersionNumber() throws IOException {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "/versioned.py";
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path(path).size(10L).build()
        );
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path(path).size(20L).build()
        );

        // When
        Optional<NamespaceFileMetadata> result = grpcWorkerNsStore.findByPath(tenantId, namespace, path, 1, false);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getRevision()).isEqualTo(1);
    }

    @Test
    void shouldReturnDeletedWhenFindByPathGivenAllowDeletedTrue() throws IOException {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "/deleted.py";
        NamespaceFileMetadata saved = nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path(path).size(10L).build()
        );
        nsStateStore.delete(saved);

        // When
        Optional<NamespaceFileMetadata> withoutDeleted = grpcWorkerNsStore.findByPath(tenantId, namespace, path, null, false);
        Optional<NamespaceFileMetadata> withDeleted = grpcWorkerNsStore.findByPath(tenantId, namespace, path, null, true);

        // Then
        assertThat(withoutDeleted).isEmpty();
        assertThat(withDeleted).isPresent();
        assertThat(withDeleted.get().isDeleted()).isTrue();
    }

    @Test
    void shouldReturnDirectChildrenWhenFindChildrenGivenNonRecursive() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/parent/").size(0L).build()
        );
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/parent/a.py").size(10L).build()
        );
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/parent/b.py").size(20L).build()
        );
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/parent/sub/").size(0L).build()
        );
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/parent/sub/deep.py").size(30L).build()
        );

        // When
        List<NamespaceFileMetadata> result = grpcWorkerNsStore.findChildren(tenantId, namespace, "/parent/", false);

        // Then
        assertThat(result).extracting(NamespaceFileMetadata::getPath)
            .containsExactlyInAnyOrder("/parent/a.py", "/parent/b.py", "/parent/sub/");
    }

    @Test
    void shouldReturnAllDescendantsWhenFindChildrenGivenRecursive() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/dir/").size(0L).build()
        );
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/dir/a.py").size(10L).build()
        );
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/dir/sub/").size(0L).build()
        );
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/dir/sub/b.py").size(20L).build()
        );

        // When
        List<NamespaceFileMetadata> result = grpcWorkerNsStore.findChildren(tenantId, namespace, "/dir/", true);

        // Then
        assertThat(result).extracting(NamespaceFileMetadata::getPath)
            .containsExactlyInAnyOrder("/dir/a.py", "/dir/sub/", "/dir/sub/b.py");
    }

    @Test
    void shouldReturnAllFilesWhenFindAllGivenNullContaining() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/a.py").size(10L).build()
        );
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/b.py").size(20L).build()
        );

        // When
        List<NamespaceFileMetadata> result = grpcWorkerNsStore.findAll(tenantId, namespace, null);

        // Then
        assertThat(result).extracting(NamespaceFileMetadata::getPath)
            .containsExactlyInAnyOrder("/a.py", "/b.py");
    }

    @Test
    void shouldFilterBySubstringWhenFindAllGivenContainingValue() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/scripts/deploy.sh").size(10L).build()
        );
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/config/app.yml").size(20L).build()
        );

        // When
        List<NamespaceFileMetadata> result = grpcWorkerNsStore.findAll(tenantId, namespace, "deploy");

        // Then
        assertThat(result).extracting(NamespaceFileMetadata::getPath)
            .containsExactly("/scripts/deploy.sh");
    }

    @Test
    void shouldReturnMatchingEntriesWhenFindByPathsGivenMultiplePaths() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/one.py").size(10L).build()
        );
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/two.py").size(20L).build()
        );
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/three.py").size(30L).build()
        );

        // When
        List<NamespaceFileMetadata> result = grpcWorkerNsStore.findByPaths(
            tenantId, namespace,
            List.of("/one.py", "/three.py"), false
        );

        // Then
        assertThat(result).extracting(NamespaceFileMetadata::getPath)
            .containsExactlyInAnyOrder("/one.py", "/three.py");
    }

    // -- findAllVersionsByPaths --

    @Test
    void shouldReturnAllVersionsWhenFindAllVersionsByPathsGivenMultipleVersions() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "/multi-version.py";
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path(path).size(10L).build()
        );
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path(path).size(20L).build()
        );

        // When
        List<NamespaceFileMetadata> result = grpcWorkerNsStore.findAllVersionsByPaths(tenantId, namespace, List.of(path));

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(NamespaceFileMetadata::getRevision).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void shouldReturnTrueWhenExistsByNamespaceGivenActiveFiles() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        nsStateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/exists.py").size(10L).build()
        );

        // When / Then
        assertThat(grpcWorkerNsStore.existsByNamespace(tenantId, namespace)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenExistsByNamespaceGivenEmptyNamespace() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        // When / Then
        assertThat(grpcWorkerNsStore.existsByNamespace(tenantId, namespace)).isFalse();
    }

    @Test
    void shouldRoundTripMetadataWhenSaveGivenValidEntry() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        NamespaceFileMetadata input = NamespaceFileMetadata.builder()
            .tenantId(tenantId).namespace(namespace).path("/saved.py").size(42L).build();

        // When
        NamespaceFileMetadata result = grpcWorkerNsStore.save(input);

        // Then
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getNamespace()).isEqualTo(namespace);
        assertThat(result.getPath()).isEqualTo("/saved.py");
        assertThat(result.getSize()).isEqualTo(42L);
        assertThat(result.getRevision()).isEqualTo(1);
    }

    @Test
    void shouldPreserveDirectoryPathWhenSaveGivenDirectoryEntry() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        NamespaceFileMetadata dirEntry = NamespaceFileMetadata.builder()
            .tenantId(tenantId).namespace(namespace).path("/scripts/").size(0L).build();

        // When
        NamespaceFileMetadata result = grpcWorkerNsStore.save(dirEntry);

        // Then
        assertThat(result.getPath()).isEqualTo("/scripts/");
        assertThat(result.isDirectory()).isTrue();
    }
}
