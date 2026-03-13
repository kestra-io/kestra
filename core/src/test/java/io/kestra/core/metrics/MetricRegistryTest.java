package io.kestra.core.metrics;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.IdUtils;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@MicronautTest
@org.junit.jupiter.api.parallel.Execution(ExecutionMode.SAME_THREAD)
class MetricRegistryTest {
    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    private MetricConfig mockConfig;

    @MockBean(MetricConfig.class)
    MetricConfig mockMetricConfig() {
        return mock(MetricConfig.class);
    }

    @Test
    void executionTagsNoLabelsConfigured() {
        when(mockConfig.getLabels()).thenReturn(
            List.of()
        );

        var execution = Execution.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .flowId("flow")
            .flowRevision(1)
            .state(State.of(State.Type.SUCCESS, Collections.emptyList()))
            .labels(List.of(
                new Label("execution-label-foo", "bar"),
                new Label(Label.CORRELATION_ID, "correlationId")
            ))
            .build();
        var tags = metricRegistry.tags(execution);

        assertThat(tags).containsExactly(
            "flow_id", "flow",
            "namespace_id", "io.kestra.unittest",
            "state", "SUCCESS"
        );
    }

    @Test
    void executionTagsLabelsConfigured() {
        when(mockConfig.getLabels()).thenReturn(
            List.of("execution-label-foo")
        );

        var executionContainingConfiguredLabel = Execution.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .flowId("flow")
            .flowRevision(1)
            .state(State.of(State.Type.SUCCESS, Collections.emptyList()))
            .labels(List.of(
                new Label("execution-label-foo", "test1"),
                new Label("execution-label-bar", "test2"),
                new Label(Label.CORRELATION_ID, "correlationId")
            ))
            .build();

        assertThat(metricRegistry.tags(executionContainingConfiguredLabel)).containsExactly(
            "flow_id", "flow",
            "namespace_id", "io.kestra.unittest",
            "state", "SUCCESS",
            "label_execution-label-foo",
            "test1"
        );

        var executionNotContainingConfiguredLabel = Execution.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .flowId("flow")
            .flowRevision(1)
            .state(State.of(State.Type.SUCCESS, Collections.emptyList()))
            .labels(List.of(
                new Label("execution-label-bar", "test2"),
                new Label(Label.CORRELATION_ID, "correlationId")
            ))
            .build();

        assertThat(metricRegistry.tags(executionNotContainingConfiguredLabel)).containsExactly(
            "flow_id", "flow",
            "namespace_id", "io.kestra.unittest",
            "state", "SUCCESS",
            "label_execution-label-foo",
            "__none__"
        );
    }
}