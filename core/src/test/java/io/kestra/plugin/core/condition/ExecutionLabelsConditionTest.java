package io.kestra.plugin.core.condition;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class ExecutionLabelsConditionTest {
    @Inject
    ConditionService conditionService;

    @Test
    void valid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils
            .mockExecution(flow, ImmutableMap.of())
            .toBuilder()
            .labels(List.of(new Label("key", "value")))
            .build();

        ExecutionLabelsCondition build = ExecutionLabelsCondition.builder()
            .labels(List.of(new Label("key", "value")))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(true));
    }

    @Test
    void validMultiples() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils
            .mockExecution(flow, ImmutableMap.of())
            .toBuilder()
            .labels(List.of(new Label("key", "value"), new Label("key2", "value2")))
            .build();

        ExecutionLabelsCondition build = ExecutionLabelsCondition.builder()
            .labels(List.of(new Label("key", "value"), new Label("key2", "value2")))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(true));
    }

    @Test
    void invalid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils
            .mockExecution(flow, ImmutableMap.of());

        ExecutionLabelsCondition build = ExecutionLabelsCondition.builder()
            .labels(List.of(new Label("key", "value")))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(false));
    }

    @Test
    void invalidMultiples() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils
            .mockExecution(flow, ImmutableMap.of())
            .toBuilder()
            .labels(List.of(new Label("key", "value")))
            .build();

        ExecutionLabelsCondition build = ExecutionLabelsCondition.builder()
            .labels(List.of(new Label("key", "value"), new Label("key2", "value2")))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(false));
    }
}
