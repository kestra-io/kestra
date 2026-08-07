package io.kestra.scheduler;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.RecoverMissedSchedules;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.plugin.core.trigger.Schedule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the next execution date computed when a Schedule definition changes.
 *
 * <p>Editing a cron makes {@code FlowListeners} run two steps back to back: the per-flow listener
 * writes a new trigger state through {@link Trigger#of(io.kestra.core.models.flows.FlowInterface,
 * io.kestra.core.models.triggers.AbstractTrigger, io.kestra.core.models.conditions.ConditionContext,
 * Optional)}, then {@code AbstractScheduler.initializedTriggers} may correct it through
 * {@link AbstractScheduler#computeScheduleInitialization}. Neither step may leave a next execution
 * date in the past, otherwise the scheduler loop fires the freshly edited schedule immediately as a
 * missed one.
 */
class SchedulerTriggerDefinitionChangeTest {

    private static final DateTimeFormatter CRON_MINUTE_HOUR = DateTimeFormatter.ofPattern("m H");

    private static Flow flow(Schedule schedule) {
        return Flow.builder()
            .id("flow")
            .namespace("io.kestra.unittest")
            .revision(1)
            .triggers(List.of(schedule))
            .build();
    }

    private static Schedule schedule(ZonedDateTime dailyAt, RecoverMissedSchedules recoverMissedSchedules) {
        return Schedule.builder()
            .id("schedule")
            .type(Schedule.class.getName())
            .cron(CRON_MINUTE_HOUR.format(dailyAt) + " * * *")
            .recoverMissedSchedules(recoverMissedSchedules)
            .build();
    }

    @ParameterizedTest
    @EnumSource(RecoverMissedSchedules.class)
    void shouldNotScheduleInThePastWhenCronIsChangedToAnAlreadyPassedTime(RecoverMissedSchedules recoverMissedSchedules) throws Exception {
        // Given
        ZonedDateTime now = ZonedDateTime.now();
        // the stored trigger was last evaluated under the previous cron, ten minutes ago
        Trigger stored = Trigger.builder()
            .namespace("io.kestra.unittest")
            .flowId("flow")
            .triggerId("schedule")
            .date(now.minusMinutes(10).truncatedTo(ChronoUnit.SECONDS))
            .nextExecutionDate(now.plusDays(1))
            .build();
        // the new cron points at a time that already passed today, but after the stored evaluation date
        Schedule updated = schedule(now.minusMinutes(5), recoverMissedSchedules);

        // When
        Trigger afterFlowListener = Trigger.of(flow(updated), updated, null, Optional.of(stored));
        Trigger afterInitialization = AbstractScheduler
            .computeScheduleInitialization(updated, afterFlowListener, recoverMissedSchedules, null)
            .orElse(afterFlowListener);

        // Then
        assertThat(afterFlowListener.getNextExecutionDate()).isAfter(now);
        assertThat(afterInitialization.getNextExecutionDate()).isAfter(now);
    }

    @Test
    void shouldKeepTheNextOccurrenceWhenCronIsChangedToAnUpcomingTime() throws Exception {
        // Given
        ZonedDateTime now = ZonedDateTime.now();
        Trigger stored = Trigger.builder()
            .namespace("io.kestra.unittest")
            .flowId("flow")
            .triggerId("schedule")
            .date(now.minusMinutes(10).truncatedTo(ChronoUnit.SECONDS))
            .nextExecutionDate(now.plusDays(1))
            .build();
        ZonedDateTime upcoming = now.plusMinutes(5).truncatedTo(ChronoUnit.MINUTES);
        Schedule updated = schedule(upcoming, RecoverMissedSchedules.ALL);

        // When
        Trigger afterFlowListener = Trigger.of(flow(updated), updated, null, Optional.of(stored));

        // Then
        assertThat(afterFlowListener.getNextExecutionDate()).isEqualTo(upcoming.truncatedTo(ChronoUnit.SECONDS));
    }
}
