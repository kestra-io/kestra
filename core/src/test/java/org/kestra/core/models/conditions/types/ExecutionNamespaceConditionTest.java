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
class ExecutionNamespaceConditionTest {
    @Inject
    ConditionService conditionService;

    @Test
    void valid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionNamespaceCondition build = ExecutionNamespaceCondition.builder()
            .namespace(flow.getNamespace())
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(true));
    }

    @Test
    void invalid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionNamespaceCondition build = ExecutionNamespaceCondition.builder()
            .namespace(flow.getNamespace() + "a")
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(false));
    }

    @Test
    void prefix() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionNamespaceCondition build = ExecutionNamespaceCondition.builder()
            .namespace(flow.getNamespace().substring(0, 3))
            .prefix(true)
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(true));
    }
}
