package io.kestra.plugin.core.flow;

import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

import java.util.List;

@KestraTest(startRunner = true)
class ForEachTest {

    @Test
    @ExecuteFlow("flows/valids/foreach-non-concurrent.yaml")
    void nonConcurrent(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(7);
    }

    @Test
    @ExecuteFlow("flows/valids/foreach-concurrent.yaml")
    void concurrent(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(7);
    }

    @Test
    @ExecuteFlow("flows/valids/foreach-concurrent-parallel.yaml")
    void concurrentWithParallel(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(10);
    }

    @Test
    @ExecuteFlow("flows/valids/foreach-concurrent-no-limit.yaml")
    void concurrentNoLimit(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(7);
    }

    @Test
    @ExecuteFlow("flows/valids/foreach-disabled-tasks.yaml")
    void disabledTasks(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
    }

    @Test
    @ExecuteFlow("flows/valids/foreach-error.yaml")
    void errors(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList()).hasSize(6);
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("flows/valids/foreach-nested.yaml")
    void nested(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("flows/valids/foreach-iteration.yaml")
    void iteration(Execution execution) throws InternalException {
        List<TaskRun> seconds = execution.findTaskRunsByTaskId("second");
        assertThat(seconds).hasSize(2);
        assertThat(seconds.get(0).getIteration()).isEqualTo(0);
        assertThat(seconds.get(1).getIteration()).isEqualTo(1);
    }
}