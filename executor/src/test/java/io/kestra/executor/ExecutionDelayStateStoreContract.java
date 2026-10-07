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

    // Far from the wall clock so a fixed clock can drive the store and no real-clock poller
    // reaches these rows; the contract starts no Executor and stores nothing else in this table.
    protected static final Instant ANCHOR = Instant.parse("2031-01-15T10:00:00Z");

    // Just past the latest date this contract inserts, so teardown cannot delete a
    // pending delay another suite left in the shared kestra_unit database.
    private static final Instant DRAIN = ANCHOR.plusSeconds(3601);

    protected abstract ExecutionDelayStateStore store();

    @BeforeEach
    void clearLeftoversFromEarlierClasses() {
        store().processExpired(DRAIN, delay -> { });
    }

    @AfterEach
    void drainRemainingDelays() {
        store().processExpired(DRAIN, delay -> { });
    }

    @Test
    void shouldConsumeNothingWhenEveryDelayIsInTheFuture() {
        store().save(delay("future", ANCHOR.plusSeconds(3600)));

        List<ExecutionDelay> consumed = new ArrayList<>();
        store().processExpired(ANCHOR, consumed::add);

        assertThat(consumed).isEmpty();
    }

    @Test
    void shouldConsumeOnlyTheDelaysDueAtOrBeforeTheGivenInstant() {
        store().save(delay("overdue", ANCHOR.minusSeconds(3600)));
        store().save(delay("not-yet-due", ANCHOR.plusSeconds(3600)));

        List<ExecutionDelay> consumed = new ArrayList<>();
        store().processExpired(ANCHOR, consumed::add);

        assertThat(consumed).extracting(ExecutionDelay::getTaskRunId).containsOnly("overdue");
    }

    @Test
    void shouldConsumeADelayDueExactlyAtTheGivenInstant() {
        store().save(delay("due-now", ANCHOR));

        List<ExecutionDelay> consumed = new ArrayList<>();
        store().processExpired(ANCHOR, consumed::add);

        assertThat(consumed).extracting(ExecutionDelay::getTaskRunId).containsOnly("due-now");
    }

    @Test
    void shouldConsumeTheRemainingDelaysOnALaterInstant() {
        store().save(delay("overdue", ANCHOR.minusSeconds(3600)));
        store().save(delay("not-yet-due", ANCHOR.plusSeconds(3600)));
        store().processExpired(ANCHOR, delay -> { });

        List<ExecutionDelay> consumed = new ArrayList<>();
        store().processExpired(ANCHOR.plusSeconds(7200), consumed::add);

        assertThat(consumed).extracting(ExecutionDelay::getTaskRunId).containsOnly("not-yet-due");
    }

    @Test
    void shouldNotConsumeADelayTwiceWhenItHasAlreadyBeenProcessed() {
        store().save(delay("overdue", ANCHOR.minusSeconds(3600)));
        store().processExpired(ANCHOR, delay -> { });

        List<ExecutionDelay> consumed = new ArrayList<>();
        store().processExpired(ANCHOR, consumed::add);

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
