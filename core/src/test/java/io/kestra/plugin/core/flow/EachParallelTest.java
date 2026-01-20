package io.kestra.plugin.core.flow;

import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
public class EachParallelTest {

    @Test
    @ExecuteFlow(value = "flows/valids/each-parallel.yaml", tenantId = "parallel")
    void parallel(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(8);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/each-parallel-nested.yaml", tenantId = "parallelnested")
    void parallelNested(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(11);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/each-parallel-Integer.yml", tenantId = "parallelinteger")
    void parallelInteger(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/each-parallel-disabled-tasks.yaml", tenantId = "disabledtasks")
    void disabledTasks(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(2);
    }
}
