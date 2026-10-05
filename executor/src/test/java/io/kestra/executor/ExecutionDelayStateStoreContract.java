package io.kestra.executor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ExecutionDelayStateStoreContract {

    protected static final Instant ANCHOR = Instant.parse("2031-01-15T10:00:00Z");

    private static final Instant DRAIN = Instant.parse("2040-01-01T00:00:00Z");

    protected abstract ExecutionDelayStateStore store();

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
