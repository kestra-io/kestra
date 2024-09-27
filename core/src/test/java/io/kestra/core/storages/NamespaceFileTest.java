package io.kestra.core.storages;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;

class NamespaceFileTest {

    private static final String NAMESPACE = "io.kestra.test";

    @Test
    void shouldReturnTrueForIsRootDirectoryGivenRootDirectory() {
        Assertions.assertTrue(NamespaceFile.of(NAMESPACE, URI.create("/")).isRootDirectory());
    }
    @Test
    void shouldReturnFalseForIsRootDirectoryGivenNonRootDirectory() {
        Assertions.assertFalse(NamespaceFile.of(NAMESPACE, URI.create("/my/sub/dir")).isRootDirectory());
    }

    @Test
    void shouldCreateValidNamespaceFileGivenSlashURI() {
        NamespaceFile expected = new NamespaceFile(
            Path.of(""),
            URI.create("kestra:///io/kestra/test/_files/"),
            NAMESPACE
        );

        // Given URI
        Assertions.assertEquals(expected, NamespaceFile.of(NAMESPACE, URI.create("/")));

        // Given Path
        Assertions.assertEquals(expected, NamespaceFile.of(NAMESPACE, Path.of("/"))
        );
    }

    @Test
    void shouldThrowExceptionGivenNullNamespace() {
        Assertions.assertThrows(NullPointerException.class, () -> NamespaceFile.of(null, (Path) null));
    }

    @Test
    void shouldThrowExceptionGivenInvalidScheme() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> NamespaceFile.of(NAMESPACE, URI.create("file:///io/kestra/test/_files/sub/dir/file.txt")));
    }

    @Test
    void shouldThrowExceptionGivenInvalidNamespace() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> NamespaceFile.of(NAMESPACE, URI.create("kestra:///com/acme/_files/sub/dir/file.txt")));
    }

    @Test
    void shouldCreateGivenNamespaceAndValidStorageURI() {
        Assertions.assertEquals(new NamespaceFile(
                Path.of("sub/dir/file.txt"),
                URI.create("kestra:///io/kestra/test/_files/sub/dir/file.txt"),
            NAMESPACE
            ), NamespaceFile.of(NAMESPACE, URI.create("kestra:///io/kestra/test/_files/sub/dir/file.txt"))
        );
    }

    @Test
    void shouldCreateGivenNamespaceAndValidRelativeURI() {
        Assertions.assertEquals(new NamespaceFile(
                Path.of("sub/dir/file.txt"),
                URI.create("kestra:///io/kestra/test/_files/sub/dir/file.txt"),
            NAMESPACE
            ), NamespaceFile.of(NAMESPACE, URI.create("/sub/dir/file.txt"))
        );
    }

    @Test
    void shouldCreateGivenNamespaceAndPath() {
        NamespaceFile expected = new NamespaceFile(
            Path.of("sub/dir/file.txt"),
            URI.create("kestra:///io/kestra/test/_files/sub/dir/file.txt"),
            NAMESPACE
        );

        Assertions.assertEquals(expected, NamespaceFile.of(NAMESPACE, Path.of("sub/dir/file.txt")));
        Assertions.assertEquals(expected, NamespaceFile.of(NAMESPACE, Path.of("/sub/dir/file.txt")));
        Assertions.assertEquals(expected, NamespaceFile.of(NAMESPACE, Path.of("./sub/dir/file.txt")));
    }

    @Test
    void shouldCreateGivenNamespaceAndNullPath() {
        Assertions.assertEquals(new NamespaceFile(
                Path.of(""),
                URI.create("kestra:///io/kestra/test/_files/"),
            NAMESPACE
            ), NamespaceFile.of(NAMESPACE)
        );
    }

    @Test
    void shouldCreateGivenNamespaceAndRootPath() {
        Assertions.assertEquals(new NamespaceFile(
                Path.of(""),
                URI.create("kestra:///io/kestra/test/_files/"),
            NAMESPACE
            ), NamespaceFile.of(NAMESPACE, Path.of("/"))
        );
    }

    @Test
    void shouldGetStoragePath() {
        NamespaceFile namespaceFile = new NamespaceFile(
            Path.of("sub/dir/file.txt"),
            URI.create("kestra:///io/kestra/test/_files/sub/dir/file.txt"),
            NAMESPACE
        );
        Assertions.assertEquals(Path.of("/io/kestra/test/_files/sub/dir/file.txt"), namespaceFile.storagePath());
    }

    @Test
    void shouldPreserveTrailingSlashForUri() {
        NamespaceFile namespaceFile = NamespaceFile.of(NAMESPACE, URI.create("/sub/dir/"));
        Assertions.assertEquals(new NamespaceFile(
                Path.of("sub/dir"),
                URI.create("kestra:///io/kestra/test/_files/sub/dir/"),
            NAMESPACE
            ), namespaceFile
        );
        Assertions.assertTrue(namespaceFile.isDirectory());
    }
}