package io.kestra.plugin.scripts.exec.scripts.runners;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class KotlpUtilsTest {

    @TempDir
    private Path tempDir;

    @Test
    void shouldCopyExecutableBinaryWhenResourceIsEmbedded() throws Exception {
        // When
        Path destination = KotlpUtils.copyTo(tempDir.resolve(KotlpUtils.BINARY_NAME));

        // Then
        assertThat(destination).exists();
        assertThat(Files.size(destination)).isGreaterThan(0);
        assertThat(Files.isExecutable(destination)).isTrue();
    }

    @Test
    void shouldCreateParentDirectoriesWhenDestinationIsNested() throws Exception {
        // When
        Path destination = KotlpUtils.copyTo(tempDir.resolve("nested/dir/" + KotlpUtils.BINARY_NAME));

        // Then
        assertThat(destination).exists();
        assertThat(Files.isExecutable(destination)).isTrue();
    }

    @Test
    void shouldOverwriteExistingFileWhenDestinationExists() throws Exception {
        // Given
        Path destination = tempDir.resolve(KotlpUtils.BINARY_NAME);
        Files.write(destination, new byte[]{1, 2, 3});

        // When
        KotlpUtils.copyTo(destination);

        // Then
        assertThat(Files.size(destination)).isGreaterThan(3);
        assertThat(Files.isExecutable(destination)).isTrue();
    }
}
