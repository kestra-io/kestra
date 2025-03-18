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
class SequentialTest {
    @Test
    @ExecuteFlow("flows/valids/sequential.yaml")
    void sequential(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(11));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @ExecuteFlow("flows/valids/sequential-with-global-errors.yaml")
    void sequentialWithGlobalErrors(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(6));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    @ExecuteFlow("flows/valids/sequential-with-local-errors.yaml")
    void sequentialWithLocalErrors(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(6));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
}