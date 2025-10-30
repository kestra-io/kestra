package io.kestra.plugin.core.condition;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class ExpressionTest {
    @Inject
    ConditionService conditionService;

    @Test
    void valid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of("test", "value"));

        Expression build = Expression.builder()
            .expression(Property.ofExpression("{{ flow.id }}"))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test).isTrue();
    }

    @Test
    void invalid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of("test", "value"));

        Expression build = Expression.builder()
            .expression(Property.ofExpression("{{ unknown is defined }}"))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test).isFalse();
    }
}
