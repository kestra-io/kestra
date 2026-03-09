package io.kestra.plugin.core.flow;

import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;
import java.util.List;

@KestraTest(startRunner = true)
class SequentialTest {
    @Test
    @ExecuteFlow("flows/valids/sequential.yaml")
    void sequential(Execution execution) {
        List<TaskRunAttempt> flowableAttempts=execution.findTaskRunsByTaskId("1-seq").getFirst().getAttempts();

        assertThat(execution.getTaskRunList()).hasSize(11);
        assertThat(flowableAttempts).isNotNull();
        assertThat(flowableAttempts.getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("flows/valids/sequential-with-global-errors.yaml")
    void sequentialWithGlobalErrors(Execution execution) {
        List<TaskRunAttempt> flowableAttempts=execution.findTaskRunsByTaskId("parent-seq").getFirst().getAttempts();

        assertThat(execution.getTaskRunList()).hasSize(6);
        assertThat(flowableAttempts).isNotNull();
        assertThat(flowableAttempts.getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    @ExecuteFlow("flows/valids/sequential-with-local-errors.yaml")
    void sequentialWithLocalErrors(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(6);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    @ExecuteFlow("flows/valids/sequential-with-disabled.yaml")
    void sequentialWithDisabled(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }
}