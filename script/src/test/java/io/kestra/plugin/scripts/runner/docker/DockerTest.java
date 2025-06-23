package io.kestra.plugin.scripts.runner.docker;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.runners.AbstractTaskRunnerTest;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class DockerTest extends AbstractTaskRunnerTest {
    @Override
    protected TaskRunner<?> taskRunner() {
        return Docker.builder().image("rockylinux:9.3-minimal").build();
    }

    @Test
    void shouldNotHaveTagInDockerPullButJustInWithTag() throws Exception {
        var runContext = runContext(this.runContextFactory);

        var docker = Docker.builder()
            .image("ghcr.io/kestra-io/kestrapy:latest")
            .pullPolicy(Property.ofValue(PullPolicy.ALWAYS))
            .build();

        var taskCommands = new CommandsWrapper(runContext).withCommands(Property.ofValue(List.of(
            "/bin/sh", "-c",
            "echo Hello World!"
        )));
        var result = docker.run(runContext, taskCommands, Collections.emptyList());

        assertThat(result).isNotNull();
        assertThat(result.getExitCode()).isZero();
        Assertions.assertThat(result.getLogConsumer().getStdOutCount()).isEqualTo(1);
    }
}