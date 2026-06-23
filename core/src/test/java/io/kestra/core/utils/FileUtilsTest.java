package io.kestra.core.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
