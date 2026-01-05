package io.kestra.plugin.core.trigger;

import io.kestra.core.exceptions.InternalException;
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
import io.kestra.core.models.flows.input.MultiselectInput;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.core.condition.TimeBetween;
import io.kestra.plugin.core.debug.Return;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
class ScheduleTest {

    private static final String TEST_CRON_EVERYDAY_AT_8 = "0 8 * * *";

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    RunContextInitializer runContextInitializer;

    @Test
    void failed() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("1 1 1 1 1").build();

        Optional<Execution> evaluate = trigger.evaluate(
            conditionContext(trigger),
            TriggerContext.builder()
                .date(ZonedDateTime.now().withSecond(2))
                .build()
        );

        assertThat(evaluate.isPresent()).isFalse();
    }

    private static TriggerContext triggerContext(ZonedDateTime date, Schedule schedule) {
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .revision(1)
            .variables(Map.of("custom_var", "VARIABLE VALUE"))
            .tasks(Collections.singletonList(Return.builder()
                .id("test")
                .type(Return.class.getName())
                .format(Property.ofValue("test"))
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
    void success() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("0 0 1 * *").build();

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

        assertThat(evaluate.isPresent()).isTrue();
        assertThat(evaluate.get().getLabels()).hasSize(4);
        assertTrue(evaluate.get().getLabels().stream().anyMatch(label -> label.key().equals(Label.CORRELATION_ID)));
        assertTrue(evaluate.get().getLabels().stream().anyMatch(label -> label.equals(new Label(Label.FROM, "trigger"))));
        assertThat(evaluate.get().getVariables()).containsEntry("custom_var", "VARIABLE VALUE");
        var vars = evaluate.get().getTrigger().getVariables();
        var inputs = evaluate.get().getInputs();

        assertThat(dateFromVars((String) vars.get("date"), date)).isEqualTo(date);
        assertThat(dateFromVars((String) vars.get("next"), date)).isEqualTo(date.plusMonths(1));
        assertThat(dateFromVars((String) vars.get("previous"), date)).isEqualTo(date.minusMonths(1));
        assertThat(evaluate.get().getLabels()).contains(new Label("flow-label-1", "flow-label-1"));
        assertThat(evaluate.get().getLabels()).contains(new Label("flow-label-2", "flow-label-2"));
        assertThat(inputs.size()).isEqualTo(2);
        assertThat(inputs.get("input1")).isNull();
        assertThat(inputs.get("input2")).isEqualTo("default");
    }

    @Test
    void successWithInput() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("0 0 1 * *").inputs(Map.of("input1", "input1")).build();

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

        assertThat(evaluate.isPresent()).isTrue();
        assertThat(evaluate.get().getLabels()).hasSize(4);
        assertTrue(evaluate.get().getLabels().stream().anyMatch(label -> label.key().equals(Label.CORRELATION_ID)));
        assertTrue(evaluate.get().getLabels().stream().anyMatch(label -> label.equals(new Label(Label.FROM, "trigger"))));
        assertThat(evaluate.get().getVariables()).containsEntry("custom_var", "VARIABLE VALUE");
        var inputs = evaluate.get().getInputs();

        assertThat(inputs.size()).isEqualTo(2);
        assertThat(inputs.get("input1")).isEqualTo("input1");
        assertThat(inputs.get("input2")).isEqualTo("default");
    }

    @Test
    void success_withLabels() throws Exception {
        var scheduleTrigger = Schedule.builder()
            .id("schedule").type(Schedule.class.getName())
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

        assertThat(evaluate.isPresent()).isTrue();
        assertThat(evaluate.get().getVariables()).containsEntry("custom_var", "VARIABLE VALUE");
        assertThat(evaluate.get().getLabels()).contains(new Label("trigger-label-1", "trigger-label-1"));
        assertThat(evaluate.get().getLabels()).contains(new Label("trigger-label-2", "trigger-label-2"));
        assertThat(evaluate.get().getLabels()).doesNotContain(new Label("trigger-label-3", ""));
    }

    @Test
    void everyMinute() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("* * * * *").build();

        ZonedDateTime date = ZonedDateTime.now()
            .minus(Duration.ofMinutes(1))
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS)
            .plus(Duration.ofMinutes(1));

        Optional<Execution> evaluate = trigger.evaluate(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent()).isTrue();
        assertThat(evaluate.get().getVariables()).containsEntry("custom_var", "VARIABLE VALUE");
        var vars = evaluate.get().getTrigger().getVariables();

        assertThat(dateFromVars((String) vars.get("date"), date)).isEqualTo(date);
        assertThat(dateFromVars((String) vars.get("next"), date)).isEqualTo(date.plus(Duration.ofMinutes(1)));
        assertThat(dateFromVars((String) vars.get("previous"), date)).isEqualTo(date.minus(Duration.ofMinutes(1)));
    }

    @Test
    void everySecond() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("* * * * * *").withSeconds(true).build();

        ZonedDateTime date = ZonedDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS)
            .minus(Duration.ofSeconds(1));

        Optional<Execution> evaluate = trigger.evaluate(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent()).isTrue();
        assertThat(evaluate.get().getVariables()).containsEntry("custom_var", "VARIABLE VALUE");
        var vars = evaluate.get().getTrigger().getVariables();

        assertThat(dateFromVars((String) vars.get("date"), date)).isEqualTo(date);
        assertThat(dateFromVars((String) vars.get("next"), date)).isEqualTo(date.plus(Duration.ofSeconds(1)));
        assertThat(dateFromVars((String) vars.get("previous"), date)).isEqualTo(date.minus(Duration.ofSeconds(1)));
    }

    @Test
    void shouldNotReturnExecutionForBackFillWhenCurrentDateIsBeforeScheduleDate() throws Exception {
        // Given
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron(TEST_CRON_EVERYDAY_AT_8).build();
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
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void
    shouldReturnExecutionForBackFillWhenCurrentDateIsAfterScheduleDate() throws Exception {
        // Given
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron(TEST_CRON_EVERYDAY_AT_8).build();
        ZonedDateTime now = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault());
        TriggerContext triggerContext = triggerContext(ZonedDateTime.now(), trigger).toBuilder()
            .backfill(Backfill
                .builder()
                .currentDate(now.with(LocalTime.MIN).plus(Duration.ofHours(8)))
                .end(now.with(LocalTime.MAX))
                .build()
            )
            .build();
        // When
        Optional<Execution> result = trigger.evaluate(conditionContext(trigger), triggerContext);

        // Then
        assertThat(result.isPresent()).isTrue();
    }

    @Test
    void noBackfillNextDate() {
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("0 0 * * *").build();
        ZonedDateTime next = trigger.nextEvaluationDate(conditionContext(trigger), Optional.empty());

        assertThat(next.getDayOfMonth()).isEqualTo(ZonedDateTime.now().plusDays(1).getDayOfMonth());
    }

    @Test
    void noBackfillNextDateContext() {
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("0 0 * * *").timezone("Europe/Paris").build();
        ZonedDateTime date = ZonedDateTime.parse("2020-01-01T00:00:00+01:00[Europe/Paris]");
        ZonedDateTime next = trigger.nextEvaluationDate(conditionContext(trigger), Optional.of(triggerContext(date, trigger)));

        assertThat(next.format(DateTimeFormatter.ISO_LOCAL_DATE)).isEqualTo(date.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    @Test
    void systemBackfillChangedFromCronExpression() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("30 0 1 * *").build();

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

        assertThat(evaluate.isPresent()).isTrue();
        assertThat(evaluate.get().getVariables()).containsEntry("custom_var", "VARIABLE VALUE");
        var vars = evaluate.get().getTrigger().getVariables();
        assertThat(dateFromVars((String) vars.get("date"), expexted)).isEqualTo(expexted);
        assertThat(dateFromVars((String) vars.get("next"), expexted)).isEqualTo(expexted.plusMonths(1));
        assertThat(dateFromVars((String) vars.get("previous"), expexted)).isEqualTo(expexted.minusMonths(1));
    }

    @Test
    void conditions() throws Exception {
        Schedule trigger = Schedule.builder()
            .id("schedule").type(Schedule.class.getName())
            .type(Schedule.class.getName())
            .cron("0 12 * * 1")
            .timezone("Europe/Paris")
            .conditions(List.of(
                DayWeekInMonth.builder()
                    .type(DayWeekInMonth.class.getName())
                    .dayOfWeek(Property.ofValue(DayOfWeek.MONDAY))
                    .dayInMonth(Property.ofValue(DayWeekInMonth.DayInMonth.FIRST))
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

        assertThat(evaluate.isPresent()).isTrue();
        assertThat(evaluate.get().getVariables()).containsEntry("custom_var", "VARIABLE VALUE");
        var vars = evaluate.get().getTrigger().getVariables();
        assertThat(dateFromVars((String) vars.get("date"), date)).isEqualTo(date);
        assertThat(dateFromVars((String) vars.get("next"), next)).isEqualTo(next);
        assertThat(dateFromVars((String) vars.get("previous"), previous)).isEqualTo(previous);
    }

    @Test
    void impossibleNextConditions() throws Exception {
        Schedule trigger = Schedule.builder()
            .id("schedule").type(Schedule.class.getName())
            .type(Schedule.class.getName())
            .cron("0 12 * * 1")
            .timezone("Europe/Paris")
            .conditions(List.of(
                DateTimeBetween.builder()
                    .type(DateTimeBetween.class.getName())
                    .before(Property.ofValue(ZonedDateTime.parse("2021-08-03T12:00:00+02:00")))
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

        assertThat(evaluate.isPresent()).isTrue();
        assertThat(evaluate.get().getVariables()).containsEntry("custom_var", "VARIABLE VALUE");
        var vars = evaluate.get().getTrigger().getVariables();
        assertThat(dateFromVars((String) vars.get("date"), date)).isEqualTo(date);
        assertThat(dateFromVars((String) vars.get("previous"), previous)).isEqualTo(previous);
        assertThat(vars.containsKey("next")).isFalse();
    }

    @Test
    void lateMaximumDelay() {
        Schedule trigger = Schedule.builder()
            .id("schedule").type(Schedule.class.getName())
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

        assertThat(evaluate).isEqualTo(expected);

    }

    @Test
    void hourly() throws Exception {
        Schedule trigger = Schedule.builder()
            .id("schedule").type(Schedule.class.getName())
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

        assertThat(evaluate.isPresent()).isTrue();
        assertThat(evaluate.get().getVariables()).containsEntry("custom_var", "VARIABLE VALUE");
        var vars = evaluate.get().getTrigger().getVariables();
        assertThat(dateFromVars((String) vars.get("date"), date)).isEqualTo(date);
    }

    @Test
    void timezone() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("12 9 1 * *").timezone("America/New_York").build();

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

        assertThat(evaluate.isPresent()).isTrue();
        assertThat(evaluate.get().getVariables()).containsEntry("custom_var", "VARIABLE VALUE");
        var vars = evaluate.get().getTrigger().getVariables();

        assertThat(dateFromVars((String) vars.get("date"), date)).isEqualTo(date);
        assertThat(ZonedDateTime.parse((String) vars.get("date")).getZone().getId()).isEqualTo("-04:00");
        assertThat(dateFromVars((String) vars.get("next"), date)).isEqualTo(date.plusMonths(1));
        assertThat(dateFromVars((String) vars.get("previous"), date)).isEqualTo(date.minusMonths(1));
    }

    @Test
    void timezone_with_backfile() throws Exception {
        Schedule trigger = Schedule.builder()
            .id("schedule").type(Schedule.class.getName())
            .cron(TEST_CRON_EVERYDAY_AT_8)
            .timezone("America/New_York")
            .build();

        TriggerContext triggerContext = triggerContext(ZonedDateTime.now(), trigger).toBuilder()
            .backfill(Backfill
                .builder()
                .currentDate(ZonedDateTime.parse("2025-01-15T08:00-05:00[America/New_York]"))
                .end(ZonedDateTime.parse("2025-01-16T07:00-05:00[America/New_York]"))
                .build()
            )
            .build();
        // When
        Optional<Execution> result = trigger.evaluate(conditionContext(trigger), triggerContext);

        // Then
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().getVariables()).containsEntry("custom_var", "VARIABLE VALUE");
    }

    @Test
    void successWithMultiselectInputDefaults() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("0 0 1 * *").build();

        ZonedDateTime date = ZonedDateTime.now()
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS)
            .minusMonths(1);

        Optional<Execution> evaluate = trigger.evaluate(
            conditionContextWithMultiselectInput(trigger),
            triggerContext(date, trigger));

        assertThat(evaluate.isPresent()).isTrue();
        var inputs = evaluate.get().getInputs();

        // Verify MULTISELECT input with explicit defaults works correctly
        assertThat(inputs.get("multiselectInput")).isEqualTo(List.of("option1", "option2"));
    }

    @Test
    void successWithMultiselectInputAutoSelectFirst() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("0 0 1 * *").build();

        ZonedDateTime date = ZonedDateTime.now()
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS)
            .minusMonths(1);

        Optional<Execution> evaluate = trigger.evaluate(
            conditionContextWithMultiselectAutoSelectFirst(trigger),
            triggerContext(date, trigger));

        assertThat(evaluate.isPresent()).isTrue();
        var inputs = evaluate.get().getInputs();

        // Verify MULTISELECT input with autoSelectFirst defaults to first option
        assertThat(inputs.get("multiselectAutoSelect")).isEqualTo(List.of("first"));
    }

    @Test
    void successWithMultiselectInputProvidedValue() throws Exception {
        // Test that provided values override defaults for MULTISELECT
        Schedule trigger = Schedule.builder()
            .id("schedule")
            .type(Schedule.class.getName())
            .cron("0 0 1 * *")
            .inputs(Map.of("multiselectInput", List.of("option3")))
            .build();

        ZonedDateTime date = ZonedDateTime.now()
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS)
            .minusMonths(1);

        Optional<Execution> evaluate = trigger.evaluate(
            conditionContextWithMultiselectInput(trigger),
            triggerContext(date, trigger));

        assertThat(evaluate.isPresent()).isTrue();
        var inputs = evaluate.get().getInputs();

        // Verify provided value overrides defaults
        assertThat(inputs.get("multiselectInput")).isEqualTo(List.of("option3"));
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
            .variables(Map.of("custom_var", "VARIABLE VALUE"))
            .inputs(List.of(
                StringInput.builder().id("input1").type(Type.STRING).required(false).build(),
                StringInput.builder().id("input2").type(Type.STRING).defaults(Property.ofValue("default")).build()
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

    private ConditionContext conditionContextWithMultiselectInput(AbstractTrigger trigger) {
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.tests")
            .labels(
                    List.of(
                            new Label("flow-label-1", "flow-label-1"),
                            new Label("flow-label-2", "flow-label-2")))
            .variables(Map.of("custom_var", "VARIABLE VALUE"))
            .inputs(List.of(
                    MultiselectInput.builder()
                        .id("multiselectInput")
                        .type(Type.MULTISELECT)
                        .values(List.of("option1", "option2", "option3"))
                        .defaults(Property.ofValue(List.of("option1", "option2")))
                        .build()))
            .build();

        TriggerContext triggerContext = TriggerContext.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .triggerId(trigger.getId())
            .build();

        return ConditionContext.builder()
            .runContext(runContextInitializer.forScheduler((DefaultRunContext) runContextFactory.of(),
                    triggerContext, trigger))
            .flow(flow)
            .build();
    }

    private ConditionContext conditionContextWithMultiselectAutoSelectFirst(AbstractTrigger trigger) {
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.tests")
            .labels(
                    List.of(
                        new Label("flow-label-1", "flow-label-1"),
                        new Label("flow-label-2", "flow-label-2")))
            .variables(Map.of("custom_var", "VARIABLE VALUE"))
            .inputs(List.of(
                    MultiselectInput.builder()
                        .id("multiselectAutoSelect")
                        .type(Type.MULTISELECT)
                        .values(List.of("first", "second", "third"))
                        .autoSelectFirst(true)
                        .build()))
            .build();

        TriggerContext triggerContext = TriggerContext.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .triggerId(trigger.getId())
            .build();

        return ConditionContext.builder()
            .runContext(runContextInitializer.forScheduler((DefaultRunContext) runContextFactory.of(),
                    triggerContext, trigger))
            .flow(flow)
            .build();
    }

    private ZonedDateTime dateFromVars(String date, ZonedDateTime expexted) {
        return ZonedDateTime.parse(date).withZoneSameInstant(expexted.getZone());
    }

    @Test
    void shouldGetNextExecutionDateWithConditionMatchingFutureDate() throws InternalException {

        ZonedDateTime now = ZonedDateTime.now().withZoneSameLocal(ZoneId.of("Europe/Paris"));
        OffsetTime before = now.minusHours(1).toOffsetDateTime().toOffsetTime().withMinute(0).withSecond(0).withNano(0);
        OffsetTime after = now.minusHours(4).toOffsetDateTime().toOffsetTime().withMinute(0).withSecond(0).withNano(0);

        Schedule trigger = Schedule.builder()
            .id("schedule").type(Schedule.class.getName())
            .cron("0 * * * *") // every hour
            .withSeconds(false)
            .timezone("Europe/Paris")
            .conditions(List.of(TimeBetween.builder()
                .type(TimeBetween.class.getName())
                .before(Property.ofValue(before))
                .after(Property.ofValue(after))
                .build()
            ))
            .build();

        TriggerContext triggerContext = triggerContext(now, trigger).toBuilder().build();

        ConditionContext conditionContext = ConditionContext.builder()
            .runContext(runContextInitializer.forScheduler((DefaultRunContext) runContextFactory.of(), triggerContext, trigger))
            .build();

        Optional<ZonedDateTime> result = trigger.truePreviousNextDateWithCondition(trigger.executionTime(), conditionContext, now, true);
        assertThat(result).isNotEmpty();
    }

    @Test
    void shouldGetNextExecutionDateWithConditionMatchingCurrentDate() throws InternalException {

        ZonedDateTime now = ZonedDateTime.now().withZoneSameLocal(ZoneId.of("Europe/Paris"));

        OffsetTime before = now.plusHours(2).toOffsetDateTime().toOffsetTime().withMinute(0).withSecond(0).withNano(0);
        OffsetTime after = now.minusHours(2).toOffsetDateTime().toOffsetTime().withMinute(0).withSecond(0).withNano(0);

        Schedule trigger = Schedule.builder()
            .id("schedule").type(Schedule.class.getName())
            .cron("*/30 * * * * *")
            .withSeconds(true)
            .timezone("Europe/Paris")
            .conditions(List.of(TimeBetween.builder()
                .type(TimeBetween.class.getName())
                .before(Property.ofValue(before))
                .after(Property.ofValue(after))
                .build()
            ))
            .build();

        TriggerContext triggerContext = triggerContext(now, trigger).toBuilder().build();

        ConditionContext conditionContext = ConditionContext.builder()
            .runContext(runContextInitializer.forScheduler((DefaultRunContext) runContextFactory.of(), triggerContext, trigger))
            .build();

        Optional<ZonedDateTime> result = trigger.truePreviousNextDateWithCondition(trigger.executionTime(), conditionContext, now, true);
        assertThat(result).isNotEmpty();
    }
}
