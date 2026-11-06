package io.kestra.executor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The behavior a delay store must honor when the cutoff is compared against the stored date
 * column, driven by a fixed anchor so the store cannot fall back on the system clock.
 */
@MicronautTest(transactional = false)
public abstract class ExecutionDelayStateStoreContract {

    // Far from the wall clock so a fixed clock can drive the store; the contract starts no Executor.
    protected static final Instant ANCHOR = Instant.parse("2031-01-15T10:00:00Z");

    private static final Instant DRAIN = ANCHOR.plusSeconds(3601);

    // Tags every row this contract creates, deterministically, so leftovers from a crashed run are
    // still recognized as ours and cleared.
    protected static final String OWNED_EXECUTION_ID_PREFIX = "delay-contract-";

    // TimeZone.setDefault is process-wide, so the zone is taken and given back under one lock.
    private static final ReentrantLock ZONE_LOCK = new ReentrantLock();

    private final ExecutionDelayStateStore executionDelayStateStore;

    private TimeZone originalTimeZone;
    private boolean zoneTaken;

    @Inject
    public ExecutionDelayStateStoreContract(ExecutionDelayStateStore executionDelayStateStore) {
        this.executionDelayStateStore = Objects.requireNonNull(executionDelayStateStore);
    }

    protected ExecutionDelayStateStore store() {
        return executionDelayStateStore;
    }

    /** The JVM zone the contract runs in; jOOQ binds the cutoff with Timestamp.valueOf, which reads it. */
    protected TimeZone testTimeZone() {
        return TimeZone.getTimeZone("UTC");
    }

    @BeforeEach
    void clearLeftoversFromEarlierClasses() {
        prepare();
        // No cleanup() on failure: JUnit runs @AfterEach even when @BeforeEach throws, and its
        // finally is the single place that gives the zone and the lock back.
        drainOwnedDelays(DRAIN);
    }

    @AfterEach
    void drainRemainingDelays() {
        try {
            drainOwnedDelays(DRAIN);
        } finally {
            cleanup();
        }
    }

    private void prepare() {
        ZONE_LOCK.lock();
        zoneTaken = true;
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(testTimeZone());
    }

    private void cleanup() {
        // Idempotent: a second call must not unlock the ReentrantLock again, or the
        // IllegalMonitorStateException would mask the failure that triggered the cleanup.
        if (!zoneTaken) {
            return;
        }
        zoneTaken = false;
        try {
            TimeZone.setDefault(originalTimeZone);
        } finally {
            ZONE_LOCK.unlock();
        }
    }

    /**
     * Consumes the delays due at or before {@code cutoff} and returns this contract's own rows.
     * The store can only select by date, so rows another suite owns are consumed too; they are
     * saved back untouched, which is safe because the test task runs a single fork.
     */
    protected List<ExecutionDelay> drainOwnedDelays(Instant cutoff) {
        List<ExecutionDelay> owned = new ArrayList<>();
        List<ExecutionDelay> foreign = new ArrayList<>();
        store().processExpired(cutoff, delay -> (isOwnedByThisContract(delay) ? owned : foreign).add(delay));
        foreign.forEach(store()::save);
        return owned;
    }

    private static boolean isOwnedByThisContract(ExecutionDelay delay) {
        return delay.getExecutionId() != null && delay.getExecutionId().startsWith(OWNED_EXECUTION_ID_PREFIX);
    }

    @Test
    void shouldConsumeNothingWhenEveryDelayIsInTheFuture() {
        store().save(delay("future", ANCHOR.plusSeconds(3600)));

        assertThat(drainOwnedDelays(ANCHOR)).isEmpty();
    }

    @Test
    void shouldConsumeOnlyTheDelaysDueAtOrBeforeTheGivenInstant() {
        store().save(delay("overdue", ANCHOR.minusSeconds(3600)));
        store().save(delay("not-yet-due", ANCHOR.plusSeconds(3600)));

        List<ExecutionDelay> consumed = drainOwnedDelays(ANCHOR);

        assertThat(consumed).extracting(ExecutionDelay::getTaskRunId).containsOnly("overdue");
    }

    @Test
    void shouldConsumeADelayDueExactlyAtTheGivenInstant() {
        store().save(delay("due-now", ANCHOR));

        List<ExecutionDelay> consumed = drainOwnedDelays(ANCHOR);

        assertThat(consumed).extracting(ExecutionDelay::getTaskRunId).containsOnly("due-now");
    }

    @Test
    void shouldConsumeTheRemainingDelaysOnALaterInstant() {
        store().save(delay("overdue", ANCHOR.minusSeconds(3600)));
        store().save(delay("not-yet-due", ANCHOR.plusSeconds(3600)));
        drainOwnedDelays(ANCHOR);

        List<ExecutionDelay> consumed = drainOwnedDelays(ANCHOR.plusSeconds(7200));

        assertThat(consumed).extracting(ExecutionDelay::getTaskRunId).containsOnly("not-yet-due");
    }

    @Test
    void shouldNotConsumeADelayTwiceWhenItHasAlreadyBeenProcessed() {
        store().save(delay("overdue", ANCHOR.minusSeconds(3600)));
        drainOwnedDelays(ANCHOR);

        assertThat(drainOwnedDelays(ANCHOR)).isEmpty();
    }

    @Test
    void shouldLeaveDelaysAnotherSuiteOwnsInTheDatabase() {
        store().save(otherSuiteDelay("other-suite", ANCHOR.minusSeconds(7200)));

        drainOwnedDelays(DRAIN);

        // The drain re-saves rows it does not own instead of deleting them, so the row is still
        // there. Other suites may share the database, so only its survival is asserted.
        List<ExecutionDelay> remaining = new ArrayList<>();
        store().processExpired(DRAIN, remaining::add);
        remaining.forEach(store()::save);
        assertThat(remaining).extracting(ExecutionDelay::getTaskRunId).contains("other-suite");
    }

    protected static ExecutionDelay delay(String taskRunId, Instant date) {
        return ExecutionDelay.builder()
            .taskRunId(taskRunId)
            .executionId(OWNED_EXECUTION_ID_PREFIX + taskRunId)
            .date(date)
            .state(State.Type.RUNNING)
            .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
            .build();
    }

    private static ExecutionDelay otherSuiteDelay(String taskRunId, Instant date) {
        return ExecutionDelay.builder()
            .taskRunId(taskRunId)
            .executionId("another-suite-" + taskRunId)
            .date(date)
            .state(State.Type.RUNNING)
            .delayType(ExecutionDelay.DelayType.RESUME_FLOW)
            .build();
    }
}
