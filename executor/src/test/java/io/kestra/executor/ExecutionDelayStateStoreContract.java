package io.kestra.executor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioral contract every {@link ExecutionDelayStateStore} must honour — annotation-free so it can
 * run against the container-injected JDBC stores ({@link AbstractExecutionDelayStateStoreTest}) and
 * against hand-built ones, keeping fakes provably faithful to the production implementations.
 *
 * <p>The anchor sits in 2031 on purpose. Each backend derives the {@code date} column from the stored
 * JSON with its own generated-column expression, so a store that binds the system clock instead of the
 * instant it is handed matches nothing here and fails loudly, while a store that binds that instant
 * against the wrong time representation silently shifts which rows match.
 *
 * <p>{@code processExpired} invokes its consumer inside the retryable transaction of
 * {@code JooqDSLContextWrapper}, so a retried attempt can deliver the same delay twice; the assertions
 * below therefore pin which delays arrive, not how many times.
 */
public abstract class ExecutionDelayStateStoreContract {

    protected static final Instant ANCHOR = Instant.parse("2031-01-15T10:00:00Z");

    private static final Instant DRAIN = Instant.parse("2040-01-01T00:00:00Z");

    /** The {@link ExecutionDelayStateStore} implementation under contract. */
    protected abstract ExecutionDelayStateStore store();

    /** Test runs are sequential in a single JVM (build.gradle:321), so draining once per test is enough. */
    @AfterEach
    void drainRemainingDelays() {
        store().processExpired(DRAIN, delay -> { });
    }

    @Test
    void shouldConsumeNothingWhenEveryDelayIsInTheFuture() {
        // Given
        store().save(delay("future", ANCHOR.plusSeconds(3600)));

        // When
        List<ExecutionDelay> consumed = new ArrayList<>();
        store().processExpired(ANCHOR, consumed::add);

        // Then
        assertThat(consumed).isEmpty();
    }

    @Test
    void shouldConsumeOnlyTheDelaysDueAtOrBeforeTheGivenInstant() {
        // Given
        store().save(delay("overdue", ANCHOR.minusSeconds(3600)));
        store().save(delay("not-yet-due", ANCHOR.plusSeconds(3600)));

        // When
        List<ExecutionDelay> consumed = new ArrayList<>();
        store().processExpired(ANCHOR, consumed::add);

        // Then
        assertThat(consumed).extracting(ExecutionDelay::getTaskRunId).containsOnly("overdue");
    }

    @Test
    void shouldConsumeTheRemainingDelaysOnALaterInstant() {
        // Given: an earlier poll cannot see the second delay yet
        store().save(delay("overdue", ANCHOR.minusSeconds(3600)));
        store().save(delay("not-yet-due", ANCHOR.plusSeconds(3600)));
        store().processExpired(ANCHOR, delay -> { });

        // When
        List<ExecutionDelay> consumed = new ArrayList<>();
        store().processExpired(ANCHOR.plusSeconds(7200), consumed::add);

        // Then
        assertThat(consumed).extracting(ExecutionDelay::getTaskRunId).containsOnly("not-yet-due");
    }

    @Test
    void shouldNotConsumeADelayTwiceWhenItHasAlreadyBeenProcessed() {
        // Given
        store().save(delay("overdue", ANCHOR.minusSeconds(3600)));
        store().processExpired(ANCHOR, delay -> { });

        // When
        List<ExecutionDelay> consumed = new ArrayList<>();
        store().processExpired(ANCHOR, consumed::add);

        // Then
        assertThat(consumed).isEmpty();
    }

    private static ExecutionDelay delay(String taskRunId, Instant date) {
        return ExecutionDelay.builder()
            .taskRunId(taskRunId)
            .executionId("execution-" + taskRunId)
            .date(date)
            .state(State.Type.RUNNING)
            .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
            .build();
    }
}
