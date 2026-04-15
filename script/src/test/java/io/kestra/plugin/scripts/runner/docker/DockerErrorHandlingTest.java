package io.kestra.plugin.scripts.runner.docker;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DockerErrorHandlingTest {
    @Test
    void shouldDetectDockerSocketErrorsFromNestedCauses() {
        RuntimeException exception = new RuntimeException(
            "docker run failed",
            new IOException("Cannot connect to the Docker daemon at unix:///var/run/docker.sock")
        );

        assertThat(Docker.isDockerSocketAccessError(exception)).isTrue();
    }

    @Test
    void shouldDetectNoSuchFileExceptionAsDockerSocketError() {
        RuntimeException exception = new RuntimeException(
            "docker run failed",
            new IOException(new NoSuchFileException("/var/run/docker.sock"))
        );

        assertThat(Docker.isDockerSocketAccessError(exception)).isTrue();
    }

    @Test
    void shouldDetectDockerSocketErrorsIgnoringCase() {
        RuntimeException exception = new RuntimeException(
            "docker run failed",
            new IOException("cannot CONNECT to the docker daemon at unix:///var/run/docker.sock")
        );

        assertThat(Docker.isDockerSocketAccessError(exception)).isTrue();
    }

    @Test
    void shouldIgnoreUnrelatedRuntimeExceptionsForSocketDetection() {
        RuntimeException exception = new RuntimeException("some other docker failure");

        assertThat(Docker.isDockerSocketAccessError(exception)).isFalse();
    }
}
