package io.kestra.worker.processors.internals;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.TimeoutExceededException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.runners.WorkerTriggerData;
import io.kestra.core.tasks.test.SleepTrigger;

import jakarta.inject.Inject;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

@KestraTest
class WorkerTriggerCallableTest {
    @Inject
    RunContextInitializer runContextInitializer;

    @Test
    void shouldInterruptAndFailWhenEvaluationExceedsTimeout() {
        // Given: a polling trigger whose evaluation blocks far longer than the timeout
        SleepTrigger trigger = SleepTrigger.builder()
            .id("sleep")
            .type(SleepTrigger.class.getName())
            .duration(Duration.ofMinutes(5).toMillis())
            .build();
        WorkerTriggerCallable callable = callable(trigger, Duration.ofMillis(300));

        // When
        State.Type state = assertTimeout(Duration.ofSeconds(5), callable::call);

        // Then: the evaluation is interrupted, marked failed, and carries a TimeoutExceededException
        // (the caller emits an error trigger result on this, which releases the scheduler lock).
        // The FAILED state makes the processor route to handleTriggerError (which releases the lock)
        // and skip publishTriggerExecution, so any partial getEvaluate() value is irrelevant here.
        assertThat(state).isEqualTo(State.Type.FAILED);
        assertThat(callable.getException()).isInstanceOf(TimeoutExceededException.class);
    }

    @Test
    void shouldKillAndFreeWorkerWhenEvaluationIgnoresInterrupt() {
        // Given: an evaluation that swallows Thread.interrupt() — only plugin kill() unblocks it
        HangUntilKilledTrigger trigger = HangUntilKilledTrigger.builder()
            .id("hang")
            .type(HangUntilKilledTrigger.class.getName())
            .build();
        WorkerTriggerCallable callable = callable(trigger, Duration.ofMillis(200));

        // When: timeout must invoke kill() while eval is still blocked, unblocking the worker
        State.Type state = assertTimeout(Duration.ofSeconds(5), callable::call);

        // Then
        assertThat(state).isEqualTo(State.Type.FAILED);
        assertThat(callable.getException()).isInstanceOf(TimeoutExceededException.class);
        assertThat(trigger.wasKilled()).isTrue();
    }

    @Test
    void shouldSucceedWhenEvaluationCompletesWithinTimeout() {
        // Given: a fast evaluation with a generous timeout
        SleepTrigger trigger = SleepTrigger.builder()
            .id("sleep")
            .type(SleepTrigger.class.getName())
            .duration(1L)
            .build();
        WorkerTriggerCallable callable = callable(trigger, Duration.ofSeconds(30));

        // When
        State.Type state = callable.call();

        // Then
        assertThat(state).isEqualTo(State.Type.SUCCESS);
        assertThat(callable.getException()).isNull();
        assertThat(callable.getEvaluate()).isEqualTo(Optional.empty());
    }

    private WorkerTriggerCallable callable(AbstractTrigger trigger, Duration timeout) {
        WorkerTrigger workerTrigger = WorkerTrigger.builder()
            .trigger(trigger)
            .data(new WorkerTriggerData(
                "tenant", "io.kestra.tests", "flow", ZonedDateTime.now(), null, null, null,
                Collections.emptyMap(), null, Collections.emptyMap()
            ))
            .build();

        // Build the callable inputs exactly like WorkerTriggerProcessor#doProcess.
        ConditionContext conditionContext = runContextInitializer.forWorker(workerTrigger);
        TriggerContext triggerContext = TriggerContext.of(workerTrigger);
        RunContext runContext = conditionContext.getRunContext();

        return new WorkerTriggerCallable(runContext, conditionContext, triggerContext, workerTrigger, (PollingTriggerInterface) trigger, timeout);
    }

    /**
     * Polling trigger whose {@code eval()} ignores interrupt and only returns after {@link #kill()}.
     */
    @SuperBuilder
    @Getter
    @NoArgsConstructor
    public static class HangUntilKilledTrigger extends AbstractTrigger implements PollingTriggerInterface {
        @Builder.Default
        private final AtomicBoolean killed = new AtomicBoolean(false);

        @Override
        public Optional<TriggerEvaluationResult> eval(ConditionContext conditionContext, TriggerContext context) {
            while (!killed.get()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                    // Swallow interrupt — only kill() unblocks this evaluation.
                }
            }
            return Optional.empty();
        }

        @Override
        public void kill() {
            killed.set(true);
        }

        public boolean wasKilled() {
            return killed.get();
        }

        @Override
        public Duration getInterval() {
            return Duration.ofSeconds(1);
        }
    }
}
