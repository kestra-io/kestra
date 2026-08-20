package io.kestra.plugin.core.flow;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.services.ExecutionOutputService;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class FlowOutputTest {
    @Inject
    private ExecutionOutputService executionOutputService;

    @Test
    @ExecuteFlow(value = "flows/valids/flow-with-outputs.yml", tenantId = "shouldgetsuccessexecutionforflowwithoutputs")
    void shouldGetSuccessExecutionForFlowWithOutputs(Execution execution) throws InternalException {
        assertThat(executionOutputService.getOutputs(execution)).hasSize(1);
        assertThat(executionOutputService.getOutputs(execution).get("key")).isEqualTo("{\"value\":\"flow-with-outputs\"}");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/flow-with-optional-outputs.yml", tenantId = "shouldgetsuccessexecutionforflowwithoptionaloutputs")
    void shouldGetSuccessExecutionForFlowWithOptionalOutputs(Execution execution) throws InternalException {
        assertThat(executionOutputService.getOutputs(execution)).isNull();
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @SuppressWarnings("unchecked")
    @Test
    @ExecuteFlow(value = "flows/valids/flow-with-array-outputs.yml", tenantId = "shouldgetsuccessexecutionforflowwitharrayoutputs")
    void shouldGetSuccessExecutionForFlowWithArrayOutputs(Execution execution) throws InternalException {
        assertThat(executionOutputService.getOutputs(execution)).hasSize(1);
        assertThat((List<String>) executionOutputService.getOutputs(execution).get("myout")).contains("1rstValue", "2ndValue");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/flow-with-outputs-failed.yml", tenantId = "shouldgetfailexecutionforflowwithinvalidoutputs")
    void shouldGetFailExecutionForFlowWithInvalidOutputs(Execution execution) throws InternalException {
        assertThat(executionOutputService.getOutputs(execution)).isNull();
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @SuppressWarnings("deprecation")
    @Test
    @ExecuteFlow(value = "flows/valids/flow-with-outputs.yml", tenantId = "shouldnotstoreoutputsinsidetheexecution")
    void shouldNotStoreOutputsInsideTheExecution(Execution execution) {
        assertThat(execution.getOutputs()).isNull();
    }
}
