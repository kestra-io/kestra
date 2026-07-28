package io.kestra.core.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FileUtilsTest {

    @Test
    void shouldGetExtension() {
        assertThat(FileUtils.getExtension((String) null)).isNull();
        assertThat(FileUtils.getExtension("")).isNull();
        assertThat(FileUtils.getExtension("/file/hello")).isNull();
        assertThat(FileUtils.getExtension("/file/hello.txt")).isEqualTo(".txt");
        assertThat(FileUtils.getExtension("/file/hello.file with spaces.txt")).isEqualTo(".txt");
        assertThat(FileUtils.getExtension("/file/hello.file.with.multiple.dots.txt")).isEqualTo(".txt");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "kestra:///ns/flow/exec/abc/output",
        "kestra:///ns/flow/exec/abc/.output",
        // a filename that merely contains ".." is not a traversal segment
        "kestra:///ns/flow/exec/abc/my..file.txt",
    })
    void isParentTraversal_false(String path) {
        assertThat(FileUtils.isParentTraversal(URI.create(path))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "kestra:///ns/flow/exec/abc/../../../etc/passwd",
        "kestra:///ns/flow/exec/abc/%2E%2E/%2E%2E/%2E%2E/etc/passwd",
        "kestra:///ns/flow/exec/abc/%2e%2e/%2e%2e/%2e%2e/etc/passwd",
        "kestra:///ns/flow/exec/abc%2F%2E%2E%2Fetc%2Fpasswd",
        // GHSA-qw4v-6w32-xx9h: Windows-style backslash traversal must also be rejected.
        // %5C decodes to '\' in URI.getPath().
        "kestra:///ns/flow/exec/abc%5C..%5C..%5C..%5Cetc%5Cpasswd",
        "kestra:///ns/flow/exec/abc%5C%2E%2E%5C%2E%2E%5Cetc%5Cpasswd",
        // mixed separators
        "kestra:///ns/flow/exec/abc/..%5C..%5Cetc%5Cpasswd",
    })
    void isParentTraversal_true(String path) {
        assertThat(FileUtils.isParentTraversal(URI.create(path))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "..",
        "../etc/passwd",
        "ns/exec/../../etc/passwd",
        "ns/exec/output/..",
        // GHSA-qw4v-6w32-xx9h: backslash variants (already decoded, as from URI.getPath())
        "..\\" + "etc\\passwd",
        "ns\\exec\\..\\.." + "\\etc\\passwd",
        "ns/exec/abc\\..\\..\\.." + "\\etc\\passwd",
    })
    void isParentTraversalString_true(String path) {
        assertThat(FileUtils.isParentTraversal(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "ns/flow/exec/abc/output",
        "ns/flow/exec/abc/.output",
        "ns/flow/exec/abc/my..file.txt",
    })
    void isParentTraversalString_false(String path) {
        assertThat(FileUtils.isParentTraversal(path)).isFalse();
    }

    @Test
    void isParentTraversal_handlesNull() {
        assertThat(FileUtils.isParentTraversal((URI) null)).isFalse();
        assertThat(FileUtils.isParentTraversal((String) null)).isFalse();
    }

    @Test
    void shouldDeleteExistingFileWithRetry(@TempDir Path tempDir) throws Exception {
        Path file = Files.createFile(tempDir.resolve("to-delete.ion"));

        assertThat(FileUtils.deleteWithRetry(file)).isEmpty();
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void shouldNotFailWhenFileAlreadyAbsent(@TempDir Path tempDir) {
        Path file = tempDir.resolve("never-existed.ion");

        // deleteIfExists semantics: a missing file is a no-op, not an error.
        assertThat(FileUtils.deleteWithRetry(file)).isEmpty();
    }

    @Test
    void shouldReturnErrorAfterExhaustingConfiguredAttempts(@TempDir Path tempDir) throws Exception {
        // A non-empty directory cannot be deleted, so every attempt throws — this exercises the
        // configurable retry/give-up path without depending on platform-specific locking.
        Path dir = Files.createDirectory(tempDir.resolve("not-empty"));
        Files.createFile(dir.resolve("child.txt"));

        FileUtils.configureDeletionRetry(2, Duration.ofMillis(1));
        try {
            assertThat(FileUtils.deleteWithRetry(dir)).isPresent();
            assertThat(Files.exists(dir)).isTrue();
        } finally {
            // Restore defaults so the tuning doesn't leak into other tests.
            FileUtils.configureDeletionRetry(FileUtils.DEFAULT_DELETE_MAX_ATTEMPTS, FileUtils.DEFAULT_DELETE_RETRY_DELAY);
        }
    }

    @Test
    void shouldFallBackToDefaultsForInvalidRetrySettings(@TempDir Path tempDir) throws Exception {
        // Non-positive attempts / null delay must not break deletion.
        FileUtils.configureDeletionRetry(0, null);
        try {
            Path file = Files.createFile(tempDir.resolve("to-delete.ion"));
            assertThat(FileUtils.deleteWithRetry(file)).isEmpty();
            assertThat(Files.exists(file)).isFalse();
        } finally {
            FileUtils.configureDeletionRetry(FileUtils.DEFAULT_DELETE_MAX_ATTEMPTS, FileUtils.DEFAULT_DELETE_RETRY_DELAY);
        }
    }
}
