package io.kestra.plugin.core.condition;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.TestsUtils;

import java.util.Collections;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class ExecutionStatusTest {
    @Inject
    ConditionService conditionService;

    @Test
    void in() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionStatus build = ExecutionStatus.builder()
            .in(Property.ofValue(Collections.singletonList(State.Type.SUCCESS)))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test).isFalse();
    }

    @Test
    void notIn() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionStatus build = ExecutionStatus.builder()
            .notIn(Property.ofValue(Collections.singletonList(State.Type.SUCCESS)))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test).isTrue();
    }

    @Test
    void both() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionStatus build = ExecutionStatus.builder()
            .in(Property.ofValue(Collections.singletonList(State.Type.CREATED)))
            .notIn(Property.ofValue(Collections.singletonList(State.Type.SUCCESS)))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test).isFalse();
    }
}
