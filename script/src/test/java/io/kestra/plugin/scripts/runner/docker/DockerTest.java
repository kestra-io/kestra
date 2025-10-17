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
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.assertj.core.api.Assertions;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    public static void callOnKill(TaskRunner<?> taskRunner, Runnable runnable) throws Exception {
        Method method = TaskRunner.class.getDeclaredMethod("onKill", Runnable.class);
        method.setAccessible(true);
        method.invoke(taskRunner, runnable);
    }

    @Test
    void killAfterResume() throws Exception {
        var taskRunId = IdUtils.create();

        // Create a new RunContext with a specific taskRunId
        var runContext = runContext(this.runContextFactory, null, taskRunId);
        var commands = initScriptCommands(runContext);


        // Setup log queue consumer
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(workerTaskLogQueue, (logEntry) -> {
            if (logEntry.isLeft()) {
                logs.add(logEntry.getLeft());
            }
        });

        var commandsList = ScriptService.scriptCommands(List.of("/bin/sh", "-c"), Collections.emptyList(),
            List.of("echo 'sleeping for 50 seconds' && sleep 50"));
        Mockito.when(commands.getCommands()).thenReturn(Property.ofValue(commandsList));

        var taskRunner = ((Docker) taskRunner())
            .toBuilder()
            .delete(Property.ofValue(false))
            .build();
        // Assert that the resume property is set to true by default
        Boolean resume = runContext.render(taskRunner.getResume()).as(Boolean.class).orElseThrow();
        assertThat(resume).isEqualTo(Boolean.TRUE);

        Thread initialContainerThread = new Thread(throwRunnable(() -> taskRunner.run(runContext, commands, Collections.emptyList())));
        initialContainerThread.start();

        try (var client = DockerService.client(runContext, null, null, null, "rockylinux:9.3-minimal")) {
            Map<String, String> labels = ScriptService.labels(runContext, "kestra.io/");

            var timeout = Duration.ofSeconds(30);
            // Wait for the container to be created
            Await.until(() -> {
                List<Container> existingContainers = client.listContainersCmd()
                    .withShowAll(true)
                    .withLabelFilter(labels)
                    .exec();
                return !existingContainers.isEmpty() && existingContainers.get(0).getState().equals("running");
            }, Duration.ofMillis(100), timeout); // Add timeout to avoid waiting forever for container to be created

            callOnKill(taskRunner, () -> {
                // override the kill method to not kill the container
            });
            initialContainerThread.interrupt();
            initialContainerThread.join();

            // Create a new RunContext with the same taskRunId to maintain labels AND the same method to get a similar context
            RunContext anotherRunContext = runContext(this.runContextFactory, null, taskRunId);

            var anotherTaskRunner = ((Docker) taskRunner())
                .toBuilder()
                .delete(Property.ofValue(true)) // Delete the container after the second run
                .build();

            // Start resume in a new thread
            var resumeCommands = initScriptCommands(anotherRunContext);

            Mockito.when(resumeCommands.getCommands()).thenReturn(Property.ofValue(commandsList));
            Thread resumeContainerThread = new Thread(throwRunnable(() -> anotherTaskRunner.run(anotherRunContext, resumeCommands, Collections.emptyList())));
            resumeContainerThread.start();

            // Wait for the log message indicating resume
            LogEntry awaitLog = TestsUtils
                .awaitLog(logs, logEntry -> logEntry.getMessage().contains("Resuming existing container:"));
            LogEntry createContainerLog = TestsUtils
                .awaitLog(logs, logEntry -> logEntry.getMessage().contains("Container created:"));

            receive.blockLast(timeout);
            // Assert that the log messages are present
            assertThat(createContainerLog).withFailMessage("create container log should not be null").isNotNull();
            assertThat(createContainerLog.getMessage()).contains("Container created:");
            assertThat(awaitLog).withFailMessage("await log should not be null").isNotNull();
            assertThat(awaitLog.getMessage()).contains("Resuming existing container:");

            // Get container id from the logs using regex

            String createContainerId = null;
            String resumeContainerId = null;
            Matcher createContainerMatcher =
                Pattern.compile("Container created: ([\\w]+)").matcher(createContainerLog.getMessage());
            if (createContainerMatcher.find()) {
                createContainerId = createContainerMatcher.group(1);
            }

            assertThat(createContainerId)
                .withFailMessage("Could not extract container id from create container log: %s", createContainerLog.getMessage())
                .isNotNull();
            Matcher resumeContainerMatcher =
                Pattern.compile("Resuming existing container: ([\\w]+)").matcher(awaitLog.getMessage());
            if (resumeContainerMatcher.find()) {
                resumeContainerId = resumeContainerMatcher.group(1);
            }

            // Assert that the container id is the same
            assertThat(resumeContainerId).isEqualTo(createContainerId);

            // Kill the container and verify cleanup
            resumeContainerThread.interrupt();
            resumeContainerThread.join();

            List<Container> existingContainers = client.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(labels)
                .exec();
            MatcherAssert.assertThat(existingContainers.isEmpty(), is(true));
        }
    }
}
