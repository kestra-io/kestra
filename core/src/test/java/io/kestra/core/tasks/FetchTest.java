package io.kestra.core.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
class FetchTest {

    @Test
    @ExecuteFlow("flows/valids/get-log.yaml")
    void fetch(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(4);
        TaskRun fetch = execution.findTaskRunsByTaskId("get-log-task").getFirst();
        assertThat(fetch.getOutputs().get("size")).isEqualTo(3);
    }

    @Test
    @ExecuteFlow("flows/valids/get-log-taskid.yaml")
    void fetchWithTaskId(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(4);
        TaskRun fetch = execution.findTaskRunsByTaskId("get-log-task").getFirst();
        assertThat(fetch.getOutputs().get("size")).isEqualTo(1);
    }

    @Test
    @ExecuteFlow("flows/valids/get-log-executionid.yaml")
    void fetchWithExecutionId(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(4);
        TaskRun fetch = execution.findTaskRunsByTaskId("get-log-task").getFirst();
        assertThat(fetch.getOutputs().get("size")).isEqualTo(3);
    }
}
