package io.kestra.plugin.core.condition;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.junit.annotations.KestraTest;
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
class MultipleConditionTest {
    @Inject
    ConditionService conditionService;

    @Inject
    MultipleConditionStorageInterface multipleConditionStorage;

    @Test
    void simple() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        MultipleCondition build = MultipleCondition.builder()
            .conditions(
                ImmutableMap.of(
                "first", ExecutionStatus.builder()
                    .in(Property.ofValue(Collections.singletonList(State.Type.SUCCESS)))
                    .build(),
                "second", Expression.builder()
                    .expression(Property.ofExpression("{{ flow.id }}"))
                    .build()
            ))
            .build();

        boolean test = conditionService.isValid((Condition) build, flow, execution, multipleConditionStorage);


        assertThat(test).isFalse();
    }
}