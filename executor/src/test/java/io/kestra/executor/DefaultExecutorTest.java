package io.kestra.executor;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.plugin.core.flow.Subflow;

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

    @Test
    void shouldReleaseTriggerLockWhenTriggerIsPolling() {
        // Given
        FlowWithSource flow = flowWith(TestPollingTrigger.builder().id("polling").type(TestPollingTrigger.class.getName()).build());
        Execution execution = executionWithTrigger("polling", TestPollingTrigger.class.getName());

        // When - Then
        assertThat(DefaultExecutor.shouldReleaseTriggerLock(flow, execution)).isTrue();
    }

    @Test
    void shouldNotReleaseTriggerLockWhenTriggerIsUnscheduled() {
        // Given a trigger the scheduler holds a state for but never evaluates: terminating its execution
        // must not release a lock it never took, nor apply stopAfter to it
        FlowWithSource flow = flowWith(TestUnscheduledTrigger.builder().id("unscheduled").type(TestUnscheduledTrigger.class.getName()).build());
        Execution execution = executionWithTrigger("unscheduled", TestUnscheduledTrigger.class.getName());

        // When - Then
        assertThat(DefaultExecutor.shouldReleaseTriggerLock(flow, execution)).isFalse();
    }

    @Test
    void shouldNotReleaseTriggerLockForASubflowExecution() {
        // Given a subflow execution, which records the parent task rather than a trigger and has no state
        FlowWithSource flow = flowWith(TestPollingTrigger.builder().id("polling").type(TestPollingTrigger.class.getName()).build());
        Execution execution = executionWithTrigger("parent-task", Subflow.class.getName());

        // When - Then
        assertThat(DefaultExecutor.shouldReleaseTriggerLock(flow, execution)).isFalse();
    }

    @Test
    void shouldReleaseTriggerLockWhenTheFlowCarriesNoTriggers() {
        // Given a flow that no longer parses: FlowWithException carries no triggers, so a polling trigger
        // cannot be resolved. Skipping the release here would strand it locked and never scheduled again.
        Execution execution = executionWithTrigger("polling", TestPollingTrigger.class.getName());

        // When - Then
        assertThat(DefaultExecutor.shouldReleaseTriggerLock(FlowWithException.builder().id("flow").namespace("io.kestra.tests").revision(1).build(), execution)).isTrue();
        assertThat(DefaultExecutor.shouldReleaseTriggerLock(null, execution)).isTrue();
    }

    @Test
    void shouldReleaseTriggerLockWhenTheTriggerWasRemovedFromTheFlow() {
        // Given a trigger dropped from the flow while its execution was running: its state can still hold the
        // lock, so the termination has to release it
        FlowWithSource flow = flowWith(TestPollingTrigger.builder().id("polling").type(TestPollingTrigger.class.getName()).build());
        Execution execution = executionWithTrigger("removed", TestPollingTrigger.class.getName());

        // When - Then
        assertThat(DefaultExecutor.shouldReleaseTriggerLock(flow, execution)).isTrue();
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

    @Plugin(internal = true)
    @SuperBuilder
    @NoArgsConstructor
    public static class TestUnscheduledTrigger extends AbstractTrigger {
    }
}
