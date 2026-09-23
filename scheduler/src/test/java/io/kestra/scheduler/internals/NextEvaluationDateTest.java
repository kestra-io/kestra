package io.kestra.scheduler.internals;

import java.time.Clock;
import java.time.ZonedDateTime;

import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.trigger.Schedule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NextEvaluationDateTest {

    @Test
    void shouldReturnNextCronTickWhenTriggerIsSchedulable() {
        Schedule schedule = Schedule.builder()
            .id(IdUtils.create())
            .type(Schedule.class.getName())
            .cron("0 4 * * *")
            .build();

        ZonedDateTime next = NextEvaluationDate.get(Clock.systemDefaultZone(), schedule);

        assertThat(next).isAfter(ZonedDateTime.now());
        assertThat(next.getHour()).isEqualTo(4);
        assertThat(next.getMinute()).isZero();
    }
}
