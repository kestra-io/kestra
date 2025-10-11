package io.kestra.plugin.scripts.runner.docker;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.runners.AbstractTaskRunnerTest;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import org.assertj.core.api.Assertions;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
    

    @Test
    void shouldResumeExistingContainer() throws Exception {
        // resume = true
        var runContext = runContext(this.runContextFactory);

        // first run: create a container and do not wait for it to finish immediately
        var dockerCreate = Docker.builder()
            .image("rockylinux:9.3-minimal")
            .wait(Property.ofValue(false))
            .resume(Property.ofValue(true))
            .build();

        var createCommands = new CommandsWrapper(runContext).withCommands(Property.ofValue(List.of(
            "/bin/sh", "-c",
            "echo \"::{\\\"outputs\\\":{\\\"msg\\\":\\\"Token\\\"}}::\" && sleep 1"
        )));

        var first = dockerCreate.run(runContext, createCommands, Collections.emptyList());
        var firstContainerId = first.getDetails().getContainerId();

        // second run: resume and wait, reusing the same labels context (same runContext)
        var dockerResume = Docker.builder()
            .image("rockylinux:9.3-minimal")
            .wait(Property.ofValue(true))
            .resume(Property.ofValue(true))
            .build();

        var resumeCommands = new CommandsWrapper(runContext).withCommands(Property.ofValue(List.of(
            "/bin/sh", "-c",
            "echo \"::{\\\"outputs\\\":{\\\"msg\\\":\\\"Token\\\"}}::\" && sleep 1"
        )));

        var resumeResult = dockerResume.run(runContext, resumeCommands, Collections.emptyList());
        var resumedContainerId = resumeResult.getDetails().getContainerId();

        assertThat(resumeResult).isNotNull();
        assertThat(resumeResult.getExitCode()).isZero();
        assertThat(resumedContainerId).isEqualTo(firstContainerId);
        assertThat(resumeResult.getLogConsumer().getStdOutCount()).isEqualTo(1);
    }

    @Test
    void shouldCreateNewContainerIfNoneToResume() throws Exception {
        var runContext = runContext(this.runContextFactory);

        var docker = Docker.builder()
            .image("rockylinux:9.3-minimal")
            .resume(Property.ofValue(true))
            .build();

        var commands = new CommandsWrapper(runContext).withCommands(Property.ofValue(List.of(
            "/bin/sh", "-c",
            "echo \"::{\\\"outputs\\\":{\\\"msg\\\":\\\"NewContainer\\\"}}::\""
        )));

        var result = docker.run(runContext, commands, Collections.emptyList());

        assertThat(result).isNotNull();
        assertThat(result.getExitCode()).isZero();
        String msg = (String) result.getLogConsumer().getOutputs().get("msg");
        MatcherAssert.assertThat(msg, containsString("NewContainer"));
        assertThat(result.getLogConsumer().getStdOutCount()).isEqualTo(1);
    }

    @Test
    void shouldCreateTwoContainersWhenResumeDisabled() throws Exception {
        // resume = false
        var runContext = runContext(this.runContextFactory);

        var dockerCreate = Docker.builder()
            .image("rockylinux:9.3-minimal")
            .wait(Property.ofValue(false))
            .delete(Property.ofValue(false))
            .build();

        var commandProps = Property.ofValue(List.of(
            "/bin/sh", "-c",
            "echo 'Token' && sleep 1"
        ));
        var commands1 = new CommandsWrapper(runContext).withCommands(commandProps);
        var commands2 = new CommandsWrapper(runContext).withCommands(commandProps);

        var first = dockerCreate.run(runContext, commands1, Collections.emptyList());
        var firstContainerId = first.getDetails().getContainerId();

        var dockerSecond = Docker.builder()
            .image("rockylinux:9.3-minimal")
            .wait(Property.ofValue(false))
            .delete(Property.ofValue(false))
            .build();

        var second = dockerSecond.run(runContext, commands2, Collections.emptyList());
        var secondContainerId = second.getDetails().getContainerId();

        assertThat(firstContainerId).isNotNull();
        assertThat(secondContainerId).isNotNull();
        assertThat(secondContainerId).isNotEqualTo(firstContainerId);


        try (var client = DockerService.client(runContext, null, null, null, "rockylinux:9.3-minimal")) {
            // wait concurrently
            var f1 = CompletableFuture.supplyAsync(() -> client.waitContainerCmd(firstContainerId).start().awaitStatusCode());
            var f2 = CompletableFuture.supplyAsync(() -> client.waitContainerCmd(secondContainerId).start().awaitStatusCode());
            
            CompletableFuture.allOf(f1, f2).join();
            assertThat(f1.get()).isZero();
            assertThat(f2.get()).isZero();
        }
    }
    
}
