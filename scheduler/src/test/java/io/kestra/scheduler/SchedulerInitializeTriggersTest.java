package io.kestra.scheduler;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.triggers.RecoverMissedSchedules;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.plugin.core.trigger.Schedule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Schedule (re)initialization decision made by the flow-change listener
 * (AbstractScheduler.initializedTriggers). The listener re-runs over every flow whenever any flow
 * in the instance changes, so it must never resurrect a stale execution lock on a trigger whose
 * execution already terminated.
 */
class SchedulerInitializeTriggersTest {

    private static Schedule schedule() {
        return Schedule.builder()
            .id("schedule")
            .type(Schedule.class.getName())
            .cron("*/16 * * * *")
            .recoverMissedSchedules(RecoverMissedSchedules.NONE)
            .build();
    }

    private static Trigger.TriggerBuilder<?, ?> trigger() {
        return Trigger.builder()
            .namespace("io.kestra.unittest")
            .flowId("flow")
            .triggerId("schedule")
            .date(ZonedDateTime.now().minusHours(1));
    }

    @Test
    void shouldNotRewriteLockedTriggerOnInitialize() throws Exception {
        Schedule schedule = schedule();
        Trigger locked = trigger()
            .executionId("running-execution-id")
            // stale next date in the past so the NONE branch would otherwise recompute and rewrite
            .nextExecutionDate(ZonedDateTime.now().minusMinutes(30))
            .build();

        Optional<Trigger> result = AbstractScheduler.computeScheduleInitialization(
            schedule, locked, RecoverMissedSchedules.NONE, null
        );

        assertThat(result).isEmpty();
    }

    @Test
    void shouldRecomputeNextDateForUnlockedTrigger() throws Exception {
        Schedule schedule = schedule();
        Trigger unlocked = trigger()
            .executionId(null)
            .nextExecutionDate(ZonedDateTime.now().minusMinutes(30))
            .build();

        Optional<Trigger> result = AbstractScheduler.computeScheduleInitialization(
            schedule, unlocked, RecoverMissedSchedules.NONE, null
        );

        assertThat(result).isPresent();
        assertThat(result.get().getExecutionId()).isNull();
        assertThat(result.get().getNextExecutionDate()).isEqualTo(schedule.nextEvaluationDate());
    }
}
