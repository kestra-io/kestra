package io.kestra.plugin.scripts.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.tasks.runners.RunnerResult;
import io.kestra.core.models.tasks.runners.TaskCommands;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.kestra.plugin.scripts.runner.docker.Docker;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
public class LogConsumerTest {
    @Inject
    private ApplicationContext applicationContext;
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        Task task = new Task() {
            @Override
            public String getId() {
                return "id";
            }

            @Override
            public String getType() {
                return "type";
            }
        };
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        String outputValue = "a".repeat(10000);
        TaskCommands taskCommands = new CommandsWrapper(runContext).withCommands(List.of(
            "/bin/sh", "-c",
            "echo \"::{\\\"outputs\\\":{\\\"someOutput\\\":\\\"" + outputValue + "\\\"}}::\"\n" +
                "echo -n another line"
        ));
        RunnerResult run = Docker.from(DockerOptions.builder().image("alpine").build()).run(
            runContext,
            taskCommands,
            Collections.emptyList(),
            Collections.emptyList()
        );
        Await.until(() -> run.getLogConsumer().getStdOutCount() == 2, null, Duration.ofSeconds(5));
        assertThat(run.getLogConsumer().getStdOutCount(), is(2));
        assertThat(run.getLogConsumer().getOutputs().get("someOutput"), is(outputValue));
    }

    @Test
    void testWithMultipleCrInSameFrame() throws Exception {
        Task task = new Task() {
            @Override
            public String getId() {
                return "id";
            }

            @Override
            public String getType() {
                return "type";
            }
        };
        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
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
        RunnerResult run = Docker.from(DockerOptions.builder().image("alpine").build()).run(
            runContext,
            taskCommands,
            Collections.emptyList(),
            Collections.emptyList()
        );

        Await.until(() -> run.getLogConsumer().getStdOutCount() == 10, null, Duration.ofSeconds(5));
        assertThat(run.getLogConsumer().getStdOutCount(), is(10));
    }
}
