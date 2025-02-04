package io.kestra.plugin.scripts.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.tasks.runners.TaskRunnerResult;
import io.kestra.core.models.tasks.runners.TaskCommands;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
public class LogConsumerTest {
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
    private RunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Test
    void run() throws Exception {
       RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        String outputValue = "a".repeat(10000);
        TaskCommands taskCommands = new CommandsWrapper(runContext).withCommands(List.of(
            "/bin/sh", "-c",
            "echo \"::{\\\"outputs\\\":{\\\"someOutput\\\":\\\"" + outputValue + "\\\"}}::\"\n" +
                "echo -n another line"
        ));
        var run = Docker.from(DockerOptions.builder()
            .image("alpine")
            .build()).run(
            runContext,
            taskCommands,
            Collections.emptyList()
        );
        Await.until(() -> run.getLogConsumer().getStdOutCount() == 2, null, Duration.ofSeconds(5));
        assertThat(run.getLogConsumer().getStdOutCount(), is(2));
        assertThat(run.getLogConsumer().getOutputs().get("someOutput"), is(outputValue));
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
        TaskCommands taskCommands = new CommandsWrapper(runContext).withCommands(List.of(
            "/bin/sh", "-c",
            "echo " + outputValue +
                "echo -n another line"
        ));
        var run = Docker.from(DockerOptions.builder().image("alpine").build()).run(
            runContext,
            taskCommands,
            Collections.emptyList()
        );

        Await.until(() -> run.getLogConsumer().getStdOutCount() == 10, null, Duration.ofSeconds(5));
        assertThat(run.getLogConsumer().getStdOutCount(), is(10));
    }

    @Test
    void logs() throws Exception {
        List<LogEntry> logs = new ArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, TASK, ImmutableMap.of());
        TaskCommands taskCommands = new CommandsWrapper(runContext).withCommands(List.of(
            "/bin/sh", "-c",
            """
                echo '::{"logs": [{"level":"INFO","message":"Hello World"}]}::'
                echo '::{"logs": [{"level":"ERROR","message":"Hello Error"}]}::'
                echo '::{"logs": [{"level":"TRACE","message":"Hello Trace"}, {"level":"TRACE","message":"Hello Trace 2"}]}::'
            """
        ));
        var run = Docker.from(DockerOptions.builder().image("alpine").build()).run(
            runContext,
            taskCommands,
            Collections.emptyList()
        );

        receive.blockLast();

        assertThat(logs.stream().filter(m -> m.getLevel().equals(Level.INFO)).count(), is(1L));
        assertThat(logs.stream().filter(m -> m.getLevel().equals(Level.ERROR)).count(), is(1L));
        assertThat(logs.stream().filter(m -> m.getLevel().equals(Level.TRACE)).filter(m -> m.getMessage().contains("Trace 2")).count(), is(1L));
        assertThat(logs.stream().filter(m -> m.getLevel().equals(Level.TRACE)).count(), greaterThanOrEqualTo(5L));
    }
}
