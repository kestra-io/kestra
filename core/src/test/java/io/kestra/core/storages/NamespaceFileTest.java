package io.kestra.core.storages;

import java.net.URI;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.kestra.core.storages.NamespaceFile.toLogicalPath;
import static org.assertj.core.api.Assertions.assertThat;

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
        Assertions.assertEquals(
            expected, NamespaceFile.of(NAMESPACE, Path.of("/"))
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
        Assertions.assertEquals(
            new NamespaceFile(
                Path.of("sub/dir/file.txt"),
                URI.create("kestra:///io/kestra/test/_files/sub/dir/file.txt"),
                NAMESPACE
            ), NamespaceFile.of(NAMESPACE, URI.create("kestra:///io/kestra/test/_files/sub/dir/file.txt"))
        );
    }

    @Test
    void shouldCreateGivenNamespaceAndValidRelativeURI() {
        Assertions.assertEquals(
            new NamespaceFile(
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
        Assertions.assertEquals(
            new NamespaceFile(
                Path.of(""),
                URI.create("kestra:///io/kestra/test/_files/"),
                NAMESPACE
            ), NamespaceFile.of(NAMESPACE)
        );
    }

    @Test
    void shouldCreateGivenNamespaceAndRootPath() {
        Assertions.assertEquals(
            new NamespaceFile(
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
        Assertions.assertEquals(
            new NamespaceFile(
                Path.of("sub/dir"),
                URI.create("kestra:///io/kestra/test/_files/sub/dir/"),
                NAMESPACE
            ), namespaceFile
        );
        Assertions.assertTrue(namespaceFile.isDirectory());
    }

    @Test
    void shouldNormalizeNamespacePathIndependentlyOfOperatingSystem() {
        Path windowsPath1 = NamespaceFile.normalize(Path.of("folder\\file.txt"));
        Path windowsPath2 = NamespaceFile.normalize(Path.of("\\folder\\file.txt"));
        Path unixPath1 = NamespaceFile.normalize(Path.of("folder/file.txt"));
        Path unixPath2 = NamespaceFile.normalize(Path.of("/folder/file.txt"));

        assertThat(toLogicalPath(windowsPath1)).isEqualTo("/folder/file.txt");
        assertThat(toLogicalPath(windowsPath2)).isEqualTo("/folder/file.txt");
        assertThat(toLogicalPath(unixPath1)).isEqualTo("/folder/file.txt");
        assertThat(toLogicalPath(unixPath2)).isEqualTo("/folder/file.txt");
    }

    @Test
    void shouldThrowOnPathTraversalWithDoubleDots() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> NamespaceFile.normalize(Path.of("/foo/../../etc/passwd")));
        Assertions.assertThrows(IllegalArgumentException.class, () -> NamespaceFile.normalize(Path.of("/../etc")));
        Assertions.assertThrows(IllegalArgumentException.class, () -> NamespaceFile.normalize(Path.of("..")));
        Assertions.assertThrows(IllegalArgumentException.class, () -> NamespaceFile.normalize(Path.of("foo/../../../bar")));
    }

    @Test
    void shouldThrowOnForwardSlashPathTraversal() {
        // GHSA-h7c7-3mfc-m7pj: on a Windows JVM Path.of("/x/../../../foo.txt").toString()
        // produces "\x\..\..\..\foo.txt"; toLogicalPath() converts backslashes back to "/",
        // so the guard must catch the forward-slash form regardless of host OS.
        Assertions.assertThrows(IllegalArgumentException.class, () -> NamespaceFile.normalize(Path.of("/x/../../../escaped.txt")));
        Assertions.assertThrows(IllegalArgumentException.class, () -> NamespaceFile.normalize(Path.of("x/../../../escaped.txt")));
        Assertions.assertThrows(IllegalArgumentException.class, () -> NamespaceFile.normalize(Path.of("/x/y/../../..")));
    }

    @Test
    void shouldAllowLegitimatePathsWithSingleDotAndMultiLevelDirs() {
        assertThat(toLogicalPath(NamespaceFile.normalize(Path.of("/foo/./bar")))).isEqualTo("/foo/bar");
        assertThat(toLogicalPath(NamespaceFile.normalize(Path.of("/foo/bar")))).isEqualTo("/foo/bar");
        assertThat(toLogicalPath(NamespaceFile.normalize(Path.of("/file..with..dots")))).isEqualTo("/file..with..dots");
    }

    @Test
    void shouldDistinguishSpaceAndPlusInStorageUri() {
        // Regression: "a b.txt" (space) and "a+b.txt" (literal '+') must map to distinct storage URIs.
        // Previously, URLEncoder.encode(space) = '+', causing both names to collide on the same object.
        NamespaceFile spaceFile = NamespaceFile.of(NAMESPACE, Path.of("/a b.txt"));
        NamespaceFile plusFile  = NamespaceFile.of(NAMESPACE, Path.of("/a+b.txt"));

        // Space must be percent-encoded in the URI (URI-illegal → %20); '+' must remain '+' (URI-legal).
        assertThat(spaceFile.uri().toString()).contains("%20");
        assertThat(spaceFile.uri().toString()).doesNotContain("a+b.txt");
        assertThat(plusFile.uri().toString()).contains("a+b.txt");
        assertThat(plusFile.uri().toString()).doesNotContain("%20");

        // The two URIs must be distinct — no silent collision.
        assertThat(spaceFile.uri()).isNotEqualTo(plusFile.uri());

        // storagePath() must round-trip back to the decoded path.
        assertThat(spaceFile.storagePath().toString()).endsWith("a b.txt");
        assertThat(plusFile.storagePath().toString()).endsWith("a+b.txt");
    }

}