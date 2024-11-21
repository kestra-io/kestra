package io.kestra.plugin.core.condition;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class ExecutionNamespaceTest {
    @Inject
    ConditionService conditionService;

    @Test
    void valid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionNamespace build = ExecutionNamespace.builder()
            .namespace(flow.getNamespace())
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(true));

        // Explicit
        build = ExecutionNamespace.builder()
            .namespace(flow.getNamespace())
            .comparison(ExecutionNamespace.Comparison.EQUALS)
            .build();

        test = conditionService.isValid(build, flow, execution);
        assertThat(test, is(true));
    }

    @Test
    void invalid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionNamespace build = ExecutionNamespace.builder()
            .namespace(flow.getNamespace() + "a")
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(false));
    }

    @Test
    void prefix() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionNamespace build = JacksonMapper.toMap(Map.of(
            "type", ExecutionNamespace.class.getName(),
            "namespace", flow.getNamespace().substring(0, 3),
            "prefix", true
        ), ExecutionNamespace.class);

        boolean test = conditionService.isValid(build, flow, execution);
        assertThat(test, is(true));

        build = ExecutionNamespace.builder()
            .namespace(flow.getNamespace().substring(0, 3))
            .comparison(ExecutionNamespace.Comparison.PREFIX)
            .build();

        test = conditionService.isValid(build, flow, execution);
        assertThat(test, is(true));

        build = ExecutionNamespace.builder()
            .namespace(flow.getNamespace().substring(0, 3))
            .prefix(true)
            .build();

        test = conditionService.isValid(build, flow, execution);
        assertThat(test, is(true));
    }

    @Test
    void defaultBehaviour() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        // Should use EQUALS if prefix is not set
        ExecutionNamespace build = ExecutionNamespace.builder()
            .namespace(flow.getNamespace().substring(0, 3))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);
        assertThat(test, is(false));
    }

    @Test
    void suffix() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionNamespace build = ExecutionNamespace.builder()
            .namespace(flow.getNamespace().substring(flow.getNamespace().length() - 4))
            .comparison(ExecutionNamespace.Comparison.SUFFIX)
            .build();

        boolean test = conditionService.isValid(build, flow, execution);
        assertThat(test, is(true));
    }

    @Test
    void comparisonMismatchShouldPreferComparisonProperty() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionNamespace build = JacksonMapper.toMap(Map.of(
            "type", ExecutionNamespace.class.getName(),
            "namespace", flow.getNamespace().substring(flow.getNamespace().length() - 4),
            "prefix", true,
            "comparison", ExecutionNamespace.Comparison.SUFFIX.name()
        ), ExecutionNamespace.class);

        boolean test = conditionService.isValid(build, flow, execution);
        assertThat(test, is(true));
    }
}
