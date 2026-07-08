package io.kestra.plugin.scripts.runner.docker;

import org.junit.jupiter.api.Test;

import io.kestra.plugin.scripts.runner.docker.Docker.FileHandlingStrategy;

import static org.assertj.core.api.Assertions.assertThat;

class DockerOutputDownloadTest {
    private static final String VOLUME_NAME = "kestra-files-volume";

    @Test
    void shouldDownloadWhenOutputFilesAreDeclared() {
        assertThat(Docker.shouldDownloadOutputFiles(true, false, FileHandlingStrategy.VOLUME, VOLUME_NAME)).isTrue();
    }

    @Test
    void shouldDownloadWhenOutputDirectoryIsEnabled() {
        assertThat(Docker.shouldDownloadOutputFiles(false, true, FileHandlingStrategy.VOLUME, VOLUME_NAME)).isTrue();
    }

    @Test
    void shouldNotDownloadWhenOnlyInputFilesWereUploaded() {
        // Regression: a task that only uploads inputFiles still needs a volume, but has nothing to
        // download. Streaming the whole working directory back used to fail with a truncated response.
        assertThat(Docker.shouldDownloadOutputFiles(false, false, FileHandlingStrategy.VOLUME, VOLUME_NAME)).isFalse();
    }

    @Test
    void shouldNotDownloadWhenStrategyIsMount() {
        assertThat(Docker.shouldDownloadOutputFiles(true, true, FileHandlingStrategy.MOUNT, VOLUME_NAME)).isFalse();
    }

    @Test
    void shouldNotDownloadWhenNoVolumeWasCreated() {
        assertThat(Docker.shouldDownloadOutputFiles(true, true, FileHandlingStrategy.VOLUME, null)).isFalse();
    }
}
