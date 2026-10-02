package io.kestra.core.namespace;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.NamespaceFileRevisionConflictException;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest(transactional = false)
public abstract class AbstractDefaultNamespaceFileMetadataStateStoreTest {
    @Inject
    protected NamespaceFileMetadataStateStore stateStore;

    @Test
    void shouldReturnLatestVersionWhenFindByPathGivenMultipleVersions() throws IOException {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "/test/file.txt";

        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId)
                .namespace(namespace)
                .path(path)
                .size(10L)
                .build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId)
                .namespace(namespace)
                .path(path)
                .size(20L)
                .build()
        );

        // When
        Optional<NamespaceFileMetadata> result = stateStore.findByPath(tenantId, namespace, path, null, false);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getRevision()).isEqualTo(2);
        assertThat(result.get().isLast()).isTrue();
        assertThat(result.get().isDeleted()).isFalse();
    }

    @Test
    void shouldReturnSpecificVersionWhenFindByPathGivenVersionNumber() throws IOException {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "/test/versioned.txt";

        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId)
                .namespace(namespace)
                .path(path)
                .size(10L)
                .build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId)
                .namespace(namespace)
                .path(path)
                .size(20L)
                .build()
        );

        // When
        Optional<NamespaceFileMetadata> result = stateStore.findByPath(tenantId, namespace, path, 1, false);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getRevision()).isEqualTo(1);
        assertThat(result.get().isLast()).isFalse();
    }

    @Test
    void shouldExcludeDeletedWhenFindByPathGivenAllowDeletedFalse() throws IOException {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "/test/deleted.txt";

        NamespaceFileMetadata saved = stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId)
                .namespace(namespace)
                .path(path)
                .size(10L)
                .build()
        );
        stateStore.delete(saved);

        // When
        Optional<NamespaceFileMetadata> withoutDeleted = stateStore.findByPath(tenantId, namespace, path, null, false);
        Optional<NamespaceFileMetadata> withDeleted = stateStore.findByPath(tenantId, namespace, path, null, true);

        // Then
        assertThat(withoutDeleted).isEmpty();
        assertThat(withDeleted).isPresent();
        assertThat(withDeleted.get().isDeleted()).isTrue();
    }

    @Test
    void shouldReturnEmptyWhenFindByPathGivenNonexistentPath() throws IOException {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        // When
        Optional<NamespaceFileMetadata> result = stateStore.findByPath(tenantId, namespace, "/nonexistent", null, false);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnDirectChildrenWhenFindChildrenGivenNonRecursive() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/parent/").size(0L).build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/parent/child1.txt").size(10L).build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/parent/child2.txt").size(20L).build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/parent/sub/").size(0L).build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/parent/sub/deep.txt").size(30L).build()
        );

        // When
        List<NamespaceFileMetadata> directChildren = stateStore.findChildren(tenantId, namespace, "/parent/", false);

        // Then
        assertThat(directChildren).extracting(NamespaceFileMetadata::getPath)
            .containsExactlyInAnyOrder("/parent/child1.txt", "/parent/child2.txt", "/parent/sub/");
    }

    @Test
    void shouldReturnAllDescendantsWhenFindChildrenGivenRecursive() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/dir/").size(0L).build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/dir/a.txt").size(10L).build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/dir/sub/").size(0L).build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/dir/sub/b.txt").size(20L).build()
        );

        // When
        List<NamespaceFileMetadata> allDescendants = stateStore.findChildren(tenantId, namespace, "/dir/", true);

        // Then
        assertThat(allDescendants).extracting(NamespaceFileMetadata::getPath)
            .containsExactlyInAnyOrder("/dir/a.txt", "/dir/sub/", "/dir/sub/b.txt");
    }

    @Test
    void shouldNormalizeParentPathWhenFindChildrenGivenPathWithoutTrailingSlash() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/norm/").size(0L).build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/norm/file.txt").size(10L).build()
        );

        // When
        List<NamespaceFileMetadata> result = stateStore.findChildren(tenantId, namespace, "/norm", false);

        // Then
        assertThat(result).extracting(NamespaceFileMetadata::getPath)
            .containsExactly("/norm/file.txt");
    }

    @Test
    void shouldReturnAllFilesWhenFindAllGivenNullContaining() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/a.txt").size(10L).build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/b.txt").size(20L).build()
        );

        // When
        List<NamespaceFileMetadata> result = stateStore.findAll(tenantId, namespace, null);

        // Then
        assertThat(result).extracting(NamespaceFileMetadata::getPath)
            .containsExactlyInAnyOrder("/a.txt", "/b.txt");
    }

    @Test
    public void shouldFilterBySubstringWhenFindAllGivenContainingValue() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/scripts/deploy.sh").size(10L).build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/config/app.yml").size(20L).build()
        );

        // When
        List<NamespaceFileMetadata> result = stateStore.findAll(tenantId, namespace, "deploy");

        // Then
        assertThat(result).extracting(NamespaceFileMetadata::getPath)
            .containsExactly("/scripts/deploy.sh");
    }

    @Test
    void shouldReturnAllFilesWhenFindAllGivenRootSlashAsContaining() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/x.txt").size(10L).build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/y.txt").size(20L).build()
        );

        // When
        List<NamespaceFileMetadata> result = stateStore.findAll(tenantId, namespace, "/");

        // Then
        assertThat(result).hasSize(2);
    }

    @Test
    void shouldReturnMatchingEntriesWhenFindByPathsGivenMultiplePaths() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/one.txt").size(10L).build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/two.txt").size(20L).build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/three.txt").size(30L).build()
        );

        // When
        List<NamespaceFileMetadata> result = stateStore.findByPaths(
            tenantId, namespace,
            List.of("/one.txt", "/three.txt"), false
        );

        // Then
        assertThat(result).extracting(NamespaceFileMetadata::getPath)
            .containsExactlyInAnyOrder("/one.txt", "/three.txt");
    }

    @Test
    void shouldExcludeDeletedWhenFindByPathsGivenAllowDeletedFalse() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        NamespaceFileMetadata saved = stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/del.txt").size(10L).build()
        );
        stateStore.save(saved.toDeleted());

        // When
        List<NamespaceFileMetadata> withoutDeleted = stateStore.findByPaths(tenantId, namespace, List.of("/del.txt"), false);
        List<NamespaceFileMetadata> withDeleted = stateStore.findByPaths(tenantId, namespace, List.of("/del.txt"), true);

        // Then
        assertThat(withoutDeleted).isEmpty();
        assertThat(withDeleted).hasSize(1);
        assertThat(withDeleted.getFirst().isDeleted()).isTrue();
    }

    @Test
    void shouldReturnAllVersionsWhenFindAllVersionsByPathsGivenMultipleVersions() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "/versioned.txt";

        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path(path).size(10L).build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path(path).size(20L).build()
        );

        // When
        List<NamespaceFileMetadata> result = stateStore.findAllVersionsByPaths(tenantId, namespace, List.of(path));

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(NamespaceFileMetadata::getRevision)
            .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void shouldReturnTrueWhenExistsByNamespaceGivenActiveFiles() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/exists.txt").size(10L).build()
        );

        // When / Then
        assertThat(stateStore.existsByNamespace(tenantId, namespace)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenExistsByNamespaceGivenEmptyNamespace() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        // When / Then
        assertThat(stateStore.existsByNamespace(tenantId, namespace)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenExistsByNamespaceGivenAllFilesDeleted() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        NamespaceFileMetadata saved = stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/gone.txt").size(10L).build()
        );
        stateStore.save(saved.toDeleted());

        // When / Then
        assertThat(stateStore.existsByNamespace(tenantId, namespace)).isFalse();
    }

    @Test
    void shouldIncrementVersionWhenSaveGivenSamePathSavedTwice() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "/incr.txt";

        // When
        NamespaceFileMetadata v1 = stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path(path).size(10L).build()
        );
        NamespaceFileMetadata v2 = stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path(path).size(20L).build()
        );

        // Then
        assertThat(v1.getRevision()).isEqualTo(1);
        assertThat(v2.getRevision()).isEqualTo(2);
    }

    @Test
    void shouldRetirePreviousRevisionWhenSaveRevisionGivenNextRevision() throws IOException {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "/claimed.txt";
        stateStore.saveRevision(NamespaceFileMetadata.builder().tenantId(tenantId).namespace(namespace).path(path).size(10L).build(), 1);

        // When
        stateStore.saveRevision(NamespaceFileMetadata.builder().tenantId(tenantId).namespace(namespace).path(path).size(20L).build(), 2);

        // Then
        assertThat(stateStore.findByPath(tenantId, namespace, path, null, false))
            .hasValueSatisfying(latest ->
            {
                assertThat(latest.getRevision()).isEqualTo(2);
                assertThat(latest.getSize()).isEqualTo(20L);
            });
        assertThat(stateStore.findByPath(tenantId, namespace, path, 1, false)).hasValueSatisfying(first -> assertThat(first.isLast()).isFalse());
    }

    @Test
    void shouldRecordARevisionOnceGivenConcurrentWriters() throws Exception {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "/contended.txt";
        int writers = 4;
        stateStore.saveRevision(NamespaceFileMetadata.builder().tenantId(tenantId).namespace(namespace).path(path).size(10L).build(), 1);

        // When
        ExecutorService executor = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<NamespaceFileMetadata>> saves = new ArrayList<>();
        try {
            for (int i = 0; i < writers; i++) {
                long size = 100L + i;
                saves.add(executor.submit(() ->
                {
                    start.await();
                    return stateStore.saveRevision(NamespaceFileMetadata.builder().tenantId(tenantId).namespace(namespace).path(path).size(size).build(), 2);
                }));
            }
            start.countDown();

            List<Long> recordedSizes = new ArrayList<>();
            int conflicts = 0;
            for (Future<NamespaceFileMetadata> save : saves) {
                try {
                    recordedSizes.add(save.get(30, TimeUnit.SECONDS).getSize());
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(NamespaceFileRevisionConflictException.class);
                    conflicts++;
                }
            }

            // Then
            assertThat(recordedSizes).hasSize(1);
            assertThat(conflicts).isEqualTo(writers - 1);
            assertThat(stateStore.findByPath(tenantId, namespace, path, null, false))
                .hasValueSatisfying(latest ->
                {
                    assertThat(latest.getRevision()).isEqualTo(2);
                    assertThat(latest.getSize()).isEqualTo(recordedSizes.getFirst());
                });
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldSoftDeleteWhenDeleteGivenActiveEntry() throws IOException {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "/soft.txt";

        NamespaceFileMetadata saved = stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path(path).size(10L).build()
        );

        // When
        NamespaceFileMetadata deleted = stateStore.delete(saved);

        // Then
        assertThat(deleted.isDeleted()).isTrue();

        Optional<NamespaceFileMetadata> found = stateStore.findByPath(tenantId, namespace, path, null, true);
        assertThat(found).isPresent();
        assertThat(found.get().isDeleted()).isTrue();

        assertThat(stateStore.findByPath(tenantId, namespace, path, null, false)).isEmpty();
    }

    @Test
    void shouldExcludeDeletedWhenFindChildrenGivenDeletedEntries() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/parent2/").size(0L).build()
        );
        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/parent2/alive.txt").size(10L).build()
        );
        NamespaceFileMetadata toDelete = stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/parent2/dead.txt").size(20L).build()
        );
        stateStore.save(toDelete.toDeleted());

        // When
        List<NamespaceFileMetadata> result = stateStore.findChildren(tenantId, namespace, "/parent2/", false);

        // Then
        assertThat(result).extracting(NamespaceFileMetadata::getPath)
            .containsExactly("/parent2/alive.txt");
    }

    @Test
    void shouldExcludeDeletedWhenFindAllGivenDeletedEntries() {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();

        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/alive2.txt").size(10L).build()
        );
        NamespaceFileMetadata toDelete = stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path("/dead2.txt").size(20L).build()
        );
        stateStore.save(toDelete.toDeleted());

        // When
        List<NamespaceFileMetadata> result = stateStore.findAll(tenantId, namespace, null);

        // Then
        assertThat(result).extracting(NamespaceFileMetadata::getPath)
            .containsExactly("/alive2.txt");
    }

    @Test
    void shouldReturnDeletedVersionWhenFindByPathGivenSpecificVersionAndAllowDeleted() throws IOException {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "/ver-del.txt";

        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path(path).size(10L).build()
        );
        NamespaceFileMetadata v2 = stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path(path).size(20L).build()
        );
        stateStore.delete(v2);

        // When
        Optional<NamespaceFileMetadata> result = stateStore.findByPath(tenantId, namespace, path, 2, true);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getRevision()).isEqualTo(2);
        assertThat(result.get().isDeleted()).isTrue();
    }

    @Test
    void shouldReturnEmptyWhenFindByPathGivenNonexistentVersion() throws IOException {
        // Given
        String tenantId = TestsUtils.randomTenant();
        String namespace = TestsUtils.randomNamespace();
        String path = "/ver-missing.txt";

        stateStore.save(
            NamespaceFileMetadata.builder()
                .tenantId(tenantId).namespace(namespace).path(path).size(10L).build()
        );

        // When
        Optional<NamespaceFileMetadata> result = stateStore.findByPath(tenantId, namespace, path, 99, false);

        // Then
        assertThat(result).isEmpty();
    }
}
