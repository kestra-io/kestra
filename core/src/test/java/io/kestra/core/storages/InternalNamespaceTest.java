package io.kestra.core.storages;

import io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface;
import io.kestra.core.utils.PathMatcherPredicate;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import org.apache.commons.lang3.tuple.Pair;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
@Slf4j
class InternalNamespaceTest {

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private NamespaceFileMetadataRepositoryInterface namespaceFileMetadataRepository;

    @Test
    void shouldGetAllNamespaceFiles() throws IOException, URISyntaxException {
        // Given
        final String namespaceId = TestsUtils.randomNamespace();
        final InternalNamespace namespace = new InternalNamespace(log, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);

        // When
        namespace.putFile(Path.of("/sub/dir/file1.txt"), new ByteArrayInputStream("1".getBytes()));
        namespace.putFile(Path.of("/sub/dir/file2.txt"), new ByteArrayInputStream("2".getBytes()));
        namespace.putFile(Path.of("/sub/dir/file3.txt"), new ByteArrayInputStream("3".getBytes()));

        // Then
        assertThat(namespace.all()).containsExactlyInAnyOrder(
            NamespaceFile.of(namespaceId, Path.of("sub/dir/file1.txt")),
            NamespaceFile.of(namespaceId, Path.of("sub/dir/file2.txt")),
            NamespaceFile.of(namespaceId, Path.of("sub/dir/file3.txt")));
    }

    @Test
    void shouldPutFileGivenNoTenant() throws IOException, URISyntaxException {
        // Given
        final String namespaceId = TestsUtils.randomNamespace();
        final InternalNamespace namespace = new InternalNamespace(log, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);

        // When
        List<NamespaceFile> namespaceFiles = namespace.putFile(Path.of("/sub/dir/file.txt"), new ByteArrayInputStream("1".getBytes()));

        // Then
        assertThat(namespaceFiles).containsExactlyInAnyOrder(
            NamespaceFile.of(namespaceId, "/", 1), 
            NamespaceFile.of(namespaceId, "sub/", 1),
            NamespaceFile.of(namespaceId, "sub/dir/", 1), 
            NamespaceFile.of(namespaceId, "sub/dir/file.txt", 1)
        );

        // Then
        NamespaceFile fileEntry = namespaceFiles.stream().filter(namespaceFile -> namespaceFile.path().endsWith("file.txt")).findFirst().get();
        try (InputStream is  = namespace.getFileContent(Path.of(fileEntry.path()))) {
            assertThat(new String(is.readAllBytes())).isEqualTo("1");
        }
    }

    @Test
    void shouldSucceedPutFileGivenExistingFileForConflictOverwrite() throws IOException, URISyntaxException {
        // Given
        final String namespaceId = TestsUtils.randomNamespace();
        final InternalNamespace namespace = new InternalNamespace(log, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);

        NamespaceFile namespaceFile = namespace.get(Path.of("/sub/dir/file.txt"));

        namespace.putFile(namespaceFile, new ByteArrayInputStream("1".getBytes()));

        // When
        namespace.putFile(namespaceFile, new ByteArrayInputStream("2".getBytes()), Namespace.Conflicts.OVERWRITE);

        // Then
        try (InputStream is  = namespace.getFileContent(Path.of(namespaceFile.path()))) {
            assertThat(new String(is.readAllBytes())).isEqualTo("2");
        }
    }

    @Test
    void shouldFailPutFileGivenExistingFileForError() throws IOException, URISyntaxException {
        // Given
        final String namespaceId = TestsUtils.randomNamespace();
        final InternalNamespace namespace = new InternalNamespace(log, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);

        NamespaceFile namespaceFile = namespace.get(Path.of("/sub/dir/file.txt"));

        namespace.putFile(namespaceFile, new ByteArrayInputStream("1".getBytes()));

        // When - Then
        Assertions.assertThrows(
            IOException.class,
            () -> namespace.putFile(namespaceFile, new ByteArrayInputStream("2".getBytes()), Namespace.Conflicts.ERROR)
        );
    }

    @Test
    void shouldIgnorePutFileGivenExistingFileForSkip() throws IOException, URISyntaxException {
        // Given
        final String namespaceId = TestsUtils.randomNamespace();
        final InternalNamespace namespace = new InternalNamespace(log, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);

        NamespaceFile namespaceFile = namespace.get(Path.of("/sub/dir/file.txt"));

        namespace.putFile(namespaceFile, new ByteArrayInputStream("1".getBytes()));

        // When
        namespace.putFile(namespaceFile, new ByteArrayInputStream("2".getBytes()), Namespace.Conflicts.SKIP);

        // Then
        try (InputStream is  = namespace.getFileContent(Path.of(namespaceFile.path()))) {
            assertThat(new String(is.readAllBytes())).isEqualTo("1");
        }
    }

