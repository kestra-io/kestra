package io.kestra.plugin.core.flow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import java.util.List;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
class FlowOutputTest {

    @Test
    @ExecuteFlow("flows/valids/flow-with-outputs.yml")
    void shouldGetSuccessExecutionForFlowWithOutputs(Execution execution) {
        assertThat(execution.getOutputs(), aMapWithSize(1));
        assertThat(execution.getOutputs().get("key"), is("{\"value\":\"flow-with-outputs\"}"));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @SuppressWarnings("unchecked")
    @Test
    @ExecuteFlow("flows/valids/flow-with-array-outputs.yml")
    void shouldGetSuccessExecutionForFlowWithArrayOutputs(Execution execution) {
        assertThat(execution.getOutputs(), aMapWithSize(1));
        assertThat((List<String>) execution.getOutputs().get("myout"), hasItems("1rstValue", "2ndValue"));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @ExecuteFlow("flows/valids/flow-with-outputs-failed.yml")
    void shouldGetFailExecutionForFlowWithInvalidOutputs(Execution execution) {
        assertThat(execution.getOutputs(), nullValue());
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
}
