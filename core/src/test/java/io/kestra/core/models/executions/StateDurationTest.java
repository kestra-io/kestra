package io.kestra.core.models.executions;

import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class StateDurationTest {


    private static final Instant NOW = Instant.now();
    private static final Instant ONE = NOW.minus(Duration.ofDays(1000));
    private static final Instant TWO = ONE.plus(Duration.ofHours(11));
    private static final Instant THREE = TWO.plus(Duration.ofHours(222));

    @Test
    void justCreated() {
        var state = State.of(
            State.Type.CREATED,
            List.of(
                new State.History(State.Type.CREATED, ONE)
            )
        );
        assertThat(state.getDuration()).isEmpty();
    }

    @Test
    void success() {
        var state = State.of(
            State.Type.SUCCESS,
            List.of(
                new State.History(State.Type.CREATED, ONE),
                new State.History(State.Type.RUNNING, TWO),
                new State.History(State.Type.SUCCESS, THREE)
            )
        );
        assertThat(state.getDuration()).isEqualTo(Optional.of(Duration.between(ONE, THREE)));
    }

    @Test
    void isRunning() {
        var state = State.of(
            State.Type.RUNNING,
            List.of(
                new State.History(State.Type.CREATED, ONE),
                new State.History(State.Type.RUNNING, TWO)
            )
        );
        assertThat(state.getDuration()).isEmpty();
    }
}