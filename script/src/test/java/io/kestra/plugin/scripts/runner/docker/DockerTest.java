package io.kestra.plugin.scripts.runner.docker;

import com.github.dockerjava.api.model.Container;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.runners.AbstractTaskRunnerTest;
import io.kestra.core.models.tasks.runners.ScriptService;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.assertj.core.api.Assertions;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import org.mockito.Mockito;

import static io.kestra.core.utils.Rethrow.throwRunnable;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

 

class DockerTest extends AbstractTaskRunnerTest {
    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> workerTaskLogQueue;

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
    void killAfterResume() throws Exception {
        var runContext = runContext(this.runContextFactory);
        var commands = initScriptCommands(runContext);


        // Setup log queue consumer
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(workerTaskLogQueue, (logEntry) -> {
            logs.add(logEntry.getLeft());
        });

        var commandsList = ScriptService.scriptCommands(List.of("/bin/sh", "-c"), Collections.emptyList(),
            List.of("echo 'sleeping for 50 seconds' && sleep 50"));
        Mockito.when(commands.getCommands()).thenReturn(Property.ofValue(commandsList));

        var taskRunner = ((Docker) taskRunner())
            .toBuilder()
            .delete(Property.ofValue(false))
            .resume(Property.ofValue(true)).build();
        Thread initialContainerThread = new Thread(throwRunnable(() -> taskRunner.run(runContext, commands, Collections.emptyList())));
        initialContainerThread.start();

        try (var client = DockerService.client(runContext, null, null, null, "rockylinux:9.3-minimal")) {
            Map<String, String> labels = ScriptService.labels(runContext, "kestra.io/");

            // Wait for the container to be created
            Await.until(() -> {
                List<Container> existingContainers = client.listContainersCmd()
                    .withShowAll(true)
                    .withLabelFilter(labels)
                    .exec();
                return !existingContainers.isEmpty() && existingContainers.get(0).getState().equals("running");
            });
            initialContainerThread.interrupt();

            // Create a new RunContext with the same taskrun variables to maintain labels
            @SuppressWarnings("unchecked")
            Map<String, Object> taskRunProps = new HashMap<>((Map<String, Object>) runContext.getVariables().get("taskrun"));
            RunContext anotherRunContext = runContext(this.runContextFactory, Map.of("taskrun", taskRunProps));
            var anotherTaskRunner = ((Docker) taskRunner())
                .toBuilder()
                .delete(Property.ofValue(false))
                .resume(Property.ofValue(true))
                .build();

            // Start resume in a new thread
            var resumeCommands = initScriptCommands(anotherRunContext);

            Mockito.when(resumeCommands.getCommands()).thenReturn(Property.ofValue(commandsList));
            Thread resumeContainerThread = new Thread(throwRunnable(() -> anotherTaskRunner.run(anotherRunContext, resumeCommands, Collections.emptyList())));
            resumeContainerThread.start();
            // Wait for the log message indicating resume
            LogEntry awaitLog = TestsUtils.awaitLog(logs, logEntry -> logEntry.getMessage().contains("Resuming existing container"));
            // assertThat(awaitLog).isNotNull().withFailMessage("await log should not be null");
            // assertThat(awaitLog.getMessage()).contains("Resuming existing container");

            receive.blockLast();

            // Kill the container and verify cleanup
            anotherTaskRunner.kill();
            resumeContainerThread.interrupt();

            List<Container> existingContainers = client.listContainersCmd()
                .withLabelFilter(labels)
                .exec();
            MatcherAssert.assertThat(existingContainers.isEmpty(), is(true));
        }
    }
}
