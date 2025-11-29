package io.kestra.core.runners;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class TaskWithRunIfTest {

    @Test
    @ExecuteFlow("flows/valids/task-runif.yml")
    void runnableTask(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.findTaskRunsByTaskId("executed").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("notexecuted").getFirst().getState().getCurrent()).isEqualTo(State.Type.SKIPPED);
        assertThat(execution.findTaskRunsByTaskId("notexecuted").getFirst().getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.SKIPPED);
        assertThat(execution.findTaskRunsByTaskId("notexecutedflowable").getFirst().getState().getCurrent()).isEqualTo(State.Type.SKIPPED);
        assertThat(execution.findTaskRunsByTaskId("notexecutedflowable").getFirst().getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.SKIPPED);
        assertThat(execution.findTaskRunsByTaskId("willfailedtheflow").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    @ExecuteFlow("flows/valids/task-runif-workingdirectory.yml")
    void runIfWorkingDirectory(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.findTaskRunsByTaskId("log_orders").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("log_test").getFirst().getState().getCurrent()).isEqualTo(State.Type.SKIPPED);
        assertThat(execution.findTaskRunsByTaskId("log_test").getFirst().getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.SKIPPED);
    }

    @Test
    @ExecuteFlow("flows/valids/task-runif-executionupdating.yml")
    void executionUpdatingTask(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.findTaskRunsByTaskId("skipSetVariables").getFirst().getState().getCurrent()).isEqualTo(State.Type.SKIPPED);
        assertThat(execution.findTaskRunsByTaskId("skipSetVariables").getFirst().getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.SKIPPED);
        assertThat(execution.findTaskRunsByTaskId("skipUnsetVariables").getFirst().getState().getCurrent()).isEqualTo(State.Type.SKIPPED);
        assertThat(execution.findTaskRunsByTaskId("skipUnsetVariables").getFirst().getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.SKIPPED);
        assertThat(execution.findTaskRunsByTaskId("unsetVariables").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("setVariables").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getVariables()).containsEntry("list", List.of(42));
    }
}
