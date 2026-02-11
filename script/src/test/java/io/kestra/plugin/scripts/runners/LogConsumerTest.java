package io.kestra.plugin.scripts.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.runners.TaskCommands;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.kestra.plugin.scripts.runner.docker.Docker;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class LogConsumerTest {
    private static final Task TASK = new Task() {
        @Override
        public String getId() {
            return "id";
        }

        @Override
        public String getType() {
            return "type";
        }
    };

    @Inject
    private TestRunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Test
    void run() throws Exception {
       RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        String outputValue = "a".repeat(10000);
        TaskCommands taskCommands = new CommandsWrapper(runContext)
            .withCommands(Property.ofValue(List.of(
            "/bin/sh", "-c",
            "echo \"::{\\\"outputs\\\":{\\\"someOutput\\\":\\\"" + outputValue + "\\\"}}::\"\n" +
                "echo -n another line"
        )));
        var run = Docker.from(DockerOptions.builder()
            .image("alpine")
            .build()).run(
            runContext,
            taskCommands,
            Collections.emptyList()
        );
        Await.until(() -> run.getLogConsumer().getStdOutCount() == 2, null, Duration.ofSeconds(5));
        assertThat(run.getLogConsumer().getStdOutCount()).isEqualTo(2);
        assertThat(run.getLogConsumer().getOutputs().get("someOutput")).isEqualTo(outputValue);
    }

    @Test
    void testWithMultipleCrInSameFrame() throws Exception {

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        StringBuilder outputValue = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            outputValue.append(Integer.toString(i).repeat(100)).append("\r")
                    .append(Integer.toString(i).repeat(800)).append("\r")
                .append(Integer.toString(i).repeat(2000)).append("\r");
        }
        TaskCommands taskCommands = new CommandsWrapper(runContext).withCommands(Property.ofValue(List.of(
            "/bin/sh", "-c",
            "echo " + outputValue +
                "echo -n another line"
        )));
        var run = Docker.from(DockerOptions.builder().image("alpine").build()).run(
            runContext,
            taskCommands,
            Collections.emptyList()
        );

        Await.until(() -> run.getLogConsumer().getStdOutCount() == 10, null, Duration.ofSeconds(5));
        assertThat(run.getLogConsumer().getStdOutCount()).isEqualTo(10);
    }

    @Test
    @FlakyTest
    void logs() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        TaskCommands taskCommands = new CommandsWrapper(runContext).withCommands(Property.ofValue(List.of(
            "/bin/sh", "-c",
            """
                echo '::{"logs": [{"level":"INFO","message":"Hello World"}]}::'
                echo '::{"logs": [{"level":"ERROR","message":"Hello Error"}]}::'
                echo '::{"logs": [{"level":"TRACE","message":"Hello Trace"}, {"level":"TRACE","message":"Hello Trace 2"}]}::'
            """
        )));

        Docker.from(DockerOptions.builder().image("alpine").build()).run(
            runContext,
            taskCommands,
            Collections.emptyList()
        );

        Await.until(() -> logs.size() >= 10, null, Duration.ofSeconds(20));
        receive.blockLast();

        assertThat(logs.stream().filter(m -> m.getLevel().equals(Level.INFO)).count()).isEqualTo(1L);
        assertThat(logs.stream().filter(m -> m.getLevel().equals(Level.ERROR)).count()).isEqualTo(1L);
        assertThat(logs.stream().filter(m -> m.getLevel().equals(Level.TRACE)).filter(m -> m.getMessage().contains("Trace 2")).count()).isEqualTo(1L);
        assertThat(logs.stream().filter(m -> m.getLevel().equals(Level.TRACE)).count()).isGreaterThanOrEqualTo(4L);
    }

    @Test
    void stderrLogsWithEmbeddedLevels() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        TaskCommands taskCommands = new CommandsWrapper(runContext).withCommands(Property.ofValue(List.of(
            "/bin/sh", "-c",
            """
                # Simulate Python logger output that goes to stderr with embedded log levels
                >&2 echo '[INFO] 2025-08-19 15:04:11,496 | Kestra | fn:_log(61) | message: This is an info message'
                >&2 echo '[ERROR] 2025-08-19 15:04:11,497 | Kestra | fn:_log(62) | message: This is an error message'
                >&2 echo '[WARNING] 2025-08-19 15:04:11,498 | Kestra | fn:_log(63) | message: This is a warning message'
                >&2 echo '[DEBUG] 2025-08-19 15:04:11,499 | Kestra | fn:_log(64) | message: This is a debug message'
                >&2 echo 'No level here - should be ERROR'
            """
        )));

        Docker.from(DockerOptions.builder().image("alpine").build()).run(
            runContext,
            taskCommands,
            Collections.emptyList()
        );

        Await.until(() -> logs.size() >= 5, null, Duration.ofSeconds(20));
        receive.blockLast();

        // Check that INFO level was properly extracted from stderr
        assertThat(logs.stream()
            .filter(m -> m.getLevel().equals(Level.INFO))
            .filter(m -> m.getMessage().contains("This is an info message"))
            .count()).isEqualTo(1L);

        // Check that ERROR level was properly extracted from stderr
        assertThat(logs.stream()
            .filter(m -> m.getLevel().equals(Level.ERROR))
            .filter(m -> m.getMessage().contains("This is an error message"))
            .count()).isEqualTo(1L);

        // Check that WARNING level was properly extracted from stderr
        assertThat(logs.stream()
            .filter(m -> m.getLevel().equals(Level.WARN))
            .filter(m -> m.getMessage().contains("This is a warning message"))
            .count()).isEqualTo(1L);

        // Check that DEBUG level was properly extracted from stderr
        assertThat(logs.stream()
            .filter(m -> m.getLevel().equals(Level.DEBUG))
            .filter(m -> m.getMessage().contains("This is a debug message"))
            .count()).isEqualTo(1L);

        // Check that stderr without level indicator defaults to ERROR
        assertThat(logs.stream()
            .filter(m -> m.getLevel().equals(Level.ERROR))
            .filter(m -> m.getMessage().contains("No level here - should be ERROR"))
            .count()).isEqualTo(1L);
    }
}
