package io.kestra.plugin.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
class ExitTest {

    @Test
    @ExecuteFlow("flows/valids/exit.yaml")
    void shouldExitTheExecution(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertThat(execution.getTaskRunList().size()).isEqualTo(2);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.WARNING);
    }

    @Test
    @ExecuteFlow("flows/valids/exit-killed.yaml")
    void shouldExitAndKillTheExecution(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(execution.getTaskRunList().size()).isEqualTo(2);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent()).isEqualTo(State.Type.KILLED);

    }
}