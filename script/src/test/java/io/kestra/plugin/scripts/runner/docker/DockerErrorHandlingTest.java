package io.kestra.plugin.scripts.runner.docker;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.BadRequestException;
import com.github.dockerjava.api.model.MountType;

import static org.assertj.core.api.Assertions.assertThat;

class DockerErrorHandlingTest {
    @Test
    void shouldDetectConfirmedContainerOomTermination() {
        InspectContainerResponse container = Mockito.mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState state = Mockito.mock(InspectContainerResponse.ContainerState.class);
        Mockito.when(container.getState()).thenReturn(state);
        Mockito.when(state.getOOMKilled()).thenReturn(true);

        assertThat(Docker.isContainerOomKilled(container)).isTrue();
    }

    @Test
    void shouldHandleMissingContainerInspectionMetadata() {
        InspectContainerResponse container = Mockito.mock(InspectContainerResponse.class);

        assertThat(Docker.isContainerOomKilled(container)).isFalse();
        assertThat(Docker.isContainerOomKilled(null)).isFalse();
    }

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

    @Test
    void shouldBuildReadWriteBindMountAtSamePathForWorkingDirectory() {
        var mount = Docker.workingDirectoryMount("/tmp/kestra-wd/tmp/abc");

        assertThat(mount.getType()).isEqualTo(MountType.BIND);
        assertThat(mount.getSource()).isEqualTo("/tmp/kestra-wd/tmp/abc");
        assertThat(mount.getTarget()).isEqualTo("/tmp/kestra-wd/tmp/abc");
        assertThat(mount.getReadOnly()).isFalse();
    }

    @Test
    void shouldDetectMissingBindSourceRejectedByDaemon() {
        var exception = new BadRequestException(
            "{\"message\":\"invalid mount config for type \\\"bind\\\": bind source path does not exist: /tmp/kestra-wd/tmp/abc\"}"
        );

        assertThat(Docker.isMissingBindSource(exception, "/tmp/kestra-wd/tmp/abc")).isTrue();
        assertThat(Docker.isMissingBindSource(new RuntimeException("wrapped", exception), "/tmp/kestra-wd/tmp/abc")).isTrue();
    }

    @Test
    void shouldNotDetectMissingBindSourceForUnrelatedDockerFailures() {
        var missingOtherPath = new BadRequestException("invalid mount config for type \"bind\": bind source path does not exist: /somewhere/else");
        var pullDenied = new BadRequestException("pull access denied for rockylinux, repository does not exist or may require authorization");
        var permission = new BadRequestException("permission denied while trying to connect to the Docker daemon socket");

        assertThat(Docker.isMissingBindSource(missingOtherPath, "/tmp/kestra-wd/tmp/abc")).isFalse();
        assertThat(Docker.isMissingBindSource(pullDenied, "/tmp/kestra-wd/tmp/abc")).isFalse();
        assertThat(Docker.isMissingBindSource(permission, "/tmp/kestra-wd/tmp/abc")).isFalse();
    }

    @Test
    void shouldNotDetectMissingBindSourceWhenNoMountWasRequested() {
        var exception = new BadRequestException("invalid mount config for type \"bind\": bind source path does not exist: /tmp/kestra-wd/tmp/abc");

        assertThat(Docker.isMissingBindSource(exception, null)).isFalse();
    }
}
