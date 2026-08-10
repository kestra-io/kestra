package io.kestra.jdbc;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.WorkerTriggerResult;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcWorkerTriggerResultQueueServiceTest {

    @Test
    void shouldReleaseRunningEntryWhenPollingTriggerEmitsAnExecution() {
        // Given
        WorkerTriggerResult result = resultOf(pollingTrigger(), execution(State.Type.CREATED));

        // When - Then
        assertThat(JdbcWorkerTriggerResultQueueService.isTerminalTriggerResult(result)).isTrue();
    }

    @Test
    void shouldReleaseRunningEntryWhenPollingTriggerEmitsNoExecution() {
        // Given
        WorkerTriggerResult result = resultOf(pollingTrigger(), null);

        // When - Then
        assertThat(JdbcWorkerTriggerResultQueueService.isTerminalTriggerResult(result)).isTrue();
    }

    @Test
    void shouldKeepRunningEntryWhenRealtimeTriggerEmitsAnExecution() {
        // Given
        // A realtime trigger sends one result per emitted execution while it keeps running,
        // so the WorkerJobRunning entry must survive for the liveness coordinator to resubmit it.
        WorkerTriggerResult result = resultOf(realtimeTrigger(), execution(State.Type.CREATED));

        // When - Then
        assertThat(JdbcWorkerTriggerResultQueueService.isTerminalTriggerResult(result)).isFalse();
    }

    @Test
    void shouldReleaseRunningEntryWhenRealtimeTriggerEmitsAFailedExecution() {
        // Given
        WorkerTriggerResult result = resultOf(realtimeTrigger(), execution(State.Type.FAILED));

        // When - Then
        assertThat(JdbcWorkerTriggerResultQueueService.isTerminalTriggerResult(result)).isTrue();
    }

    @Test
    void shouldReleaseRunningEntryWhenRealtimeTriggerEmitsNoExecution() {
        // Given
        // An error reported without an execution: the trigger is no longer running on the worker.
        WorkerTriggerResult result = resultOf(realtimeTrigger(), null);

        // When - Then
        assertThat(JdbcWorkerTriggerResultQueueService.isTerminalTriggerResult(result)).isTrue();
    }

    private static WorkerTriggerResult resultOf(AbstractTrigger trigger, Execution execution) {
        return WorkerTriggerResult.builder()
            .trigger(trigger)
            .triggerContext(
                TriggerContext.builder()
                    .namespace("io.kestra.tests")
                    .flowId("flow")
                    .triggerId(trigger.getId())
                    .build()
            )
            .execution(Optional.ofNullable(execution))
            .build();
    }

    private static Execution execution(State.Type stateType) {
        return Execution.builder()
            .id("execution")
            .namespace("io.kestra.tests")
            .flowId("flow")
            .state(new State(stateType, new State()))
            .build();
    }

    private static AbstractTrigger realtimeTrigger() {
        return TestRealtimeTrigger.builder()
            .id("realtime")
            .type(TestRealtimeTrigger.class.getName())
            .build();
    }

    private static AbstractTrigger pollingTrigger() {
        return TestPollingTrigger.builder()
            .id("polling")
            .type(TestPollingTrigger.class.getName())
            .interval(Duration.ofSeconds(30))
            .build();
    }

    @Plugin(internal = true)
    @SuperBuilder
    @NoArgsConstructor
    public static class TestRealtimeTrigger extends AbstractTrigger implements RealtimeTriggerInterface {
        @Override
        public Publisher<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) {
            throw new UnsupportedOperationException("not evaluated in this test");
        }
    }

    @Plugin(internal = true)
    @SuperBuilder
    @NoArgsConstructor
    @Getter
    public static class TestPollingTrigger extends AbstractTrigger implements PollingTriggerInterface {
        private Duration interval;

        @Override
        public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) {
            throw new UnsupportedOperationException("not evaluated in this test");
        }
    }
}
