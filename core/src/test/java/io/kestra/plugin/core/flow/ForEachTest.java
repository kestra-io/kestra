package io.kestra.plugin.core.flow;

import static org.assertj.core.api.Assertions.assertThat;

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
    @ExecuteFlow(value = "flows/valids/foreach-non-concurrent.yaml", tenantId = "nonconcurrent")
    void nonConcurrent(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(7);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/foreach-concurrent.yaml", tenantId = "concurrent")
    void concurrent(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(7);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/foreach-concurrent-parallel.yaml", tenantId = "concurrentwithparallel")
    void concurrentWithParallel(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(10);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/foreach-concurrent-no-limit.yaml", tenantId = "concurrentnolimit")
    void concurrentNoLimit(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(7);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/foreach-disabled-tasks.yaml", tenantId = "disabledtasks")
    void disabledTasks(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/foreach-error.yaml", tenantId = "errors")
    void errors(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList()).hasSize(6);
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/foreach-nested.yaml", tenantId = "nested")
    void nested(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/foreach-iteration.yaml", tenantId = "iteration")
    void iteration(Execution execution) {
        List<TaskRun> seconds = execution.findTaskRunsByTaskId("second");
        assertThat(seconds).hasSize(2);
        assertThat(seconds.get(0).getIteration()).isEqualTo(0);
        assertThat(seconds.get(1).getIteration()).isEqualTo(1);
    }
}