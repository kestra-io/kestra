package org.kestra.core.models.conditions.types;

import com.google.common.collect.ImmutableMap;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.utils.TestsUtils;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ExecutionStatusConditionTest {
    @Test
    void in() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionStatusCondition build = ExecutionStatusCondition.builder()
            .in(Collections.singletonList(State.Type.SUCCESS))
            .build();

        boolean test = build.test(flow, execution);

        assertThat(test, is(false));
    }

    @Test
    void notIn() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionStatusCondition build = ExecutionStatusCondition.builder()
            .notIn(Collections.singletonList(State.Type.SUCCESS))
            .build();

        boolean test = build.test(flow, execution);

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

        boolean test = build.test(flow, execution);

        assertThat(test, is(false));
    }
}
