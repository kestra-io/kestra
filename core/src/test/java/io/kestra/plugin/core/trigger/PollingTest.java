package io.kestra.plugin.core.trigger;

import io.kestra.core.junit.annotations.EvaluateTrigger;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
class PollingTest {

    @Test
    @EvaluateTrigger(
            flow = "flows/tests/trigger-polling.yaml",
            triggerId = "polling-trigger-1"
    )
    void pollingTriggerSuccess(Optional<Execution> optionalExecution) {
        assertThat(optionalExecution).isPresent();
        Execution execution = optionalExecution.get();
        assertThat(execution.getFlowId()).isEqualTo("polling-flow");
        assertThat(execution.getVariables()).containsEntry("custom_var", "VARIABLE VALUE");
        assertTrue(execution.getState().getCurrent().isCreated());
    }
}
