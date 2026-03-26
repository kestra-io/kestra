package io.kestra.core.runners;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.services.TaskOutputService;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class NullOutputTest {
    @Inject
    private TaskOutputService taskOutputService;

    @Test
    @ExecuteFlow("flows/valids/null-output.yaml")
    void shouldIncludeNullOutput(Execution execution) throws io.kestra.core.exceptions.InternalException {
        assertThat(execution).isNotNull();
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().getFirst())).hasSize(1);
        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().getFirst()).containsKey("value")).isTrue();
    }
}
