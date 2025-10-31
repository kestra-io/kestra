package io.kestra.plugin.core.condition;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class ExecutionOutputsTest {
    @Inject
    ConditionService conditionService;

    @Test
    void shouldEvaluateToTrueGivenValidExpression() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(
            flow,
            Map.of(),
            Map.of("test", "value"));

        ExecutionOutputs build = ExecutionOutputs.builder()
            .expression(Property.ofExpression("{{ trigger.outputs.test == 'value' }}"))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test).isTrue();
    }

    @Test
    void shouldEvaluateToFalseGivenInvalidExpression() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(
            flow,
            Map.of(),
            Map.of("test", "value"));

        ExecutionOutputs build = ExecutionOutputs.builder()
            .expression(Property.ofExpression("{{ unknown is defined }}"))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test).isFalse();
    }

    @Test
    void shouldEvaluateToFalseGivenExecutionWithNoOutputs() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        ExecutionOutputs build = ExecutionOutputs.builder()
            .expression(Property.ofExpression("{{ not evaluated }}"))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test).isFalse();
    }
}
