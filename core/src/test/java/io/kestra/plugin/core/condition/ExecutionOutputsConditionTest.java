package io.kestra.plugin.core.condition;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class ExecutionOutputsConditionTest {
    @Inject
    ConditionService conditionService;

    @Test
    void shouldEvaluateToTrueGivenValidExpression() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(
            flow,
            Map.of(),
            Map.of("test", "value"));

        ExecutionOutputsCondition build = ExecutionOutputsCondition.builder()
            .expression("{{ trigger.outputs.test == 'value' }}")
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(true));
    }

    @Test
    void shouldEvaluateToFalseGivenInvalidExpression() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(
            flow,
            Map.of(),
            Map.of("test", "value"));

        ExecutionOutputsCondition build = ExecutionOutputsCondition.builder()
            .expression("{{ unknown is defined }}")
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(false));
    }

    @Test
    void shouldEvaluateToFalseGivenExecutionWithNoOutputs() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        ExecutionOutputsCondition build = ExecutionOutputsCondition.builder()
            .expression("{{ not evaluated }}")
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(false));
    }
}