    @Test
    void shouldFindAllMatchingGivenNoTenant() throws IOException, URISyntaxException {
        // Given
        final String namespaceId = TestsUtils.randomNamespace();
        final InternalNamespace namespace = new InternalNamespace(log, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);

        // When
        namespace.putFile(Path.of("/a/b/c/1.sql"), new ByteArrayInputStream("1".getBytes()));
        namespace.putFile(Path.of("/a/2.sql"), new ByteArrayInputStream("2".getBytes()));
        namespace.putFile(Path.of("/b/c/d/3.sql"), new ByteArrayInputStream("3".getBytes()));
        namespace.putFile(Path.of("/b/d/4.sql"), new ByteArrayInputStream("4".getBytes()));
        namespace.putFile(Path.of("/c/5.sql"), new ByteArrayInputStream("5".getBytes()));

        List<NamespaceFile> namespaceFiles = namespace.findAllFilesMatching(PathMatcherPredicate.builder()
            .includes(List.of("/a/**", "c/**"))
            .excludes(List.of("**/2.sql"))
            .build()
        );

        // Then
        assertThat(namespaceFiles.stream().map(NamespaceFile::path).toList()).containsExactlyInAnyOrder("a/b/c/1.sql", "b/c/d/3.sql", "c/5.sql");
    }

    @Test
    void shouldFindAllGivenTenant() throws IOException, URISyntaxException {
        // Given
        final String namespaceId = TestsUtils.randomNamespace();
        final InternalNamespace namespaceTenant1 = new InternalNamespace(log, "tenant1", namespaceId, storageInterface, namespaceFileMetadataRepository);
        NamespaceFile namespaceFile1 = namespaceTenant1.putFile(Path.of("/a/b/c/test.txt"), new ByteArrayInputStream("1".getBytes())).stream()
            .filter(namespaceFile -> namespaceFile.path().endsWith("test.txt"))
            .findFirst().get();

        final InternalNamespace namespaceTenant2 = new InternalNamespace(log, "tenant2", namespaceId, storageInterface, namespaceFileMetadataRepository);
        NamespaceFile namespaceFile2 = namespaceTenant2.putFile(Path.of("/a/b/c/test.txt"), new ByteArrayInputStream("1".getBytes())).stream()
            .filter(namespaceFile -> namespaceFile.path().endsWith("test.txt"))
            .findFirst().get();

        // When - Then
        List<NamespaceFile> allTenant1 = namespaceTenant1.all();
        assertThat(allTenant1.size()).isEqualTo(1);
        assertThat(allTenant1).containsExactlyInAnyOrder(namespaceFile1);

        List<NamespaceFile> allTenant2 = namespaceTenant2.all();
        assertThat(allTenant2.size()).isEqualTo(1);
        assertThat(allTenant2).containsExactlyInAnyOrder(namespaceFile2);
    }

    @Test
    void shouldReturnNoNamespaceFileForEmptyNamespace() throws IOException {
        // Given
        final String namespaceId = TestsUtils.randomNamespace();
        final InternalNamespace namespace = new InternalNamespace(log, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);
        List<NamespaceFile> namespaceFiles = namespace.findAllFilesMatching((unused) -> true);
        assertThat(namespaceFiles.size()).isZero();
    }
    
