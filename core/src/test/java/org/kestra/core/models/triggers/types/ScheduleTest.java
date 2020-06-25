package org.kestra.core.models.triggers.types;

import com.devskiller.friendly_id.FriendlyId;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.TriggerContext;
import org.kestra.core.tasks.debugs.Return;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ScheduleTest {
    @Test
    void failed() {
        Schedule trigger = Schedule.builder().cron("1 1 1 1 1").build();

        Optional<Execution> evaluate = trigger.evaluate(TriggerContext.builder()
            .date(ZonedDateTime.now().withSecond(2))
            .build()
        );

        assertThat(evaluate.isPresent(), is(false));
    }

    private static Flow create() {
        return Flow.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace("org.kestra.unittest")
            .revision(1)
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void success() {
        Schedule trigger = Schedule.builder().cron("0 0 1 * *").build();

        ZonedDateTime date = ZonedDateTime.now()
            .withMonth(ZonedDateTime.now().getMonthValue() + 1)
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS);

        Optional<Execution> evaluate = trigger.evaluate(TriggerContext.builder()
            .date(date.minus(Duration.ofSeconds(1)))
            .flow(create())
            .build()
        );

        assertThat(evaluate.isPresent(), is(true));

        var vars = (Map<String, ZonedDateTime>) evaluate.get().getVariables().get("schedule");
        assertThat(vars.get("date"), is(date));
        assertThat(vars.get("next"), is(date.plusMonths(1)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void everyMinute() {
        Schedule trigger = Schedule.builder().cron("* * * * *").build();

        ZonedDateTime date = ZonedDateTime.now()
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS)
            .plus(Duration.ofMinutes(1));

        Optional<Execution> evaluate = trigger.evaluate(TriggerContext.builder()
            .date(date.minus(Duration.ofSeconds(1)))
            .flow(create())
            .build()
        );

        assertThat(evaluate.isPresent(), is(true));

        var vars = (Map<String, ZonedDateTime>) evaluate.get().getVariables().get("schedule");
        assertThat(vars.get("date"), is(date));
        assertThat(vars.get("next"), is(date.plus(Duration.ofMinutes(1))));
    }
}
