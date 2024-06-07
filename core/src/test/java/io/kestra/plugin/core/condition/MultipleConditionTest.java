package io.kestra.plugin.core.condition;

import com.google.common.collect.ImmutableMap;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
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
                "first", ExecutionStatusCondition.builder()
                    .in(Collections.singletonList(State.Type.SUCCESS))
                    .build(),
                "second", ExpressionCondition.builder()
                    .expression("{{ flow.id }}")
                    .build()
            ))
            .build();

        boolean test = conditionService.isValid(build, flow, execution, multipleConditionStorage);


        assertThat(test, is(false));
    }
}