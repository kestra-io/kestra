package io.kestra.core.models.conditions.types;

import com.google.common.collect.ImmutableMap;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

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

        // Explicit
        build = ExecutionNamespaceCondition.builder()
            .namespace(flow.getNamespace())
            .comparison(ExecutionNamespaceCondition.Comparison.EQUALS)
            .build();

        test = conditionService.isValid(build, flow, execution);
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

        build = ExecutionNamespaceCondition.builder()
            .namespace(flow.getNamespace().substring(0, 3))
            .comparison(ExecutionNamespaceCondition.Comparison.PREFIX)
            .build();

        test = conditionService.isValid(build, flow, execution);
        assertThat(test, is(true));
    }

    @Test
    void defaultBehaviour() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionNamespaceCondition build = ExecutionNamespaceCondition.builder()
            .namespace(flow.getNamespace().substring(0, 3))
            .prefix(true)
            .build();

        boolean test = conditionService.isValid(build, flow, execution);
        assertThat(test, is(true));

        // Should use EQUALS if prefix is not set
        build = ExecutionNamespaceCondition.builder()
            .namespace(flow.getNamespace().substring(0, 3))
            .build();

        test = conditionService.isValid(build, flow, execution);
        assertThat(test, is(false));
    }

    @Test
    void suffix() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionNamespaceCondition build = ExecutionNamespaceCondition.builder()
            .namespace(flow.getNamespace().substring(flow.getNamespace().length() - 4))
            .comparison(ExecutionNamespaceCondition.Comparison.SUFFIX)
            .build();

        boolean test = conditionService.isValid(build, flow, execution);
        assertThat(test, is(true));
    }
}
