package io.kestra.core.models.flows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.tasks.Task;
import io.kestra.plugin.core.debug.Return;

import static org.assertj.core.api.Assertions.assertThat;

class StateTest {

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-01-01T01:01:01Z");

    private State terminatedState() {
        return new State(
            State.Type.SUCCESS, List.of(
                new State.History(State.Type.CREATED, START),
                new State.History(State.Type.SUCCESS, END)
            )
        );
    }

    @Test
    void shouldReturnEmptyEndDateWhenHistoryIsEmpty() {
        // Given: a terminated state with no history entries
        State state = new State(State.Type.SUCCESS, List.of());

        // When / Then: end date is absent rather than throwing
        assertThat(state.getEndDate()).isEmpty();
    }

    @Test
    void shouldGetStartDateFromFirstHistoryEntry() {
        // Given / When / Then
        assertThat(terminatedState().getStartDate()).isEqualTo(START);
    }

    @Test
    void shouldGetEndDateFromLastHistoryEntryWhenTerminated() {
        // Given / When / Then
        assertThat(terminatedState().getEndDate()).contains(END);
    }

    @Test
    void shouldGetEmptyEndDateWhenNotTerminatedNorPaused() {
        // Given
        State state = new State(State.Type.RUNNING, List.of(new State.History(State.Type.RUNNING, START)));

        // When / Then
        assertThat(state.getEndDate()).isEmpty();
    }

    @Test
    void shouldGetEndDateFromLastHistoryEntryWhenPaused() {
        // Given
        State state = new State(
            State.Type.PAUSED, List.of(
                new State.History(State.Type.CREATED, START),
                new State.History(State.Type.PAUSED, END)
            )
        );

        // When / Then
        assertThat(state.getEndDate()).contains(END);
    }

    @Test
    void shouldGetDurationBetweenStartAndEndWhenTerminated() {
        // Given / When / Then
        assertThat(terminatedState().getDuration()).contains(Duration.between(START, END));
    }

    @Test
    void shouldGetEmptyDurationWhenNotTerminated() {
        // Given
        State state = new State(State.Type.RUNNING, List.of(new State.History(State.Type.RUNNING, START)));

        // When / Then
        assertThat(state.getDuration()).isEmpty();
    }

    @Test
    void shouldGetPersistedDurationWhenTerminated() {
        // Given / When / Then
        assertThat(terminatedState().getDurationOrComputeIt()).isEqualTo(Duration.between(START, END));
    }

    @Test
    void shouldComputeDurationOnTheFlyWhenNotTerminated() {
        // Given
        Instant start = Instant.now().minusSeconds(5);
        State state = new State(State.Type.RUNNING, List.of(new State.History(State.Type.RUNNING, start)));

        // When / Then
        assertThat(state.getDurationOrComputeIt()).isCloseTo(Duration.ofSeconds(5), Duration.ofSeconds(3));
    }

    @Test
    void shouldGetLastRunningDurationFromTheLastRunningTransition() {
        // a retry appends attempts; the window must be the last RUNNING → end, not the first
        Instant firstRunning = Instant.parse("2024-01-01T00:00:00Z");
        Instant lastRunning = Instant.parse("2024-01-01T00:10:00Z");
        Instant end = Instant.parse("2024-01-01T00:10:03Z");
        State state = new State(
            State.Type.SUCCESS, List.of(
                new State.History(State.Type.CREATED, firstRunning.minusSeconds(1)),
                new State.History(State.Type.RUNNING, firstRunning),
                new State.History(State.Type.FAILED, firstRunning.plusSeconds(4)),
                new State.History(State.Type.RETRYING, lastRunning.minusSeconds(1)),
                new State.History(State.Type.RUNNING, lastRunning),
                new State.History(State.Type.SUCCESS, end)
            )
        );

        assertThat(state.lastRunningDuration()).contains(Duration.between(lastRunning, end));
    }

    @Test
    void shouldGetEmptyLastRunningDurationWhenNeverRunning() {
        State state = new State(
            State.Type.FAILED, List.of(
                new State.History(State.Type.CREATED, START),
                new State.History(State.Type.FAILED, END)
            )
        );

        assertThat(state.lastRunningDuration()).isEmpty();
    }

    @Test
    void shouldGetEmptyLastRunningDurationWhenNotEnded() {
        State state = new State(State.Type.RUNNING, List.of(new State.History(State.Type.RUNNING, START)));

        assertThat(state.lastRunningDuration()).isEmpty();
    }

    @Test
    void shouldFormatHumanDurationAsHoursMinutesSeconds() {
        // Given / When / Then
        assertThat(terminatedState().humanDuration()).isEqualTo("01:01:01.000");
    }

    @Test
    void shouldGetMaxDateFromLastHistoryEntry() {
        // Given / When / Then
        assertThat(terminatedState().maxDate()).isEqualTo(END);
    }

    @Test
    void shouldGetMaxDateAsNowWhenHistoryIsEmpty() {
        // Given
        Instant before = Instant.now();

        // When
        Instant maxDate = new State(State.Type.RUNNING, List.of()).maxDate();

        // Then
        assertThat(maxDate).isBetween(before, Instant.now());
    }

    @Test
    void shouldGetMinDateFromFirstHistoryEntry() {
        // Given / When / Then
        assertThat(terminatedState().minDate()).isEqualTo(START);
    }

    @Test
    void shouldGetMinDateAsNowWhenHistoryIsEmpty() {
        // Given
        Instant before = Instant.now();

        // When
        Instant minDate = new State(State.Type.RUNNING, List.of()).minDate();

        // Then
        assertThat(minDate).isBetween(before, Instant.now());
    }

    @Test
    void shouldFailWithSuccessWhenAllowFailureAndAllowWarning() {
        // Given
        Task task = Return.builder().allowFailure(true).allowWarning(true).build();

        // When / Then
        assertThat(State.Type.fail(task)).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    void shouldFailWithWarningWhenOnlyAllowFailure() {
        // Given
        Task task = Return.builder().allowFailure(true).allowWarning(false).build();

        // When / Then
        assertThat(State.Type.fail(task)).isEqualTo(State.Type.WARNING);
    }

    @Test
    void shouldFailWithFailedWhenNotAllowFailure() {
        // Given
        Task task = Return.builder().allowFailure(false).build();

        // When / Then
        assertThat(State.Type.fail(task)).isEqualTo(State.Type.FAILED);
    }

    @Test
    void shouldFailWithFailedWhenNotAllowFailureEvenIfAllowWarning() {
        // Given
        Task task = Return.builder().allowFailure(false).allowWarning(true).build();

        // When / Then
        assertThat(State.Type.fail(task)).isEqualTo(State.Type.FAILED);
    }
}
