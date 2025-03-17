package io.kestra.plugin.core.flow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
public class EachParallelTest {

    @Test
    @ExecuteFlow("flows/valids/each-parallel.yaml")
    void parallel(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(8));
    }

    @Test
    @ExecuteFlow("flows/valids/each-parallel-nested.yaml")
    void parallelNested(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(11));
    }

    @Test
    @ExecuteFlow("flows/valids/each-parallel-Integer.yml")
    void parallelInteger(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @ExecuteFlow("flows/valids/each-parallel-disabled-tasks.yaml")
    void disabledTasks(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(2));
    }
}
