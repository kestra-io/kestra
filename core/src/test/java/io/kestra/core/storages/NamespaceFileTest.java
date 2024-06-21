package io.kestra.core.storages;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;

class NamespaceFileTest {

    private static final String namespace = "io.kestra.test";

    @Test
    void shouldThrowExceptionGivenNullNamespace() {
        Assertions.assertThrows(NullPointerException.class, () -> NamespaceFile.of(null, (Path) null));
    }

    @Test
    void shouldThrowExceptionGivenInvalidScheme() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> NamespaceFile.of(namespace, URI.create("file:///io/kestra/test/_files/sub/dir/file.txt")));
    }

    @Test
    void shouldThrowExceptionGivenInvalidNamespace() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> NamespaceFile.of(namespace, URI.create("kestra:///com/acme/_files/sub/dir/file.txt")));
    }

    @Test
    void shouldCreateGivenNamespaceAndValidStorageURI() {
        Assertions.assertEquals(new NamespaceFile(
                Path.of("sub/dir/file.txt"),
                URI.create("kestra:///io/kestra/test/_files/sub/dir/file.txt"),
                namespace
            ), NamespaceFile.of(namespace, URI.create("kestra:///io/kestra/test/_files/sub/dir/file.txt"))
        );
    }

    @Test
    void shouldCreateGivenNamespaceAndValidRelativeURI() {
        Assertions.assertEquals(new NamespaceFile(
                Path.of("sub/dir/file.txt"),
                URI.create("kestra:///io/kestra/test/_files/sub/dir/file.txt"),
                namespace
            ), NamespaceFile.of(namespace, URI.create("/sub/dir/file.txt"))
        );
    }

    @Test
    void shouldCreateGivenNamespaceAndPath() {
        NamespaceFile expected = new NamespaceFile(
            Path.of("sub/dir/file.txt"),
            URI.create("kestra:///io/kestra/test/_files/sub/dir/file.txt"),
            namespace
        );

        Assertions.assertEquals(expected, NamespaceFile.of(namespace, Path.of("sub/dir/file.txt")));
        Assertions.assertEquals(expected, NamespaceFile.of(namespace, Path.of("/sub/dir/file.txt")));
        Assertions.assertEquals(expected, NamespaceFile.of(namespace, Path.of("./sub/dir/file.txt")));
    }

    @Test
    void shouldCreateGivenNamespaceAndNullPath() {
        Assertions.assertEquals(new NamespaceFile(
                Path.of(""),
                URI.create("kestra:///io/kestra/test/_files/"),
                namespace
            ), NamespaceFile.of(namespace)
        );
    }

    @Test
    void shouldCreateGivenNamespaceAndRootPath() {
        Assertions.assertEquals(new NamespaceFile(
                Path.of(""),
                URI.create("kestra:///io/kestra/test/_files/"),
                namespace
            ), NamespaceFile.of(namespace, Path.of("/"))
        );
    }

    @Test
    void shouldGetStoragePath() {
        NamespaceFile namespaceFile = new NamespaceFile(
            Path.of("sub/dir/file.txt"),
            URI.create("kestra:///io/kestra/test/_files/sub/dir/file.txt"),
            namespace
        );
        Assertions.assertEquals(Path.of("/io/kestra/test/_files/sub/dir/file.txt"), namespaceFile.storagePath());
    }

    @Test
    void shouldPreserveTrailingSlashForUri() {
        NamespaceFile namespaceFile = NamespaceFile.of(namespace, URI.create("/sub/dir/"));
        Assertions.assertEquals(new NamespaceFile(
                Path.of("sub/dir"),
                URI.create("kestra:///io/kestra/test/_files/sub/dir/"),
                namespace
            ), namespaceFile
        );
        Assertions.assertTrue(namespaceFile.isDirectory());
    }
}