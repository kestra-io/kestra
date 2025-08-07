package io.kestra.plugin.scripts.runner.docker;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.runners.AbstractTaskRunnerTest;
import io.kestra.core.models.tasks.runners.TaskCommands;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import org.assertj.core.api.Assertions;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hamcrest.Matchers.containsString;

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

    @Test
    void shouldSetCorrectCPULimitsInContainer() throws Exception {
        var runContext = runContext(this.runContextFactory);

        var cpuConfig = Cpu.builder()
            .cpus(Property.ofValue(1.5))
            .build();

        var docker = Docker.builder()
            .image("rockylinux:9.3-minimal")
            .cpu(cpuConfig)
            .build();

        var taskCommands = new CommandsWrapper(runContext).withCommands(Property.ofValue(List.of(
                "/bin/sh", "-c",
                "CPU_LIMIT=$(cat /sys/fs/cgroup/cpu.max || cat /sys/fs/cgroup/cpu/cpu.cfs_quota_us) && " +
                    "echo \"::{\\\"outputs\\\":{\\\"cpuLimit\\\":\\\"$CPU_LIMIT\\\"}}::\""
            )));
        var result = docker.run(runContext, taskCommands, Collections.emptyList());

        assertThat(result).isNotNull();
        assertThat(result.getExitCode()).isZero();
        MatcherAssert.assertThat((String) result.getLogConsumer().getOutputs().get("cpuLimit"), containsString("150000"));
        assertThat(result.getLogConsumer().getStdOutCount()).isEqualTo(1);
    }
}