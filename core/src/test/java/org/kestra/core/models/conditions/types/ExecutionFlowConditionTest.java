package org.kestra.core.models.conditions.types;

import com.google.common.collect.ImmutableMap;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.services.ConditionService;
import org.kestra.core.utils.TestsUtils;

import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class ExecutionFlowConditionTest {
    @Inject
    ConditionService conditionService;

    @Test
    void valid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionFlowCondition build = ExecutionFlowCondition.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(true));
    }

    @Test
    void notValid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionFlowCondition build = ExecutionFlowCondition.builder()
            .namespace(flow.getNamespace() + "a")
            .flowId(flow.getId())
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(false));
    }
}
