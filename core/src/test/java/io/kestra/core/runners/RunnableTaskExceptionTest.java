package io.kestra.core.runners;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
class RunnableTaskExceptionTest {
    @Test
    @ExecuteFlow("flows/valids/exception-with-output.yaml")
    void simple(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getOutputs().get("message"), is("Oh no!"));
    }
}