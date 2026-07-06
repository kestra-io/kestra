package io.kestra.executor.statemachine.domain;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutableUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layer-0 truth table for {@link ExecutableUtils#guessState} — the single pure decision that maps
 * a child (subflow) execution state onto the parent Subflow taskrun state, over transmitFailed ×
 * allowFailure × allowWarning. Twins the final-state expectations otherwise only exercised
 * end-to-end through FlowCaseTest#waitSuccess/#waitFailed and
 * SubflowRunnerTest#subflowOutputWithWait (StandAloneRunner over real queues). Pure JUnit,
 * no harness.
 * <p>
 * The 2 states × 8 flag combinations that would explode into 32 rows collapse because:
 * transmitFailed=false short-circuits everything to SUCCESS; allowFailure only applies to a
 * FAILED child; allowWarning only applies once the (possibly downgraded) state is WARNING.
 */
class SubflowFinalStateTest {

    // --- transmitFailed=true: the full decision surface

    static Stream<Arguments> transmitFailedDecisions() {
        return Stream.of(
            // childState, allowFailure, allowWarning, expected parent taskrun state
            // SUCCESS child: never re-mapped, flags are dead code
            Arguments.of(State.Type.SUCCESS, false, false, State.Type.SUCCESS),
            Arguments.of(State.Type.SUCCESS, true, true, State.Type.SUCCESS),
            // FAILED child: allowFailure downgrades to WARNING, then allowWarning to SUCCESS —
            // allowWarning ALONE never rescues a FAILED child
            Arguments.of(State.Type.FAILED, false, false, State.Type.FAILED),
            Arguments.of(State.Type.FAILED, false, true, State.Type.FAILED),
            Arguments.of(State.Type.FAILED, true, false, State.Type.WARNING),
            Arguments.of(State.Type.FAILED, true, true, State.Type.SUCCESS),
            // KILLED child: transmitted verbatim — allowFailure only applies to isFailed()
            // (strictly FAILED) and allowWarning only applies to WARNING, so no flag touches it
            Arguments.of(State.Type.KILLED, false, false, State.Type.KILLED),
            Arguments.of(State.Type.KILLED, true, true, State.Type.KILLED),
            // WARNING child: allowWarning upgrades to SUCCESS, allowFailure is irrelevant
            Arguments.of(State.Type.WARNING, false, false, State.Type.WARNING),
            Arguments.of(State.Type.WARNING, true, false, State.Type.WARNING),
            Arguments.of(State.Type.WARNING, false, true, State.Type.SUCCESS),
            Arguments.of(State.Type.WARNING, true, true, State.Type.SUCCESS)
        );
    }

    @ParameterizedTest(name = "child {0}, allowFailure={1}, allowWarning={2} -> {3}")
    @MethodSource("transmitFailedDecisions")
    void shouldMapChildStateWhenTransmitFailed(State.Type childState, boolean allowFailure, boolean allowWarning, State.Type expected) {
        assertThat(ExecutableUtils.guessState(executionInState(childState), true, allowFailure, allowWarning))
            .isEqualTo(expected);
    }

    // --- transmitFailed=false: unconditional SUCCESS, whatever the child did

    static Stream<Arguments> childStatesIgnoredWithoutTransmitFailed() {
        return Stream.of(
            Arguments.of(State.Type.SUCCESS),
            Arguments.of(State.Type.WARNING),
            Arguments.of(State.Type.FAILED),
            Arguments.of(State.Type.KILLED),
            Arguments.of(State.Type.PAUSED),
            Arguments.of(State.Type.CANCELLED)
        );
    }

    @ParameterizedTest(name = "child {0} -> SUCCESS")
    @MethodSource("childStatesIgnoredWithoutTransmitFailed")
    void shouldAlwaysSucceedWhenTransmitFailedDisabled(State.Type childState) {
        // the allowFailure/allowWarning flags are dead code on this branch — prove it at both extremes
        assertThat(ExecutableUtils.guessState(executionInState(childState), false, false, false))
            .isEqualTo(State.Type.SUCCESS);
        assertThat(ExecutableUtils.guessState(executionInState(childState), false, true, true))
            .isEqualTo(State.Type.SUCCESS);
    }

    // --- documented oddities of the actual implementation (transmitFailed=true)

    @Test
    void shouldTreatCancelledChildAsSuccessWhenTransmitFailed() {
        // Surprising but actual: the guard only checks isFailed() (strictly FAILED), isPaused(),
        // KILLED and WARNING — a CANCELLED child (e.g. concurrency-limit CANCEL) slips through to
        // SUCCESS even with transmitFailed=true.
        assertThat(ExecutableUtils.guessState(executionInState(State.Type.CANCELLED), true, false, false))
            .isEqualTo(State.Type.SUCCESS);
    }

    @Test
    void shouldPropagatePausedChildStateWhenTransmitFailed() {
        // PAUSED is the only non-terminal child state the guard transmits verbatim
        assertThat(ExecutableUtils.guessState(executionInState(State.Type.PAUSED), true, false, false))
            .isEqualTo(State.Type.PAUSED);
        // allowFailure/allowWarning never touch it: PAUSED is neither FAILED nor WARNING
        assertThat(ExecutableUtils.guessState(executionInState(State.Type.PAUSED), true, true, true))
            .isEqualTo(State.Type.PAUSED);
    }

    @Test
    void shouldReturnSuccessWhenChildStillCreated() {
        // This is the mechanism behind wait=false: the executor guesses the parent taskrun state
        // from the freshly CREATED child, and a non-failed/non-paused child maps to SUCCESS
        assertThat(ExecutableUtils.guessState(executionInState(State.Type.CREATED), true, false, false))
            .isEqualTo(State.Type.SUCCESS);
    }

    // --- fixtures

    private static Execution executionInState(State.Type state) {
        return Execution.builder()
            .id("child-execution")
            .namespace("io.kestra.tests")
            .flowId("child-flow")
            .state(new State(state))
            .build();
    }
}
