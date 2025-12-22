package io.kestra.core.storages;

import io.kestra.core.repositories.NamespaceFileMetadataRepositoryInterface;
import io.kestra.core.utils.PathMatcherPredicate;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class InternalNamespaceTest {

    private static final Logger logger = LoggerFactory.getLogger(InternalNamespaceTest.class);

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private NamespaceFileMetadataRepositoryInterface namespaceFileMetadataRepository;

    @Test
    void shouldGetAllNamespaceFiles() throws IOException, URISyntaxException {
        // Given
        final String namespaceId = TestsUtils.randomNamespace();
        final InternalNamespace namespace = new InternalNamespace(logger, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);

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
        final InternalNamespace namespace = new InternalNamespace(logger, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);

        // When
        List<NamespaceFile> namespaceFiles = namespace.putFile(Path.of("/sub/dir/file.txt"), new ByteArrayInputStream("1".getBytes()));

        // Then
        assertThat(namespaceFiles).containsExactlyInAnyOrder(NamespaceFile.of(namespaceId, "", 1), NamespaceFile.of(namespaceId, "sub/", 1), NamespaceFile.of(namespaceId, "sub/dir/", 1), NamespaceFile.of(namespaceId, "sub/dir/file.txt", 1));

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
        final InternalNamespace namespace = new InternalNamespace(logger, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);

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
        final InternalNamespace namespace = new InternalNamespace(logger, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);

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
        final InternalNamespace namespace = new InternalNamespace(logger, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);

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
        final InternalNamespace namespace = new InternalNamespace(logger, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);

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
        final InternalNamespace namespaceTenant1 = new InternalNamespace(logger, "tenant1", namespaceId, storageInterface, namespaceFileMetadataRepository);
        NamespaceFile namespaceFile1 = namespaceTenant1.putFile(Path.of("/a/b/c/test.txt"), new ByteArrayInputStream("1".getBytes())).stream()
            .filter(namespaceFile -> namespaceFile.path().endsWith("test.txt"))
            .findFirst().get();

        final InternalNamespace namespaceTenant2 = new InternalNamespace(logger, "tenant2", namespaceId, storageInterface, namespaceFileMetadataRepository);
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
        final InternalNamespace namespace = new InternalNamespace(logger, MAIN_TENANT, namespaceId, storageInterface, namespaceFileMetadataRepository);
        List<NamespaceFile> namespaceFiles = namespace.findAllFilesMatching((unused) -> true);
        assertThat(namespaceFiles.size()).isZero();
    }
}
