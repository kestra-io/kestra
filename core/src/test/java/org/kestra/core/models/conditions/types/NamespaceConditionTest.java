package org.kestra.core.models.conditions.types;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.utils.TestsUtils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class NamespaceConditionTest {
    @Test
    void valid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        NamespaceCondition build = NamespaceCondition.builder()
            .namespace(flow.getNamespace())
            .build();

        boolean test = build.test(flow, execution);

        assertThat(test, is(true));
    }

    @Test
    void invalid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        NamespaceCondition build = NamespaceCondition.builder()
            .namespace(flow.getNamespace() + "a")
            .build();

        boolean test = build.test(flow, execution);

        assertThat(test, is(false));
    }

    @Test
    void prefix() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        NamespaceCondition build = NamespaceCondition.builder()
            .namespace(flow.getNamespace().substring(0, 3))
            .prefix(true)
            .build();

        boolean test = build.test(flow, execution);

        assertThat(test, is(true));
    }
}
