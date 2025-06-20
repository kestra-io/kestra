package io.kestra.plugin.core.condition;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.property.Property;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class ExecutionNamespaceTest {
    @Inject
    ConditionService conditionService;

    @Test
    void valid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionNamespace build = ExecutionNamespace.builder()
            .namespace(Property.ofValue(flow.getNamespace()))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test).isTrue();

        // Explicit
        build = ExecutionNamespace.builder()
            .namespace(Property.ofValue(flow.getNamespace()))
            .comparison(Property.ofValue(ExecutionNamespace.Comparison.EQUALS))
            .build();

        test = conditionService.isValid(build, flow, execution);
        assertThat(test).isTrue();
    }

    @Test
    void invalid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionNamespace build = ExecutionNamespace.builder()
            .namespace(Property.ofValue(flow.getNamespace() + "a"))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test).isFalse();
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
        assertThat(test).isTrue();

        build = ExecutionNamespace.builder()
            .namespace(Property.ofValue(flow.getNamespace().substring(0, 3)))
            .comparison(Property.ofValue(ExecutionNamespace.Comparison.PREFIX))
            .build();

        test = conditionService.isValid(build, flow, execution);
        assertThat(test).isTrue();

        build = ExecutionNamespace.builder()
            .namespace(Property.ofValue(flow.getNamespace().substring(0, 3)))
            .prefix(Property.ofValue(true))
            .build();

        test = conditionService.isValid(build, flow, execution);
        assertThat(test).isTrue();
    }

    @Test
    void defaultBehaviour() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        // Should use EQUALS if prefix is not set
        ExecutionNamespace build = ExecutionNamespace.builder()
            .namespace(Property.ofValue(flow.getNamespace().substring(0, 3)))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);
        assertThat(test).isFalse();
    }

    @Test
    void suffix() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionNamespace build = ExecutionNamespace.builder()
            .namespace(Property.ofValue(flow.getNamespace().substring(flow.getNamespace().length() - 4)))
            .comparison(Property.ofValue(ExecutionNamespace.Comparison.SUFFIX))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);
        assertThat(test).isTrue();
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
        assertThat(test).isTrue();
    }
}
