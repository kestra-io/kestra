package io.kestra.plugin.core.condition;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.TestsUtils;

import java.util.Collections;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class ExecutionStatusConditionTest {
    @Inject
    ConditionService conditionService;

    @Test
    void in() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionStatusCondition build = ExecutionStatusCondition.builder()
            .in(Collections.singletonList(State.Type.SUCCESS))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(false));
    }

    @Test
    void notIn() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionStatusCondition build = ExecutionStatusCondition.builder()
            .notIn(Collections.singletonList(State.Type.SUCCESS))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(true));
    }

    @Test
    void both() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionStatusCondition build = ExecutionStatusCondition.builder()
            .in(Collections.singletonList(State.Type.CREATED))
            .notIn(Collections.singletonList(State.Type.SUCCESS))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(false));
    }
}
