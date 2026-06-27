package io.kestra.executor;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultExecutorTest {

    @Test
    void shouldDetectRealtimeTriggerExecutionWhenTriggerIsRealtime() {
        // Given
        FlowWithSource flow = flowWith(TestRealtimeTrigger.builder().id("realtime").type(TestRealtimeTrigger.class.getName()).build());
        Execution execution = executionWithTrigger("realtime", TestRealtimeTrigger.class.getName());

        // When - Then
        assertThat(DefaultExecutor.isRealtimeTriggerExecution(flow, execution)).isTrue();
    }

    @Test
    void shouldNotDetectRealtimeTriggerExecutionWhenTriggerIsPolling() {
        // Given
        FlowWithSource flow = flowWith(TestPollingTrigger.builder().id("polling").type(TestPollingTrigger.class.getName()).build());
        Execution execution = executionWithTrigger("polling", TestPollingTrigger.class.getName());

        // When - Then
        assertThat(DefaultExecutor.isRealtimeTriggerExecution(flow, execution)).isFalse();
    }

    @Test
    void shouldNotDetectRealtimeTriggerExecutionWhenTriggerIsNotFound() {
        // Given
        FlowWithSource flow = flowWith(TestRealtimeTrigger.builder().id("realtime").type(TestRealtimeTrigger.class.getName()).build());
        Execution execution = executionWithTrigger("removed", TestRealtimeTrigger.class.getName());

        // When - Then
        assertThat(DefaultExecutor.isRealtimeTriggerExecution(flow, execution)).isFalse();
    }

    @Test
    void shouldNotDetectRealtimeTriggerExecutionWhenFlowIsNull() {
        // Given
        Execution execution = executionWithTrigger("realtime", TestRealtimeTrigger.class.getName());

        // When - Then
        assertThat(DefaultExecutor.isRealtimeTriggerExecution(null, execution)).isFalse();
    }

    private static FlowWithSource flowWith(AbstractTrigger trigger) {
        return FlowWithSource.builder()
            .id("flow")
            .namespace("io.kestra.tests")
            .revision(1)
            .triggers(List.of(trigger))
            .build();
    }

    private static Execution executionWithTrigger(String triggerId, String triggerType) {
        return Execution.builder()
            .id("execution")
            .trigger(ExecutionTrigger.builder().id(triggerId).type(triggerType).build())
            .build();
    }

    @Plugin(internal = true)
    @SuperBuilder
    @NoArgsConstructor
    public static class TestRealtimeTrigger extends AbstractTrigger implements RealtimeTriggerInterface {
    }

    @Plugin(internal = true)
    @SuperBuilder
    @NoArgsConstructor
    @Getter
    public static class TestPollingTrigger extends AbstractTrigger implements PollingTriggerInterface {
        private Duration interval;
    }
}
