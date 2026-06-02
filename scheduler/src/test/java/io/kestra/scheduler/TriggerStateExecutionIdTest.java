package io.kestra.scheduler;

import java.time.Clock;

import io.kestra.core.models.flows.State;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerStateExecutionIdTest {

    private static final Clock CLOCK = Clock.systemUTC();

    private static TriggerState newState() {
        return TriggerState.of(Fixtures.triggerId(), TriggerType.SCHEDULE, null, false, 0);
    }

    @Test
    void shouldDefaultExecutionIdToNull() {
        assertThat(newState().getExecutionId()).isNull();
    }

    @Test
    void shouldSetExecutionIdWhenAssigned() {
        TriggerState state = newState().executionId(CLOCK, "exec-123");
        assertThat(state.getExecutionId()).isEqualTo("exec-123");
    }

    @Test
    void shouldPreserveExecutionIdAcrossUpdate() {
        TriggerState state = newState()
            .executionId(CLOCK, "exec-123")
            .locked(CLOCK, true);
        assertThat(state.getExecutionId()).isEqualTo("exec-123");
        assertThat(state.isLocked()).isTrue();
    }

    @Test
    void shouldClearExecutionIdWhenReset() {
        TriggerState state = newState()
            .executionId(CLOCK, "exec-123")
            .locked(CLOCK, true)
            .reset(CLOCK);
        assertThat(state.getExecutionId()).isNull();
        assertThat(state.isLocked()).isFalse();
    }

    @Test
    void shouldClearExecutionIdWhenExecutionTerminated() {
        TriggerState state = newState()
            .executionId(CLOCK, "exec-123")
            .updateOnExecutionTerminated(CLOCK, State.Type.SUCCESS);
        assertThat(state.getExecutionId()).isNull();
    }
}
