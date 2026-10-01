package io.kestra.core.plugins.endpoint;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultTaskRunOutputsTest {
    private Execution executionWithTaskRuns() {
        TaskRun tr1 = TaskRun.builder().id("tr1").outputs(Map.of("a", 1)).build();
        TaskRun tr2 = TaskRun.builder().id("tr2").outputs(Map.of("b", 2)).build();
        return Execution.builder().taskRunList(List.of(tr1, tr2)).build();
    }

    @Test
    void shouldReturnOnlyTheTargetTaskRunOutputs() {
        TaskRunOutputs outputs = new DefaultTaskRunOutputs(executionWithTaskRuns(), "tr1");
        assertThat(outputs.get()).containsExactlyEntriesOf(Map.of("a", 1));
    }

    @Test
    void shouldReturnEmptyWhenTaskRunHasNoOutputs() {
        TaskRun tr = TaskRun.builder().id("tr1").build();
        Execution execution = Execution.builder().taskRunList(List.of(tr)).build();
        assertThat(new DefaultTaskRunOutputs(execution, "tr1").get()).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenTaskRunUnknown() {
        assertThat(new DefaultTaskRunOutputs(executionWithTaskRuns(), "nope").get()).isEmpty();
    }
}