    @Test
    void shouldMoveFolderWithFilesIntoAnotherFolder() throws Exception {
        // Given: folder1 with 2 files, folder2 with 2 files
        final String namespaceId = TestsUtils.randomNamespace();
        final InternalNamespace namespace = new InternalNamespace(log, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);

        namespace.putFile(Path.of("/folder1/file1.txt"), new ByteArrayInputStream("content1".getBytes()));
        namespace.putFile(Path.of("/folder1/file2.txt"), new ByteArrayInputStream("content2".getBytes()));
        namespace.putFile(Path.of("/folder2/file3.txt"), new ByteArrayInputStream("content3".getBytes()));
        namespace.putFile(Path.of("/folder2/file4.txt"), new ByteArrayInputStream("content4".getBytes()));

        // When: move folder2 into folder1
        List<Pair<NamespaceFile, NamespaceFile>> moved = namespace.move(Path.of("/folder2"), Path.of("/folder1/folder2"));

        // Then: folder2 and its files were moved
        assertThat(moved).isNotEmpty();

        // folder1's original files are untouched
        assertThat(namespace.exists(Path.of("/folder1/file1.txt"))).isTrue();
        assertThat(namespace.exists(Path.of("/folder1/file2.txt"))).isTrue();
        try (InputStream is = namespace.getFileContent(Path.of("/folder1/file1.txt"))) {
            assertThat(new String(is.readAllBytes())).isEqualTo("content1");
        }
        try (InputStream is = namespace.getFileContent(Path.of("/folder1/file2.txt"))) {
            assertThat(new String(is.readAllBytes())).isEqualTo("content2");
        }

        // folder2 now exists as a nested directory inside folder1
        assertThat(namespace.exists(Path.of("/folder1/folder2"))).isTrue();

        // folder2's files are accessible at the new paths with correct content
        assertThat(namespace.exists(Path.of("/folder1/folder2/file3.txt"))).isTrue();
        assertThat(namespace.exists(Path.of("/folder1/folder2/file4.txt"))).isTrue();
        try (InputStream is = namespace.getFileContent(Path.of("/folder1/folder2/file3.txt"))) {
            assertThat(new String(is.readAllBytes())).isEqualTo("content3");
        }
        try (InputStream is = namespace.getFileContent(Path.of("/folder1/folder2/file4.txt"))) {
            assertThat(new String(is.readAllBytes())).isEqualTo("content4");
        }

        // folder2 no longer exists at the old location
        assertThat(namespace.exists(Path.of("/folder2"))).isFalse();
        assertThat(namespace.exists(Path.of("/folder2/file3.txt"))).isFalse();
        assertThat(namespace.exists(Path.of("/folder2/file4.txt"))).isFalse();
    }

    @Test
    void shouldRollbackMoveWhenCopyFails() throws Exception {
        // Given: folder1 with 2 files, folder2 with 2 files
        final String namespaceId = TestsUtils.randomNamespace();
        final InternalNamespace namespace = new InternalNamespace(log, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);

        namespace.putFile(Path.of("/folder1/file1.txt"), new ByteArrayInputStream("content1".getBytes()));
        namespace.putFile(Path.of("/folder1/file2.txt"), new ByteArrayInputStream("content2".getBytes()));
        namespace.putFile(Path.of("/folder2/file3.txt"), new ByteArrayInputStream("content3".getBytes()));
        List<NamespaceFile> file4Result = namespace.putFile(Path.of("/folder2/file4.txt"), new ByteArrayInputStream("content4".getBytes()));

        // Corrupt file4 in the underlying storage so that reading it during move will fail
        NamespaceFile file4 = file4Result.stream().filter(f -> f.path().endsWith("file4.txt")).findFirst().orElseThrow();
        storageInterface.delete(MAIN_TENANT, namespaceId, file4.storagePath().toUri());

        // When: move folder2 into folder1 — should fail because file4.txt can't be read
        Assertions.assertThrows(IOException.class, () ->
            namespace.move(Path.of("/folder2"), Path.of("/folder1/folder2"))
        );

        // Then: rollback should have cleaned up any partially-created target entries
        assertThat(namespace.exists(Path.of("/folder1/folder2"))).isFalse();
        assertThat(namespace.exists(Path.of("/folder1/folder2/file3.txt"))).isFalse();
        assertThat(namespace.exists(Path.of("/folder1/folder2/file4.txt"))).isFalse();

        // Source files are still intact at original locations
        assertThat(namespace.exists(Path.of("/folder2"))).isTrue();
        assertThat(namespace.exists(Path.of("/folder2/file3.txt"))).isTrue();

        // folder1's original files are untouched
        assertThat(namespace.exists(Path.of("/folder1/file1.txt"))).isTrue();
        assertThat(namespace.exists(Path.of("/folder1/file2.txt"))).isTrue();
        try (InputStream is = namespace.getFileContent(Path.of("/folder1/file1.txt"))) {
            assertThat(new String(is.readAllBytes())).isEqualTo("content1");
        }
        try (InputStream is = namespace.getFileContent(Path.of("/folder1/file2.txt"))) {
            assertThat(new String(is.readAllBytes())).isEqualTo("content2");
        }
    }

    @Test
    void shouldCreateDirectory() throws IOException {
        // Given
        final String namespaceId = TestsUtils.randomNamespace();
        final InternalNamespace namespace = new InternalNamespace(log, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);

        // When
        NamespaceFile directory = namespace.createDirectory(Path.of("my-directory"));

        // Then
        assertThat(directory.isDirectory()).isTrue();
        assertThat(directory.uri().toString()).matches(uri -> uri.endsWith("my-directory/"));
    }
}
