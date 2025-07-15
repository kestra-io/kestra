package io.kestra.plugin.core.execution;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class SetVariablesTest {

    @ExecuteFlow("flows/valids/set-variables.yaml")
    @Test
    void shouldUpdateExecution(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(((Map<String, Object>) execution.getTaskRunList().get(1).getOutputs().get("values"))).containsEntry("message", "Hello Loïc");
    }

    @ExecuteFlow("flows/valids/set-variables-duplicate.yaml")
    @Test
    void shouldFailWhenExistingVariable(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }
}