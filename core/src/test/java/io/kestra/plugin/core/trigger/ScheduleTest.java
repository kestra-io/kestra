package io.kestra.plugin.core.trigger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.cronutils.model.time.ExecutionTime;

import io.kestra.core.exceptions.InvalidTriggerConfigurationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.MultiselectInput;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.scheduler.SchedulerClock;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(
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
            .tasks(
                Collections.singletonList(
                    Return.builder()
                        .id("test")
                        .type(Return.class.getName())
                        .format(Property.ofValue("test"))
                        .build()
                )
            )
            .build();

        return TriggerContext.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
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

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent()).isTrue();
        assertThat(evaluate.get().labels()).hasSize(2);
        assertTrue(evaluate.get().labels().stream().anyMatch(label -> label.key().equals(Label.CORRELATION_ID)));
        assertTrue(evaluate.get().labels().stream().anyMatch(label -> label.equals(new Label(Label.FROM, "trigger"))));
        var vars = evaluate.get().trigger().getVariables();
        var inputs = evaluate.get().inputs();

        assertThat(dateFromVars((String) vars.get("date"), date)).isEqualTo(date);
        assertThat(dateFromVars((String) vars.get("next"), date)).isEqualTo(date.plusMonths(1));
        assertThat(dateFromVars((String) vars.get("previous"), date)).isEqualTo(date.minusMonths(1));
        // the flow's labels are deliberately absent: the execution takes them from the flow processed for
        // runtime when it is created, so carrying the raw flow's here would let them override governance
        assertThat(evaluate.get().labels()).doesNotContain(
            new Label("flow-label-1", "flow-label-1"),
            new Label("flow-label-2", "flow-label-2")
        );
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

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent()).isTrue();
        assertThat(evaluate.get().labels()).hasSize(2);
        assertTrue(evaluate.get().labels().stream().anyMatch(label -> label.key().equals(Label.CORRELATION_ID)));
        assertTrue(evaluate.get().labels().stream().anyMatch(label -> label.equals(new Label(Label.FROM, "trigger"))));
        var inputs = evaluate.get().inputs();

        assertThat(inputs.size()).isEqualTo(2);
        assertThat(inputs.get("input1")).isEqualTo("input1");
        assertThat(inputs.get("input2")).isEqualTo("default");
    }

    @Test
    void success_withLabels() throws Exception {
        var scheduleTrigger = Schedule.builder()
            .id("schedule").type(Schedule.class.getName())
            .cron("0 0 1 * *")
            .labels(
                List.of(
                    new Label("trigger-label-1", "trigger-label-1"),
                    new Label("trigger-label-2", "{{ 'trigger-label-2' }}"),
                    new Label("trigger-label-3", "{{ null }}"),
                    new Label("system.replay", "replay"),
                    new Label("system.test", "test")
                )
            )
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

        Optional<TriggerEvaluationResult> evaluate = scheduleTrigger.eval(conditionContext, triggerContext);

        assertThat(evaluate.isPresent()).isTrue();
        assertThat(evaluate.get().labels()).doesNotContain(new Label("system.replay", "replay"));
        assertThat(evaluate.get().labels()).doesNotContain(new Label("system.test", "test"));
        assertThat(evaluate.get().labels()).contains(new Label("trigger-label-1", "trigger-label-1"));
        assertThat(evaluate.get().labels()).contains(new Label("trigger-label-2", "trigger-label-2"));
        assertThat(evaluate.get().labels()).doesNotContain(new Label("trigger-label-3", ""));
    }

    @Test
    void everyMinute() throws Exception {
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("* * * * *").build();

        ZonedDateTime date = ZonedDateTime.now()
            .minus(Duration.ofMinutes(1))
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS)
            .plus(Duration.ofMinutes(1));

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent()).isTrue();
        var vars = evaluate.get().trigger().getVariables();

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

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent()).isTrue();
        var vars = evaluate.get().trigger().getVariables();

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
            .backfill(
                Backfill
                    .builder()
                    .currentDate(now.with(LocalTime.MIN))
                    .end(now.with(LocalTime.MAX))
                    .build()
            ).build();
        // When
        Optional<TriggerEvaluationResult> result = trigger.eval(conditionContext(trigger), triggerContext);
        // Then
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void shouldReturnExecutionForBackFillWhenCurrentDateIsAfterScheduleDate() throws Exception {
        // Given
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron(TEST_CRON_EVERYDAY_AT_8).build();
        ZonedDateTime now = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault());
        TriggerContext triggerContext = triggerContext(now, trigger).toBuilder()
            .backfill(
                Backfill
                    .builder()
                    .currentDate(now.with(LocalTime.MIN).plus(Duration.ofHours(8)))
                    .end(now.with(LocalTime.MAX))
                    .build()
            )
            .build();
        // When
        Optional<TriggerEvaluationResult> result = trigger.eval(conditionContext(trigger), triggerContext);

        // Then
        assertThat(result.isPresent()).isTrue();
    }

    @Test
    void shouldThrowWhenCronHasNoValidCalendarDate() {
        Schedule trigger = Schedule.builder()
            .id("schedule")
            .type(Schedule.class.getName())
            .cron("0 0 30 2 *")
            .build();

        assertThatThrownBy(trigger::nextEvaluationDate)
            .isInstanceOf(InvalidTriggerConfigurationException.class)
            .hasMessageContaining("0 0 30 2 *");

        assertThatThrownBy(() -> trigger.nextEvaluationDate(conditionContext(trigger), Optional.empty()))
            .isInstanceOf(InvalidTriggerConfigurationException.class)
            .hasMessageContaining("0 0 30 2 *");
    }

    @Test
    void shouldReturnEmptyWhenEvaluatingWithNullDate() throws Exception {
        Schedule trigger = Schedule.builder()
            .id("schedule")
            .type(Schedule.class.getName())
            .cron("0 0 * * *")
            .build();

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(
            conditionContext(trigger),
            TriggerContext.builder().date(null).build()
        );

        assertThat(evaluate).isEmpty();
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

        ZonedDateTime expected = date.withMinute(30)
            .plusMonths(1);

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent()).isTrue();
        var vars = evaluate.get().trigger().getVariables();
        assertThat(dateFromVars((String) vars.get("date"), expected)).isEqualTo(expected);
        assertThat(dateFromVars((String) vars.get("next"), expected)).isEqualTo(expected.plusMonths(1));
        assertThat(dateFromVars((String) vars.get("previous"), expected)).isEqualTo(expected.minusMonths(1));
    }

    @Test
    void conditions() throws Exception {
        Schedule trigger = Schedule.builder()
            .id("schedule")
            .type(Schedule.class.getName())
            .cron("0 12 * * 1")
            .timezone("Europe/Paris")
            .when("{{isDayWeekInMonth(trigger.date, 'MONDAY', 'FIRST')}}")
            .build();

        ZonedDateTime date = ZonedDateTime.parse("2021-08-02T12:00:00+02:00");
        ZonedDateTime previous = ZonedDateTime.parse("2021-07-05T12:00:00+02:00");
        ZonedDateTime next = ZonedDateTime.parse("2021-09-06T12:00:00+02:00");

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent()).isTrue();
        var execution = evaluate.get();
        if (execution.trigger() != null && execution.trigger().getVariables() != null) {
            var vars = execution.trigger().getVariables();
            assertThat(dateFromVars((String) vars.get("date"), date)).isEqualTo(date);
            assertThat(dateFromVars((String) vars.get("next"), next)).isEqualTo(next);
            assertThat(dateFromVars((String) vars.get("previous"), previous)).isEqualTo(previous);
        }
    }

    @Test
    void isDateBetweenConditions() throws Exception {
        Schedule trigger = Schedule.builder()
            .id("schedule")
            .type(Schedule.class.getName())
            .cron("0 12 * * 1")
            .timezone("Europe/Paris")
            .when("{{ isDateBetween(trigger.date, '2021-07-27T00:00:00Z', '2021-08-03T00:00:00Z') }}")
            .build();

        ZonedDateTime date = ZonedDateTime.parse("2021-08-02T12:00:00+02:00");

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent()).isTrue();
        var vars = evaluate.get().trigger().getVariables();
        assertThat(dateFromVars((String) vars.get("date"), date)).isEqualTo(date);
        assertThat(vars).doesNotContainKeys("previous", "next");
    }

    @Test
    void impossibleNextConditions() throws Exception {
        Schedule trigger = Schedule.builder()
            .id("schedule")
            .type(Schedule.class.getName())
            .cron("0 12 * * 1")
            .timezone("Europe/Paris")
            .when("{{trigger.date < '2021-08-02T12:00:00+02:00'}}")
            .build();

        ZonedDateTime date = ZonedDateTime.parse("2021-08-02T12:00:00+02:00");
        ZonedDateTime previous = ZonedDateTime.parse("2021-07-26T12:00:00+02:00");

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent()).isTrue();
        var execution = evaluate.get();
        var vars = execution.trigger().getVariables();
        if (vars != null) {
            assertThat(dateFromVars((String) vars.get("date"), date)).isEqualTo(date);
            assertThat(dateFromVars((String) vars.get("previous"), previous)).isEqualTo(previous);
            assertThat(vars.containsKey("next")).isFalse();
        }
    }

    @Test
    void neverMatchingWhenOnFrequentCronDoesNotHangTheEvaluation() throws Exception {
        Schedule trigger = Schedule.builder()
            .id("schedule")
            .type(Schedule.class.getName())
            .cron("* * * * * *")
            .withSeconds(true)
            .when("{{ false }}")
            .build();

        AtomicInteger tickCount = new AtomicInteger();
        Optional<ZonedDateTime> next = trigger.findNextDateMatchingConditions(
            countingExecutionTime(trigger.executionTime(), tickCount),
            conditionContext(trigger),
            ZonedDateTime.now()
        );

        assertThat(next).isEmpty();
        // A 10-year lookahead on a per-second cron would otherwise walk ~315M ticks (3 calls/tick
        // internally): bounding against the cap, with margin for that internal call count, proves
        // the iteration bound kicked in rather than the year bound.
        assertThat(tickCount.get())
            .as("the tick walk must be bounded by an iteration cap, not just a 10-year lookahead")
            .isBetween(Schedule.MAX_WHEN_CONDITION_ITERATIONS, Schedule.MAX_WHEN_CONDITION_ITERATIONS * 10);
    }

    private static ExecutionTime countingExecutionTime(ExecutionTime delegate, AtomicInteger counter) {
        return new ExecutionTime() {
            @Override
            public Optional<ZonedDateTime> nextExecution(ZonedDateTime date) {
                counter.incrementAndGet();
                return delegate.nextExecution(date);
            }

            @Override
            public Optional<Duration> timeToNextExecution(ZonedDateTime date) {
                return delegate.timeToNextExecution(date);
            }

            @Override
            public Optional<ZonedDateTime> lastExecution(ZonedDateTime date) {
                return delegate.lastExecution(date);
            }

            @Override
            public Optional<Duration> timeFromLastExecution(ZonedDateTime date) {
                return delegate.timeFromLastExecution(date);
            }

            @Override
            public boolean isMatch(ZonedDateTime date) {
                return delegate.isMatch(date);
            }
        };
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
            Optional.of(
                TriggerContext.builder()
                    .date(date)
                    .build()
            )
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

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(
            conditionContext(trigger),
            TriggerContext.builder()
                .date(date)
                .namespace("io.kestra.tests")
                .flowId(IdUtils.create())
                .build()
        );

        assertThat(evaluate.isPresent()).isTrue();
        var vars = evaluate.get().trigger().getVariables();
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

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(
            conditionContext(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent()).isTrue();
        var vars = evaluate.get().trigger().getVariables();

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

        ZonedDateTime fixedDate = ZonedDateTime.parse("2025-01-15T00:00:00-05:00[America/New_York]");
        TriggerContext triggerContext = triggerContext(fixedDate, trigger).toBuilder()
            .backfill(
                Backfill
                    .builder()
                    .currentDate(ZonedDateTime.parse("2025-01-15T08:00-05:00[America/New_York]"))
                    .end(ZonedDateTime.parse("2025-01-16T07:00-05:00[America/New_York]"))
                    .build()
            )
            .build();
        // When
        Optional<TriggerEvaluationResult> result = trigger.eval(conditionContext(trigger), triggerContext);

        // Then
        assertThat(result.isPresent()).isTrue();
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

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(
            conditionContextWithMultiselectInput(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent()).isTrue();
        var inputs = evaluate.get().inputs();

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

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(
            conditionContextWithMultiselectAutoSelectFirst(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent()).isTrue();
        var inputs = evaluate.get().inputs();

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

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(
            conditionContextWithMultiselectInput(trigger),
            triggerContext(date, trigger)
        );

        assertThat(evaluate.isPresent()).isTrue();
        var inputs = evaluate.get().inputs();

        // Verify provided value overrides defaults
        assertThat(inputs.get("multiselectInput")).isEqualTo(List.of("option3"));
    }

    @Test
    void shouldRenderInputDefaultsReferencingFlowLabels() throws Exception {
        // Given
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("0 0 1 * *").build();

        ZonedDateTime date = ZonedDateTime.now()
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS)
            .minusMonths(1);

        // When
        Optional<TriggerEvaluationResult> evaluate = trigger.eval(
            conditionContextWithLabelInput(trigger),
            triggerContext(date, trigger)
        );

        // Then
        assertThat(evaluate).isPresent();
        assertThat(evaluate.get().inputs()).containsEntry("labelEcho", "platform/critical");
        // the flow's labels are exposed for rendering only, never carried by the execution the executor creates
        assertThat(evaluate.get().labels()).doesNotContain(new Label("team", "platform"), new Label("app.tier", "critical"));
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
            .inputs(
                List.of(
                    StringInput.builder().id("input1").type(Type.STRING).required(false).build(),
                    StringInput.builder().id("input2").type(Type.STRING).defaults(Property.ofValue("default")).build()
                )
            )
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
                    new Label("flow-label-2", "flow-label-2")
                )
            )
            .variables(Map.of("custom_var", "VARIABLE VALUE"))
            .inputs(
                List.of(
                    MultiselectInput.builder()
                        .id("multiselectInput")
                        .type(Type.MULTISELECT)
                        .values(
                            List.of(
                                new io.kestra.core.models.flows.input.ValueOption("option1", "option1"),
                                new io.kestra.core.models.flows.input.ValueOption("option2", "option2"),
                                new io.kestra.core.models.flows.input.ValueOption("option3", "option3")
                            )
                        )
                        .defaults(Property.ofValue(List.of("option1", "option2")))
                        .build()
                )
            )
            .build();

        TriggerContext triggerContext = TriggerContext.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .triggerId(trigger.getId())
            .build();

        return ConditionContext.builder()
            .runContext(
                runContextInitializer.forScheduler(
                    (DefaultRunContext) runContextFactory.of(),
                    triggerContext, trigger
                )
            )
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
                    new Label("flow-label-2", "flow-label-2")
                )
            )
            .variables(Map.of("custom_var", "VARIABLE VALUE"))
            .inputs(
                List.of(
                    MultiselectInput.builder()
                        .id("multiselectAutoSelect")
                        .type(Type.MULTISELECT)
                        .values(
                            List.of(
                                new io.kestra.core.models.flows.input.ValueOption("first", "first"),
                                new io.kestra.core.models.flows.input.ValueOption("second", "second"),
                                new io.kestra.core.models.flows.input.ValueOption("third", "third")
                            )
                        )
                        .autoSelectFirst(true)
                        .build()
                )
            )
            .build();

        TriggerContext triggerContext = TriggerContext.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .triggerId(trigger.getId())
            .build();

        return ConditionContext.builder()
            .runContext(
                runContextInitializer.forScheduler(
                    (DefaultRunContext) runContextFactory.of(),
                    triggerContext, trigger
                )
            )
            .flow(flow)
            .build();
    }

    private ConditionContext conditionContextWithLabelInput(AbstractTrigger trigger) {
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.tests")
            .labels(
                List.of(
                    new Label("team", "platform"),
                    new Label("app.tier", "critical")
                )
            )
            .inputs(
                List.of(
                    StringInput.builder()
                        .id("labelEcho")
                        .type(Type.STRING)
                        .required(false)
                        // dotted keys are nested by Label.toNestedMap, so dot notation is the only working form
                        .defaults(Property.ofExpression("{{ labels.team }}/{{ labels.app.tier }}"))
                        .build()
                )
            )
            .build();

        TriggerContext triggerContext = TriggerContext.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .triggerId(trigger.getId())
            .build();

        return ConditionContext.builder()
            .runContext(
                runContextInitializer.forScheduler(
                    (DefaultRunContext) runContextFactory.of(),
                    triggerContext, trigger
                )
            )
            .flow(flow)
            .build();
    }

    private ZonedDateTime dateFromVars(String date, ZonedDateTime expected) {
        return ZonedDateTime.parse(date).withZoneSameInstant(expected.getZone());
    }

    @Test
    void testLastDayCron() throws Exception {
        Schedule trigger = Schedule.builder()
            .id("schedule")
            .type(Schedule.class.getName())
            .cron("0 12 L * *")
            .build();

        ZonedDateTime now = ZonedDateTime.now()
            .withDayOfMonth(1)
            .withHour(12)
            .withMinute(0)
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS);

        ZonedDateTime expected = now
            .withDayOfMonth(now.toLocalDate().lengthOfMonth());

        ZonedDateTime next = trigger.nextEvaluationDate(
            conditionContext(trigger),
            Optional.of(triggerContext(now, trigger))
        );

        assertThat(next).isNotNull();
        assertThat(next.getDayOfMonth()).isEqualTo(expected.getDayOfMonth());
        assertThat(next.getHour()).isEqualTo(12);
        assertThat(next.getMinute()).isEqualTo(0);
        assertThat(next.getSecond()).isEqualTo(0);
    }

    // Daylight-saving transitions
    //
    // a cron time that falls inside the hour removed by a spring-forward change must still
    // run once, at the local time shifted forward by the length of the gap (02:00 -> 03:00 in New York,
    // 02:30 -> 03:30 in Paris), as standard cron does. Fall-back must neither skip nor duplicate runs.
    //
    // Transitions used (verified against the JDK tz database):
    //   America/New_York     2026-03-08 02:00 -05:00 -> 03:00 -04:00 (gap), 2026-11-01 02:00 -04:00 -> 01:00 -05:00 (overlap)
    //   Europe/Paris         2026-03-29 02:00 +01:00 -> 03:00 +02:00 (gap)
    //   Australia/Lord_Howe  2026-10-04 02:00 +10:30 -> 02:30 +11:00 (30-minute gap)
    //   America/Havana       2026-03-08 00:00 -05:00 -> 01:00 -04:00 (gap at midnight)

    @AfterEach
    void resetSchedulerClock() {
        SchedulerClock.setClock(Clock.systemDefaultZone());
    }

    private Schedule scheduleIn(String cron, String timezone) {
        return Schedule.builder().id("schedule").type(Schedule.class.getName()).cron(cron).timezone(timezone).build();
    }

    /**
     * Walks {@code count} successive evaluation dates from {@code start}, the way the scheduler does
     * (each result becomes the "last" trigger date of the next call), rendered as offset date-times.
     */
    private List<String> nextDates(Schedule trigger, String start, int count) {
        ConditionContext context = conditionContext(trigger);
        ZonedDateTime current = ZonedDateTime.parse(start);
        List<String> dates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            current = trigger.nextEvaluationDate(context, Optional.of(triggerContext(current, trigger)));
            dates.add(current.toOffsetDateTime().toString());
        }
        return dates;
    }

    private static String offsetDateTime(Object isoDate) {
        return ZonedDateTime.parse((String) isoDate).toOffsetDateTime().toString();
    }

    // spring-forward: the skipped day must still run (these fail before the fix) ---

    @Test
    void dst_springForward_dailyNewYork() {
        assertThat(nextDates(scheduleIn("0 2 * * *", "America/New_York"), "2026-03-07T02:00-05:00[America/New_York]", 3))
            .containsExactly("2026-03-08T03:00-04:00", "2026-03-09T02:00-04:00", "2026-03-10T02:00-04:00");
    }

    @Test
    void dst_springForward_dailyParisKeepsMinuteOffset() {
        // 02:30 does not exist: shift by the gap length, so 03:30 (not 03:00)
        assertThat(nextDates(scheduleIn("30 2 * * *", "Europe/Paris"), "2026-03-28T02:30+01:00[Europe/Paris]", 3))
            .containsExactly("2026-03-29T03:30+02:00", "2026-03-30T02:30+02:00", "2026-03-31T02:30+02:00");
    }

    @Test
    void dst_springForward_thirtyMinuteGapLordHowe() {
        assertThat(nextDates(scheduleIn("15 2 * * *", "Australia/Lord_Howe"), "2026-10-03T02:15+10:30[Australia/Lord_Howe]", 2))
            .containsExactly("2026-10-04T02:45+11:00", "2026-10-05T02:15+11:00");
    }

    @Test
    void dst_springForward_gapAtMidnightHavana() {
        assertThat(nextDates(scheduleIn("0 0 * * *", "America/Havana"), "2026-03-07T00:00-05:00[America/Havana]", 2))
            .containsExactly("2026-03-08T01:00-04:00", "2026-03-09T00:00-04:00");
    }

    @Test
    void dst_springForward_catchUpIsFoundWhenLastDateIsBetweenTransitionAndShiftedRun() {
        // last date (03:10) is after the transition instant (03:00) but before the shifted run (03:30)
        assertThat(nextDates(scheduleIn("30 2 * * *", "Europe/Paris"), "2026-03-29T03:10+02:00[Europe/Paris]", 2))
            .containsExactly("2026-03-29T03:30+02:00", "2026-03-30T02:30+02:00");
    }

    @Test
    void dst_springForward_catchUpRunsOncePerGapEvenWithSeveralMatchesInTheGap() {
        assertThat(nextDates(scheduleIn("0,30 2 * * *", "America/New_York"), "2026-03-07T02:30-05:00[America/New_York]", 3))
            .containsExactly("2026-03-08T03:00-04:00", "2026-03-09T02:00-04:00", "2026-03-09T02:30-04:00");
    }

    @Test
    void dst_springForward_weeklyAndMonthlyExpressionsAreCaughtUpToo() {
        // 2026-03-08 is a Sunday
        assertThat(nextDates(scheduleIn("0 2 * * 0", "America/New_York"), "2026-03-01T02:00-05:00[America/New_York]", 2))
            .containsExactly("2026-03-08T03:00-04:00", "2026-03-15T02:00-04:00");
        assertThat(nextDates(scheduleIn("0 2 8 3 *", "America/New_York"), "2025-03-08T02:00-05:00[America/New_York]", 1))
            .containsExactly("2026-03-08T03:00-04:00");
    }

    // spring-forward: no duplicates and no change when a regular tick already covers the gap (pass before AND after the fix) ---

    @Test
    void dst_springForward_catchUpDoesNotDuplicateARegularRun() {
        // 02:00 shifts to 03:00, which is already a regular run: it must fire once
        assertThat(nextDates(scheduleIn("0 2,3 * * *", "America/New_York"), "2026-03-07T03:00-05:00[America/New_York]", 3))
            .containsExactly("2026-03-08T03:00-04:00", "2026-03-09T02:00-04:00", "2026-03-09T03:00-04:00");
    }

    @Test
    void dst_springForward_hourlyAndFrequentSchedulesAreUnchanged() {
        assertThat(nextDates(scheduleIn("0 * * * *", "America/New_York"), "2026-03-08T01:00-05:00[America/New_York]", 3))
            .containsExactly("2026-03-08T03:00-04:00", "2026-03-08T04:00-04:00", "2026-03-08T05:00-04:00");
        assertThat(nextDates(scheduleIn("*/15 * * * *", "America/New_York"), "2026-03-08T01:45-05:00[America/New_York]", 3))
            .containsExactly("2026-03-08T03:00-04:00", "2026-03-08T03:15-04:00", "2026-03-08T03:30-04:00");
    }

    @Test
    void dst_springForward_scheduleOutsideTheGapIsUnchanged() {
        assertThat(nextDates(scheduleIn("0 3 * * *", "America/New_York"), "2026-03-07T03:00-05:00[America/New_York]", 2))
            .containsExactly("2026-03-08T03:00-04:00", "2026-03-09T03:00-04:00");
        assertThat(nextDates(scheduleIn("0 2 * * *", "UTC"), "2026-03-07T02:00Z[UTC]", 2))
            .containsExactly("2026-03-08T02:00Z", "2026-03-09T02:00Z");
    }

    // fall-back: must stay correct (regression guard) ---

    @Test
    void dst_fallBack_dailyRunIsNotDuplicated() {
        // 01:00 happens twice on 2026-11-01 in New York; a daily run must fire only once that day
        List<String> dates = nextDates(scheduleIn("0 1 * * *", "America/New_York"), "2026-10-31T01:00-04:00[America/New_York]", 3);
        assertThat(dates).hasSize(3);
        assertThat(dates.stream().map(d -> d.substring(0, 10)).toList())
            .containsExactly("2026-11-01", "2026-11-02", "2026-11-03");
    }

    @Test
    void dst_fallBack_hourlyRunsOnBothRepeatedHours() {
        // in Paris 02:00-03:00 happens twice on 2026-10-25 (03:00+02:00 falls back to 02:00+01:00)
        assertThat(nextDates(scheduleIn("0 * * * *", "Europe/Paris"), "2026-10-25T01:00+02:00[Europe/Paris]", 4))
            .containsExactly("2026-10-25T02:00+02:00", "2026-10-25T02:00+01:00", "2026-10-25T03:00+01:00", "2026-10-25T04:00+01:00");
    }

    // eval()/previous consistency around the catch-up date ---

    @Test
    void dst_evalOnCatchUpDateExposesDateNextAndPrevious() throws Exception {
        Schedule trigger = scheduleIn("0 2 * * *", "America/New_York");
        ZonedDateTime date = ZonedDateTime.parse("2026-03-08T03:00-04:00[America/New_York]");

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(conditionContext(trigger), triggerContext(date, trigger));

        assertThat(evaluate).isPresent();
        var vars = evaluate.get().trigger().getVariables();
        assertThat(offsetDateTime(vars.get("date"))).isEqualTo("2026-03-08T03:00-04:00");
        assertThat(offsetDateTime(vars.get("previous"))).isEqualTo("2026-03-07T02:00-05:00");
        assertThat(offsetDateTime(vars.get("next"))).isEqualTo("2026-03-09T02:00-04:00");
    }

    @Test
    void dst_evalAfterCatchUpSeesCatchUpAsPrevious() throws Exception {
        Schedule trigger = scheduleIn("0 2 * * *", "America/New_York");
        ZonedDateTime date = ZonedDateTime.parse("2026-03-09T02:00-04:00[America/New_York]");

        Optional<TriggerEvaluationResult> evaluate = trigger.eval(conditionContext(trigger), triggerContext(date, trigger));

        assertThat(evaluate).isPresent();
        var vars = evaluate.get().trigger().getVariables();
        assertThat(offsetDateTime(vars.get("date"))).isEqualTo("2026-03-09T02:00-04:00");
        assertThat(offsetDateTime(vars.get("previous"))).isEqualTo("2026-03-08T03:00-04:00");
    }

    @Test
    void dst_previousEvaluationDateIncludesTheCatchUp() {
        Schedule trigger = scheduleIn("0 2 * * *", "America/New_York");
        ConditionContext context = conditionContext(trigger);

        // 2026-03-08T12:00 New York time, after the catch-up run
        SchedulerClock.setClock(Clock.fixed(Instant.parse("2026-03-08T16:00:00Z"), ZoneId.of("America/New_York")));
        assertThat(trigger.previousEvaluationDate(context).orElseThrow().toOffsetDateTime().toString())
            .isEqualTo("2026-03-08T03:00-04:00");

        // 2026-03-08T01:00 New York time, before the gap: the previous run is the day before
        SchedulerClock.setClock(Clock.fixed(Instant.parse("2026-03-08T06:00:00Z"), ZoneId.of("America/New_York")));
        assertThat(trigger.previousEvaluationDate(context).orElseThrow().toOffsetDateTime().toString())
            .isEqualTo("2026-03-07T02:00-05:00");
    }
}