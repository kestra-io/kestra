package io.kestra.core.models.flows.input;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InputOutputValidationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.TestRunnerUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest(startRunner = true)
class JsonInputSchemaFlowIntegrationTest {
    @Inject
    private TestRunnerUtils runnerUtils;

    @Inject
    private FlowInputOutput flowIO;

    @Test
    @LoadFlows("flows/valids/json-input-schema-validation.yaml")
    void shouldRunFlowWhenJsonInputMatchesSchema() throws TimeoutException, QueueException, io.kestra.core.exceptions.InternalException {
        // Given
        Map<String, Object> inputs = Map.of(
            "jsonWithSchema", Map.of("name", "kestra"),
            "jsonWithoutSchema", Map.of("any", "shape")
        );

        // When
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            "io.kestra.tests",
            "json-input-schema-validation",
            null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs)
        );

        // Then
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(2);
    }

    @Test
    @LoadFlows("flows/valids/json-input-schema-validation.yaml")
    void shouldFailWhenJsonInputDoesNotMatchSchema() {
        // Given
        Map<String, Object> inputs = Map.of(
            "jsonWithSchema", Map.of("name", 42),
            "jsonWithoutSchema", Map.of("ok", true)
        );

        // When
        InputOutputValidationException exception = assertThrows(
            InputOutputValidationException.class,
            () -> runnerUtils.runOne(
                MAIN_TENANT,
                "io.kestra.tests",
                "json-input-schema-validation",
                null,
                (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs)
            )
        );

        // Then
        assertThat(exception.getMessage()).contains("Invalid value for input `jsonWithSchema`");
        assertThat(exception.getMessage()).contains("it must match the json schema");
    }
}
