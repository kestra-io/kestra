package io.kestra.plugin.core.flow;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(execution.getOutputs()).hasSize(1);
        assertThat(execution.getOutputs().get("key")).isEqualTo("{\"value\":\"flow-with-outputs\"}");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }
    
    @Test
    @ExecuteFlow("flows/valids/flow-with-optional-outputs.yml")
    void shouldGetSuccessExecutionForFlowWithOptionalOutputs(Execution execution) {
        assertThat(execution.getOutputs()).isNull();
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @SuppressWarnings("unchecked")
    @Test
    @ExecuteFlow("flows/valids/flow-with-array-outputs.yml")
    void shouldGetSuccessExecutionForFlowWithArrayOutputs(Execution execution) {
        assertThat(execution.getOutputs()).hasSize(1);
        assertThat((List<String>) execution.getOutputs().get("myout")).contains("1rstValue", "2ndValue");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("flows/valids/flow-with-outputs-failed.yml")
    void shouldGetFailExecutionForFlowWithInvalidOutputs(Execution execution) {
        assertThat(execution.getOutputs()).isNull();
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }
}
