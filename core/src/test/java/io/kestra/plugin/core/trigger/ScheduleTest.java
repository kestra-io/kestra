package io.kestra.plugin.core.trigger;

import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.plugin.core.condition.DateTimeBetween;
import io.kestra.plugin.core.condition.DayWeekInMonth;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.core.debug.Return;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class ScheduleTest {

    private static final String TEST_CRON_EVERYDAY_AT_8 = "0 8 * * *";

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    RunContextInitializer runContextInitializer;

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
                .format(Property.of("test"))
                .build()))
            .build();

        return TriggerContext.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getNamespace())
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
            .minusMonths(1);

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
    void successWithInput() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").cron("0 0 1 * *").inputs(Map.of("input1", "input1")).build();

        ZonedDateTime date = ZonedDateTime.now()
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS)
            .minusMonths(1);

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

    @Test
    void success_withLabels() throws Exception {
        var scheduleTrigger = Schedule.builder()
            .id("schedule")
            .cron("0 0 1 * *")
            .labels(List.of(
                new Label("trigger-label-1", "trigger-label-1"),
                new Label("trigger-label-2", "{{ 'trigger-label-2' }}"),
                new Label("trigger-label-3", "{{ null }}")
            ))
            .build();
        var conditionContext = conditionContext(scheduleTrigger);
        var date = ZonedDateTime.now()
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS)
            .minusMonths(1);
        var triggerContext = triggerContext(date, scheduleTrigger);

        Optional<Execution> evaluate = scheduleTrigger.evaluate(conditionContext, triggerContext);

        assertThat(evaluate.isPresent(), is(true));
        assertThat(evaluate.get().getLabels(), hasItem(new Label("trigger-label-1", "trigger-label-1")));
        assertThat(evaluate.get().getLabels(), hasItem(new Label("trigger-label-2", "trigger-label-2")));
        assertThat(evaluate.get().getLabels(), hasItem(new Label("trigger-label-3", "")));
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

    @SuppressWarnings("unchecked")
    @Test
    void everySecond() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").cron("* * * * * *").withSeconds(true).build();

        ZonedDateTime date = ZonedDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS)
            .minus(Duration.ofSeconds(1));

        Optional<Execution> evaluate = trigger.evaluate(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent(), is(true));

        var vars = (Map<String, String>) evaluate.get().getVariables().get("schedule");


        assertThat(dateFromVars(vars.get("date"), date), is(date));
        assertThat(dateFromVars(vars.get("next"), date), is(date.plus(Duration.ofSeconds(1))));
        assertThat(dateFromVars(vars.get("previous"), date), is(date.minus(Duration.ofSeconds(1))));
    }

    @Test
    void shouldNotReturnExecutionForBackFillWhenCurrentDateIsBeforeScheduleDate() throws Exception {
        // Given
        Schedule trigger = Schedule.builder().id("schedule").cron(TEST_CRON_EVERYDAY_AT_8).build();
        ZonedDateTime now = ZonedDateTime.now();
        TriggerContext triggerContext = triggerContext(now, trigger).toBuilder()
            .backfill(Backfill
                .builder()
                .currentDate(ZonedDateTime.now().with(LocalTime.MIN))
                .end(ZonedDateTime.now().with(LocalTime.MAX))
                .build()
            ).build();
        // When
        Optional<Execution> result = trigger.evaluate(conditionContext(trigger), triggerContext);

        // Then
        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void
    shouldReturnExecutionForBackFillWhenCurrentDateIsAfterScheduleDate() throws Exception {
        // Given
        Schedule trigger = Schedule.builder().id("schedule").cron(TEST_CRON_EVERYDAY_AT_8).build();
        ZonedDateTime now = ZonedDateTime.now();
        TriggerContext triggerContext = triggerContext(now, trigger).toBuilder()
            .backfill(Backfill
                .builder()
                .currentDate(ZonedDateTime.now().with(LocalTime.MIN).plus(Duration.ofHours(8)))
                .end(ZonedDateTime.now().with(LocalTime.MAX))
                .build()
            )
            .build();
        // When
        Optional<Execution> result = trigger.evaluate(conditionContext(trigger), triggerContext);

        // Then
        assertThat(result.isPresent(), is(true));
    }

    @Test
    void noBackfillNextDate() {
        Schedule trigger = Schedule.builder().id("schedule").cron("0 0 * * *").build();
        ZonedDateTime next = trigger.nextEvaluationDate(conditionContext(trigger), Optional.empty());

        assertThat(next.getDayOfMonth(), is(ZonedDateTime.now().plusDays(1).getDayOfMonth()));
    }

    @Test
    void noBackfillNextDateContext() {
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
            .minusMonths(1);

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
            .conditions(List.of(
                DayWeekInMonth.builder()
                    .dayOfWeek(DayOfWeek.MONDAY)
                    .dayInMonth(DayWeekInMonth.DayInMonth.FIRST)
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
            .conditions(List.of(
                DateTimeBetween.builder()
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

    @Test
    void lateMaximumDelay() {
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
                .namespace("io.kestra.tests")
                .flowId(IdUtils.create())
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
            .minusMonths(1);

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
                StringInput.builder().id("input1").type(Type.STRING).required(false).build(),
                StringInput.builder().id("input2").type(Type.STRING).defaults("default").build()
            ))
            .build();

        TriggerContext triggerContext = TriggerContext.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .triggerId(trigger.getId())
            .build();

        return ConditionContext.builder()
            .runContext(runContextInitializer.forScheduler((DefaultRunContext) runContextFactory.of(), triggerContext, trigger))
            .flow(flow)
            .build();
    }

    private ZonedDateTime dateFromVars(String date, ZonedDateTime expexted) {
        return ZonedDateTime.parse(date).withZoneSameInstant(expexted.getZone());
    }
}
