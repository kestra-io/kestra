package io.kestra.core.storages;

import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.PathMatcherPredicate;
import io.kestra.storage.local.LocalStorage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class InternalNamespaceTest {

    private static final Logger logger = LoggerFactory.getLogger(InternalNamespaceTest.class);

    LocalStorage storageInterface;

    @BeforeEach
    public void setUp() throws IOException {
        Path basePath = Files.createTempDirectory("unit");
        storageInterface = new LocalStorage();
        storageInterface.setBasePath(basePath);
        storageInterface.init();
    }

    @Test
    void shouldGetAllNamespaceFiles() throws IOException {
        // Given
        final String namespaceId = "io.kestra." + IdUtils.create();
        final InternalNamespace namespace = new InternalNamespace(logger, null, namespaceId, storageInterface);

        // When
        namespace.putFile(Path.of("/sub/dir/file1.txt"), new ByteArrayInputStream("1".getBytes()));
        namespace.putFile(Path.of("/sub/dir/file2.txt"), new ByteArrayInputStream("2".getBytes()));
        namespace.putFile(Path.of("/sub/dir/file3.txt"), new ByteArrayInputStream("3".getBytes()));

        // Then
        assertThat(namespace.all(), containsInAnyOrder(
            is(NamespaceFile.of(namespaceId, Path.of("sub/dir/file1.txt"))),
            is(NamespaceFile.of(namespaceId, Path.of("sub/dir/file2.txt"))),
            is(NamespaceFile.of(namespaceId, Path.of("sub/dir/file3.txt")))
        ));
    }

    @Test
    void shouldPutFileGivenNoTenant() throws IOException {
        // Given
        final String namespaceId = "io.kestra." + IdUtils.create();
        final InternalNamespace namespace = new InternalNamespace(logger, null, namespaceId, storageInterface);

        // When
        NamespaceFile namespaceFile = namespace.putFile(Path.of("/sub/dir/file.txt"), new ByteArrayInputStream("1".getBytes()));

        // Then
        assertThat(namespaceFile, is(NamespaceFile.of(namespaceId, Path.of("sub/dir/file.txt"))));
        // Then
        try (InputStream is  = namespace.getFileContent(namespaceFile.path())) {
            assertThat(new String(is.readAllBytes()), is("1"));
        }
    }

    @Test
    void shouldSucceedPutFileGivenExistingFileForConflictOverwrite() throws IOException {
        // Given
        final String namespaceId = "io.kestra." + IdUtils.create();
        final InternalNamespace namespace = new InternalNamespace(logger, null, namespaceId, storageInterface);

        NamespaceFile namespaceFile = namespace.get(Path.of("/sub/dir/file.txt"));

        namespace.putFile(namespaceFile.path(), new ByteArrayInputStream("1".getBytes()));

        // When
        namespace.putFile(namespaceFile.path(), new ByteArrayInputStream("2".getBytes()), Namespace.Conflicts.OVERWRITE);

        // Then
        try (InputStream is  = namespace.getFileContent(namespaceFile.path())) {
            assertThat(new String(is.readAllBytes()), is("2"));
        }
    }

    @Test
    void shouldFailPutFileGivenExistingFileForError() throws IOException {
        // Given
        final String namespaceId = "io.kestra." + IdUtils.create();
        final InternalNamespace namespace = new InternalNamespace(logger, null, namespaceId, storageInterface);

        NamespaceFile namespaceFile = namespace.get(Path.of("/sub/dir/file.txt"));

        namespace.putFile(namespaceFile.path(), new ByteArrayInputStream("1".getBytes()));

        // When - Then
        Assertions.assertThrows(
            IOException.class,
            () -> namespace.putFile(namespaceFile.path(), new ByteArrayInputStream("2".getBytes()), Namespace.Conflicts.ERROR)
        );
    }

    @Test
    void shouldIgnorePutFileGivenExistingFileForSkip() throws IOException {
        // Given
        final String namespaceId = "io.kestra." + IdUtils.create();
        final InternalNamespace namespace = new InternalNamespace(logger, null, namespaceId, storageInterface);

        NamespaceFile namespaceFile = namespace.get(Path.of("/sub/dir/file.txt"));

        namespace.putFile(namespaceFile.path(), new ByteArrayInputStream("1".getBytes()));

        // When
        namespace.putFile(namespaceFile.path(), new ByteArrayInputStream("2".getBytes()), Namespace.Conflicts.SKIP);

        // Then
        try (InputStream is  = namespace.getFileContent(namespaceFile.path())) {
            assertThat(new String(is.readAllBytes()), is("1"));
        }
    }

    @Test
    void shouldFindAllMatchingGivenNoTenant() throws IOException {
        // Given
        final String namespaceId = "io.kestra." + IdUtils.create();
        final InternalNamespace namespace = new InternalNamespace(logger, null, namespaceId, storageInterface);

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
        assertThat(namespaceFiles.stream().map(NamespaceFile::path).toList(), containsInAnyOrder(
            is(Path.of("a/b/c/1.sql")),
            is(Path.of("b/c/d/3.sql")),
            is(Path.of("c/5.sql"))
        ));
    }

    @Test
    void shouldFindAllGivenTenant() throws IOException {
        // Given
        final String namespaceId = "io.kestra." + IdUtils.create();
        final InternalNamespace namespaceTenant1 = new InternalNamespace(logger, "tenant1", namespaceId, storageInterface);
        NamespaceFile namespaceFile1 = namespaceTenant1.putFile(Path.of("/a/b/c/test.txt"), new ByteArrayInputStream("1".getBytes()));

        final InternalNamespace namespaceTenant2 = new InternalNamespace(logger, "tenant2", namespaceId, storageInterface);
        NamespaceFile namespaceFile2 = namespaceTenant2.putFile(Path.of("/a/b/c/test.txt"), new ByteArrayInputStream("1".getBytes()));

        // When - Then
        List<NamespaceFile> allTenant1 = namespaceTenant1.all();
        assertThat(allTenant1.size(), is(1));
        assertThat(allTenant1, containsInAnyOrder(is(namespaceFile1)));

        List<NamespaceFile> allTenant2 = namespaceTenant2.all();
        assertThat(allTenant2.size(), is(1));
        assertThat(allTenant2, containsInAnyOrder(is(namespaceFile2)));
    }

    @Test
    void shouldReturnNoNamespaceFileForEmptyNamespace() throws IOException {
        // Given
        final String namespaceId = "io.kestra." + IdUtils.create();
        final InternalNamespace namespace = new InternalNamespace(logger, null, namespaceId, storageInterface);
        List<NamespaceFile> namespaceFiles = namespace.findAllFilesMatching((unused) -> true);
        assertThat(namespaceFiles.size(), is(0));
    }
}