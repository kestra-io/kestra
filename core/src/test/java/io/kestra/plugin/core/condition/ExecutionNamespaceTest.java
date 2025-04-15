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
            .namespace(Property.of(flow.getNamespace()))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test).isEqualTo(true);

        // Explicit
        build = ExecutionNamespace.builder()
            .namespace(Property.of(flow.getNamespace()))
            .comparison(Property.of(ExecutionNamespace.Comparison.EQUALS))
            .build();

        test = conditionService.isValid(build, flow, execution);
        assertThat(test).isEqualTo(true);
    }

    @Test
    void invalid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionNamespace build = ExecutionNamespace.builder()
            .namespace(Property.of(flow.getNamespace() + "a"))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test).isEqualTo(false);
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
        assertThat(test).isEqualTo(true);

        build = ExecutionNamespace.builder()
            .namespace(Property.of(flow.getNamespace().substring(0, 3)))
            .comparison(Property.of(ExecutionNamespace.Comparison.PREFIX))
            .build();

        test = conditionService.isValid(build, flow, execution);
        assertThat(test).isEqualTo(true);

        build = ExecutionNamespace.builder()
            .namespace(Property.of(flow.getNamespace().substring(0, 3)))
            .prefix(Property.of(true))
            .build();

        test = conditionService.isValid(build, flow, execution);
        assertThat(test).isEqualTo(true);
    }

    @Test
    void defaultBehaviour() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        // Should use EQUALS if prefix is not set
        ExecutionNamespace build = ExecutionNamespace.builder()
            .namespace(Property.of(flow.getNamespace().substring(0, 3)))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);
        assertThat(test).isEqualTo(false);
    }

    @Test
    void suffix() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ExecutionNamespace build = ExecutionNamespace.builder()
            .namespace(Property.of(flow.getNamespace().substring(flow.getNamespace().length() - 4)))
            .comparison(Property.of(ExecutionNamespace.Comparison.SUFFIX))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);
        assertThat(test).isEqualTo(true);
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
        assertThat(test).isEqualTo(true);
    }
}
