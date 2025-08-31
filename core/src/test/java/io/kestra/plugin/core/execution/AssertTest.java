package io.kestra.plugin.core.execution;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

@KestraTest(startRunner = true)
public class AssertTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Test
    void success() throws Exception {
        Assert task = Assert.builder()
            .id(IdUtils.create())
            .type(Assert.class.getName())
            .conditions(List.of(
                "{{ inputs.key == 'value' }}",
                "{{ 42 == 42 }}"
            ))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of("key", "value"));
        task.run(runContext);

        assertThat(runContext.metrics().stream().filter(e -> e.getName().equals("success")).findFirst().orElseThrow().getValue()).isEqualTo(2d);
        assertThat(runContext.metrics().stream().filter(e -> e.getName().equals("failed")).findFirst().orElseThrow().getValue()).isEqualTo(0d);
    }

    @Test
    void failed() {
        List<LogEntry> logs = new ArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));


        Assert task = Assert.builder()
            .id(IdUtils.create())
            .type(Assert.class.getName())
            .conditions(List.of(
                "{{ 42 == 42 }}",
                "{{ inputs.key == 'value1' }}",
                "{{ 42 == 42 }}",
                "{{ inputs.key == 'value2' }}",
                "{{ 42 == 42 }}"
            ))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of("key", "value"));

        Exception exception = assertThrows(Exception.class, () -> task.run(runContext));

        assertThat(exception.getMessage()).contains("2 assertions failed");

        List<LogEntry> matchingLog = TestsUtils.awaitLogs(logs, 2);
        receive.blockLast();


        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getMessage().contains("inputs.key == 'value1'")).count()).isEqualTo(1L);
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getMessage().contains("inputs.key == 'value2'")).count()).isEqualTo(1L);

        assertThat(runContext.metrics().stream().filter(e -> e.getName().equals("success")).findFirst().orElseThrow().getValue()).isEqualTo(3d);
        assertThat(runContext.metrics().stream().filter(e -> e.getName().equals("failed")).findFirst().orElseThrow().getValue()).isEqualTo(2d);
    }

    @Test
    @ExecuteFlow("flows/valids/assert.yaml")
    void dontFailOnCondition(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }
}
