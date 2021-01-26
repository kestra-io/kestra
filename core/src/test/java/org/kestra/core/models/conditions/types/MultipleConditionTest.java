package org.kestra.core.models.conditions.types;

import com.google.common.collect.ImmutableMap;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import org.kestra.core.services.ConditionService;
import org.kestra.core.utils.TestsUtils;

import java.util.Collections;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
@MicronautTest
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
                "second", VariableCondition.builder()
                    .expression("{{ flow.id }}")
                    .build()
            ))
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        
        assertThat(test, is(false));
    }
}