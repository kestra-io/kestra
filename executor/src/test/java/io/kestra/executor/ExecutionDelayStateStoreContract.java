package io.kestra.executor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ExecutionDelayStateStoreContract {

    // Far from the wall clock so a fixed clock can drive the store; the contract starts no Executor.
    protected static final Instant ANCHOR = Instant.parse("2031-01-15T10:00:00Z");

    private static final Instant DRAIN = ANCHOR.plusSeconds(3601);

    // Tags every row this contract creates. Drains can only select by date, so they consume
    // everything due and give back whatever this prefix does not match; keeping it deterministic
    // (no per-run id) means leftovers from a crashed run are still recognized as ours and cleared.
    private static final String OWNED_EXECUTION_ID_PREFIX = "delay-contract-";

    protected abstract ExecutionDelayStateStore store();

    /** Runs around each drain, so subclasses can set up state the drain and the tests both rely on. */
    protected void prepare() {
    }

    protected void cleanup() {
    }

    @BeforeEach
    void clearLeftoversFromEarlierClasses() {
        prepare();
        // No cleanup() on failure: JUnit runs @AfterEach even when @BeforeEach throws, and its
        // finally is the single place that gives the zone and the lock back. Cleaning up here too
        // would unlock twice, and the IllegalMonitorStateException would mask the database error.
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

    /**
     * Consumes the delays due at or before {@code cutoff} and returns this contract's own rows.
     * The store can only select by date, not by key, so rows another suite owns are consumed too;
     * they are saved back untouched. That re-insert is only safe because the test task runs a
     * single fork: nothing else is consuming from these databases while a test method runs.
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

        List<ExecutionDelay> remaining = new ArrayList<>();
        store().processExpired(DRAIN, remaining::add);
        assertThat(remaining).extracting(ExecutionDelay::getTaskRunId).containsExactly("other-suite");
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
