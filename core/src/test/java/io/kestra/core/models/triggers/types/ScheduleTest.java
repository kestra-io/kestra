package io.kestra.core.models.triggers.types;

import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.conditions.types.DateTimeBetweenCondition;
import io.kestra.core.models.conditions.types.DayWeekCondition;
import io.kestra.core.models.conditions.types.DayWeekInMonthCondition;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.utils.IdUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class ScheduleTest {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void failed() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").cron("1 1 1 1 1").build();

        Optional<Execution> evaluate = trigger.evaluate(
            conditionContext(trigger),
            TriggerContext.builder()
                .date(ZonedDateTime.now().withSecond(2))
                .build()
        );

        assertThat(evaluate.isPresent(), is(false));
    }

    private static TriggerContext triggerContext(ZonedDateTime date, Schedule schedule) {
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .revision(1)
            .tasks(Collections.singletonList(Return.builder()
                .id("test")
                .type(Return.class.getName())
                .format("test")
                .build()))
            .build();

        return TriggerContext.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getNamespace())
            .flowRevision(flow.getRevision())
            .triggerId(schedule.getId())
            .date(date)
            .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void success() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").cron("0 0 1 * *").build();

        ZonedDateTime date = ZonedDateTime.now()
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS)
            .minus(1, ChronoUnit.MONTHS);

        Optional<Execution> evaluate = trigger.evaluate(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent(), is(true));

        var vars = (Map<String, String>) evaluate.get().getVariables().get("schedule");
        var inputs = evaluate.get().getInputs();

        assertThat(dateFromVars(vars.get("date"), date), is(date));
        assertThat(dateFromVars(vars.get("next"), date), is(date.plusMonths(1)));
        assertThat(dateFromVars(vars.get("previous"), date), is(date.minusMonths(1)));
        assertThat(evaluate.get().getLabels(), hasItem(new Label("flow-label-1", "flow-label-1")));
        assertThat(evaluate.get().getLabels(), hasItem(new Label("flow-label-2", "flow-label-2")));
        assertThat(inputs.size(), is(2));
        assertThat(inputs.get("input1"), nullValue());
        assertThat(inputs.get("input2"), is("default"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void successWithInput() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").cron("0 0 1 * *").inputs(Map.of("input1", "input1")).build();

        ZonedDateTime date = ZonedDateTime.now()
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS)
            .minus(1, ChronoUnit.MONTHS);

        Optional<Execution> evaluate = trigger.evaluate(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent(), is(true));

        var inputs = evaluate.get().getInputs();

        assertThat(inputs.size(), is(2));
        assertThat(inputs.get("input1"), is("input1"));
        assertThat(inputs.get("input2"), is("default"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void everyMinute() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").cron("* * * * *").build();

        ZonedDateTime date = ZonedDateTime.now()
            .minus(Duration.ofMinutes(1))
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS)
            .plus(Duration.ofMinutes(1));

        Optional<Execution> evaluate = trigger.evaluate(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent(), is(true));

        var vars = (Map<String, String>) evaluate.get().getVariables().get("schedule");


        assertThat(dateFromVars(vars.get("date"), date), is(date));
        assertThat(dateFromVars(vars.get("next"), date), is(date.plus(Duration.ofMinutes(1))));
        assertThat(dateFromVars(vars.get("previous"), date), is(date.minus(Duration.ofMinutes(1))));
    }

    @Test
    void noBackfillNextDate() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").cron("0 0 * * *").build();
        ZonedDateTime next = trigger.nextEvaluationDate(conditionContext(trigger), Optional.empty());

        assertThat(next.getDayOfMonth(), is(ZonedDateTime.now().plusDays(1).getDayOfMonth()));
    }

    @Test
    void noBackfillNextDateContext() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").cron("0 0 * * *").timezone("Europe/Paris").build();
        ZonedDateTime date = ZonedDateTime.parse("2020-01-01T00:00:00+01:00[Europe/Paris]");
        ZonedDateTime next = trigger.nextEvaluationDate(conditionContext(trigger), Optional.of(triggerContext(date, trigger)));

        assertThat(next.format(DateTimeFormatter.ISO_LOCAL_DATE), is(date.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void systemBackfillChangedFromCronExpression() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").cron("30 0 1 * *").build();

        ZonedDateTime date = ZonedDateTime.now()
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(45)
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS)
            .minus(1, ChronoUnit.MONTHS);

        ZonedDateTime expexted = date.withMinute(30)
            .plusMonths(1);

        Optional<Execution> evaluate = trigger.evaluate(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent(), is(true));

        var vars = (Map<String, String>) evaluate.get().getVariables().get("schedule");
        assertThat(dateFromVars(vars.get("date"), expexted), is(expexted));
        assertThat(dateFromVars(vars.get("next"), expexted), is(expexted.plusMonths(1)));
        assertThat(dateFromVars(vars.get("previous"), expexted), is(expexted.minusMonths(1)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void conditions() throws Exception {
        Schedule trigger = Schedule.builder()
            .id("schedule")
            .cron("0 12 * * 1")
            .timezone("Europe/Paris")
            .scheduleConditions(List.of(
                DayWeekInMonthCondition.builder()
                    .dayOfWeek(DayOfWeek.MONDAY)
                    .dayInMonth(DayWeekInMonthCondition.DayInMonth.FIRST)
                    .date("{{ trigger.date }}")
                    .build()
            ))
            .build();

        ZonedDateTime date = ZonedDateTime.parse("2021-08-02T12:00:00+02:00");
        ZonedDateTime previous = ZonedDateTime.parse("2021-07-05T12:00:00+02:00");
        ZonedDateTime next = ZonedDateTime.parse("2021-09-06T12:00:00+02:00");

        Optional<Execution> evaluate = trigger.evaluate(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent(), is(true));

        var vars = (Map<String, String>) evaluate.get().getVariables().get("schedule");
        assertThat(dateFromVars(vars.get("date"), date), is(date));
        assertThat(dateFromVars(vars.get("next"), next), is(next));
        assertThat(dateFromVars(vars.get("previous"), previous), is(previous));
    }

    @SuppressWarnings("unchecked")
    @Test
    void impossibleNextConditions() throws Exception {
        Schedule trigger = Schedule.builder()
            .id("schedule")
            .cron("0 12 * * 1")
            .timezone("Europe/Paris")
            .scheduleConditions(List.of(
                DateTimeBetweenCondition.builder()
                    .before(ZonedDateTime.parse("2021-08-03T12:00:00+02:00"))
                    .date("{{ trigger.date }}")
                    .build()
            ))
            .build();

        ZonedDateTime date = ZonedDateTime.parse("2021-08-02T12:00:00+02:00");
        ZonedDateTime previous = ZonedDateTime.parse("2021-07-26T12:00:00+02:00");

        Optional<Execution> evaluate = trigger.evaluate(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent(), is(true));

        var vars = (Map<String, String>) evaluate.get().getVariables().get("schedule");
        assertThat(dateFromVars(vars.get("date"), date), is(date));
        assertThat(dateFromVars(vars.get("previous"), previous), is(previous));
        assertThat(vars.containsKey("next"), is(false));
    }

    @SuppressWarnings("unchecked")
    @Test
    void conditionsWithBackfill() throws Exception {
        Schedule trigger = Schedule.builder()
            .id("schedule")
            .cron("0 12 * * 1")
            .timezone("Europe/Paris")
//            .backfill(Schedule.ScheduleBackfill.builder()
//                .start(ZonedDateTime.parse("2021-01-01T00:00:00+02:00"))
//                .build()
//            )
            .scheduleConditions(List.of(
                DayWeekInMonthCondition.builder()
                    .dayOfWeek(DayOfWeek.MONDAY)
                    .dayInMonth(DayWeekInMonthCondition.DayInMonth.FIRST)
                    .date("{{ trigger.date }}")
                    .build(),
                DateTimeBetweenCondition.builder()
                    .before(ZonedDateTime.parse("2021-05-01T12:00:00+02:00"))
                    .date("{{ trigger.date }}")
                    .build()
            ))
            .build();

        ZonedDateTime date = ZonedDateTime.parse("2021-01-04T12:00:00+01:00");

        for (int i = 0; i < 4; i++) {
            Optional<Execution> evaluate = trigger.evaluate(
                conditionContext(trigger),
                triggerContext(date, trigger)
            );

            assertThat(evaluate.isPresent(), is(true));

            var vars = (Map<String, String>) evaluate.get().getVariables().get("schedule");
            assertThat(dateFromVars(vars.get("date"), date), is(date));
            if (i == 3) {
                assertThat(vars.containsKey("next"), is(false));
            } else {
                date = dateFromVars(vars.get("next"), date);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void lateMaximumDelay() throws Exception {
        Schedule trigger = Schedule.builder()
            .id("schedule")
            .cron("* * * * *")
            .lateMaximumDelay(Duration.ofMinutes(5))
            .build();

        ZonedDateTime date = ZonedDateTime.now().minusMinutes(15);
        ZonedDateTime expected = ZonedDateTime.now().minusMinutes(4)
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS);

        ZonedDateTime evaluate = trigger.nextEvaluationDate(
            conditionContext(trigger),
            Optional.of(TriggerContext.builder()
                .date(date)
                .build())
        );

        assertThat(evaluate, is(expected));

    }

    @SuppressWarnings("unchecked")
    @Test
    void hourly() throws Exception {
        Schedule trigger = Schedule.builder()
            .id("schedule")
            .cron("@hourly")
            .build();

        ZonedDateTime date = ZonedDateTime.now().minusHours(1).withMinute(0).withSecond(0).withNano(0);


        Optional<Execution> evaluate = trigger.evaluate(
            conditionContext(trigger),
            TriggerContext.builder()
                .date(date)
                .build()
        );

        assertThat(evaluate.isPresent(), is(true));
        var vars = (Map<String, String>) evaluate.get().getVariables().get("schedule");
        assertThat(dateFromVars(vars.get("date"), date), is(date));
    }

    @SuppressWarnings("unchecked")
    @Test
    void timezone() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").cron("12 9 1 * *").timezone("America/New_York").build();

        ZonedDateTime date = ZonedDateTime.now()
            .withZoneSameLocal(ZoneId.of("America/New_York"))
            .withMonth(5)
            .withDayOfMonth(1)
            .withHour(9)
            .withMinute(12)
            .withSecond(0)
            .withYear(2022)
            .truncatedTo(ChronoUnit.SECONDS)
            .minus(1, ChronoUnit.MONTHS);

        Optional<Execution> evaluate = trigger.evaluate(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent(), is(true));

        var vars = (Map<String, String>) evaluate.get().getVariables().get("schedule");

        assertThat(dateFromVars(vars.get("date"), date), is(date));
        assertThat(ZonedDateTime.parse(vars.get("date")).getZone().getId(), is("-04:00"));
        assertThat(dateFromVars(vars.get("next"), date), is(date.plusMonths(1)));
        assertThat(dateFromVars(vars.get("previous"), date), is(date.minusMonths(1)));
    }



    private ConditionContext conditionContext(AbstractTrigger trigger) {
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.tests")
            .labels(
                List.of(
                    new Label("flow-label-1", "flow-label-1"),
                    new Label("flow-label-2", "flow-label-2")
                )
            )
            .inputs(List.of(
                StringInput.builder().id("input1").type(Input.Type.STRING).required(false).build(),
                StringInput.builder().id("input2").type(Input.Type.STRING).defaults("default").build()
            ))
            .build();

        TriggerContext triggerContext = TriggerContext.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .triggerId(trigger.getId())
            .build();

        return ConditionContext.builder()
            .runContext(runContextFactory.of().forScheduler(triggerContext, trigger))
            .flow(flow)
            .build();
    }

    private ZonedDateTime dateFromVars(String date, ZonedDateTime expexted) {
        return ZonedDateTime.parse(date).withZoneSameInstant(expexted.getZone());
    }
}
