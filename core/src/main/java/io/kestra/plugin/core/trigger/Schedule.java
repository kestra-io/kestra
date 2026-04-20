package io.kestra.plugin.core.trigger;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.kestra.core.scheduler.SchedulerClock;
import io.kestra.core.validations.ScheduleValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.*;
import lombok.AccessLevel;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.throwPredicate;

@Slf4j
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Schedule a Flow with a CRON expression.",
    description = """
        Runs a Flow on a cron schedule (5 fields by default; enable seconds with `withSeconds`). Tracks last scheduled date to support backfill. Changing the trigger `id` starts a new schedule from “now”. Default timezone is UTC; override via `timezone`.

        Multiple Schedule triggers can coexist on one Flow."""
)
@Plugin(
    examples = {
        @Example(
            title = "Schedule a flow every 15 minutes.",
            full = true,
            code = """
                id: scheduled_flow
                namespace: company.team

                tasks:
                  - id: sleep_randomly
                    type: io.kestra.plugin.scripts.shell.Commands
                    taskRunner:
                      type: io.kestra.plugin.core.runner.Process
                    commands:
                      - echo "{{ trigger.date ?? execution.startDate }}"
                      - sleep $((RANDOM % 60 + 1))

                triggers:
                  - id: every_15_minutes
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "*/15 * * * *"
                """
        ),
        @Example(
            full = true,
            title = "Schedule a flow every day at 6:30 AM",
            code = """
                    id: daily_flow
                    namespace: company.team

                    tasks:
                      - id: log
                        type: io.kestra.plugin.core.log.Log
                        message: It's {{ trigger.date ?? taskrun.startDate | date("HH:mm") }}

                    triggers:
                      - id: schedule
                        type: io.kestra.plugin.core.trigger.Schedule
                        cron: 30 6 * * *
                """
        ),
        @Example(
            title = "Schedule a flow every hour using the cron nickname `@hourly`.",
            code = """
                id: scheduled_flow
                namespace: company.team

                tasks:
                  - id: log_hello_world
                    type: io.kestra.plugin.core.log.Log
                    message: Hello World! 🚀

                triggers:
                  - id: hourly
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "@hourly"
                """,
            full = true
        ),
        @Example(
            title = "Schedule a flow on the first Monday of the month at 11:00 AM.",
            code = """
                id: scheduled_flow
                namespace: company.team

                tasks:
                  - id: log_hello_world
                    type: io.kestra.plugin.core.log.Log
                    message: Hello World! 🚀

                triggers:
                  - id: schedule
                    cron: "0 11 * * 1"
                    conditions:
                      - type: io.kestra.plugin.core.condition.DayWeekInMonth
                        date: "{{ trigger.date }}"
                        dayOfWeek: "MONDAY"
                        dayInMonth: "FIRST"
                """,
            full = true
        ),
        @Example(
            title = "Schedule a flow every day at 9:00 AM and pause a schedule trigger after a failed execution using the `stopAfter` property.",
            full = true,
            code = """
                id: business_critical_flow
                namespace: company.team

                tasks:
                  - id: important_task
                    type: io.kestra.plugin.core.log.Log
                    message: "if this run fails, disable the schedule until the issue is fixed"

                triggers:
                  - id: stop_after_failed
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "0 9 * * *"
                    stopAfter:
                      - FAILED"""
        )
    }
)
@ScheduleValidation
public class Schedule extends AbstractTrigger implements Schedulable, TriggerOutput<Schedule.Output> {
    private static final CronDefinitionBuilder CRON_DEFINITION_BUILDER = CronDefinitionBuilder.defineCron()
        .withMinutes().withValidRange(0, 59).withStrictRange().and()
        .withHours().withValidRange(0, 23).withStrictRange().and()
        .withDayOfMonth().withValidRange(1, 31).supportsL().withStrictRange().and()
        .withMonth().withValidRange(1, 12).withStrictRange().and()
        .withDayOfWeek().withValidRange(0, 7).withMondayDoWValue(1).withIntMapping(7, 0).withStrictRange().and()
        .withSupportedNicknameYearly()
        .withSupportedNicknameAnnually()
        .withSupportedNicknameMonthly()
        .withSupportedNicknameWeekly()
        .withSupportedNicknameDaily()
        .withSupportedNicknameMidnight()
        .withSupportedNicknameHourly();

    private static final CronParser CRON_PARSER = new CronParser(CRON_DEFINITION_BUILDER.instance());
    private static final CronParser CRON_PARSER_WITH_SECONDS = new CronParser(CRON_DEFINITION_BUILDER.withSeconds().withValidRange(0, 59).withStrictRange().and().instance());

    @NotNull
    @Schema(
        title = "The cron expression.",
        description = """
            A standard [unix cron expression](https://en.wikipedia.org/wiki/Cron) with 5 fields (minutes precision). Using `withSeconds: true` you can switch to 6 fields and a seconds precision.
            Both `0` and `7` represent Sunday for the day-of-week field.
            Can also be a cron extension / nickname:
            * `@yearly`
            * `@annually`
            * `@monthly`
            * `@weekly`
            * `@daily`
            * `@midnight`
            * `@hourly`"""
    )
    @PluginProperty
    private String cron;

    @Schema(
        title = "Whether the cron expression has seconds precision",
        description = "By default, the cron expression has 5 fields. Setting this property to true allows for a 6th field to be used for seconds precision."
    )
    @NotNull
    @Builder.Default
    @PluginProperty
    private Boolean withSeconds = false;

    @PluginProperty
    @Builder.Default
    private String timezone = ZoneId.systemDefault().toString();

    @Schema(hidden = true)
    @Builder.Default
    @Null
    private final Duration interval = null;

    private Map<String, Object> inputs;

    @Schema(
        title = "The maximum delay that is accepted",
        description = "If the scheduled execution didn't start after this delay (e.g. due to infrastructure issues), the execution will be skipped."
    )
    @PluginProperty
    private Duration lateMaximumDelay;

    @Getter(AccessLevel.NONE)
    private transient ExecutionTime executionTime;

    @Getter(AccessLevel.NONE)
    private transient Cron cachedCron;

    private RecoverMissedSchedules recoverMissedSchedules;

    @Override
    public ZonedDateTime nextEvaluationDate(ConditionContext conditionContext, Optional<? extends TriggerContext> last) {
        ExecutionTime executionTime = this.executionTime();
        ZonedDateTime nextDate;
        Backfill backfill = null;
        if (last.isPresent() && (last.get().getBackfill() != null || last.get().getDate() != null)) {
            ZonedDateTime lastDate;
            if (last.get().getBackfill() != null) {
                backfill = last.get().getBackfill();
                lastDate = convertDateTime(backfill.getCurrentDate());
            } else {
                lastDate = convertDateTime(last.get().getDate());
            }

            // previous present & conditions
            if (this.getConditions() != null) {
                try {
                    Optional<ZonedDateTime> next = this.findNextDateMatchingConditions(
                        executionTime,
                        conditionContext,
                        lastDate
                    );

                    if (next.isPresent()) {
                        return next.get().truncatedTo(ChronoUnit.SECONDS);
                    }
                } catch (InternalException e) {
                    conditionContext.getRunContext().logger()
                        .warn("Unable to evaluate the conditions for the next evaluation date for trigger '{}', conditions will not be evaluated", this.getId());
                }
            }

            // previous present but no conditions
            nextDate = computeNextEvaluationDate(executionTime, lastDate).orElse(null);

            // if we have a current backfill but the nextDate
            // is after the end, then we calculate again the nextDate
            // based on now()
            if (backfill != null && nextDate != null && nextDate.isAfter(backfill.getEnd())) {
                nextDate = computeNextEvaluationDate(executionTime, convertDateTime(SchedulerClock.now())).orElse(null);
            }
        }
        // no previous present & no backfill or recover missed schedules, just provide now
        else {
            nextDate = computeNextEvaluationDate(executionTime, convertDateTime(SchedulerClock.now())).orElse(null);
        }

        // if max delay reached, we calculate a new date except if we are doing a backfill
        if (this.lateMaximumDelay != null && nextDate != null && backfill == null) {
            Output scheduleDates = this.scheduleDates(executionTime, nextDate).orElse(null);
            scheduleDates = this.handleMaxDelay(scheduleDates);
            if (scheduleDates != null) {
                nextDate = scheduleDates.getDate();
            } else {
                return null;
            }
        }

        return nextDate;
    }

    @Override
    public ZonedDateTime nextEvaluationDate() {
        // it didn't take into account the schedule condition, but as they are taken into account inside eval() it's OK.
        ExecutionTime executionTime = this.executionTime();
        return computeNextEvaluationDate(executionTime, convertDateTime(SchedulerClock.now())).orElse(convertDateTime(SchedulerClock.now()));
    }

    @Override
    public ZonedDateTime previousEvaluationDate(ConditionContext conditionContext) {
        ExecutionTime executionTime = this.executionTime();
        if (this.getConditions() != null) {
            try {
                Optional<ZonedDateTime> previous = this.findPreviousDateMatchingConditions(
                    executionTime,
                    conditionContext,
                    SchedulerClock.now()
                );

                if (previous.isPresent()) {
                    return previous.get().truncatedTo(ChronoUnit.SECONDS);
                }
            } catch (InternalException e) {
                conditionContext.getRunContext().logger()
                    .warn("Unable to evaluate the conditions for the next evaluation date for trigger '{}', conditions will not be evaluated", this.getId());
            }
        }
        return computePreviousEvaluationDate(executionTime, convertDateTime(SchedulerClock.now())).orElse(convertDateTime(SchedulerClock.now()));
    }

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext triggerContext) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        ExecutionTime executionTime = this.executionTime();
        ZonedDateTime currentDateTimeExecution = convertDateTime(triggerContext.getDate());

        final Backfill backfill = triggerContext.getBackfill();

        if (backfill != null) {
            if (backfill.getPaused()) {
                return Optional.empty();
            }
            currentDateTimeExecution = convertDateTime(backfill.getCurrentDate());
        }

        Output scheduleDates = this.scheduleDates(executionTime, currentDateTimeExecution).orElse(null);

        if (scheduleDates == null || scheduleDates.getDate() == null) {
            return Optional.empty();
        }

        final ZonedDateTime next = scheduleDates.getDate();

        // If the trigger is evaluated for 'back-fill', we have to make sure
        // that 'current-date' is strictly after the next execution date for an execution to be eligible.
        if (backfill != null && currentDateTimeExecution.isBefore(next)) {
            // Otherwise, skip the execution.
            return Optional.empty();
        }

        // we are in the future don't allow
        // No use case, just here for prevention but it should never happen
        if (next.compareTo(SchedulerClock.now().plus(Duration.ofSeconds(1))) > 0) {
            if (log.isTraceEnabled()) {
                log.trace("Schedule is in the future, execution skipped, this behavior should never happen.");
            }
            return Optional.empty();
        }

        // inject outputs variables for scheduleCondition
        conditionContext = conditionContext(conditionContext, scheduleDates);

        // control conditions
        if (this.getConditions() != null) {
            try {
                scheduleDates = this.trueOutputWithCondition(executionTime, conditionContext, scheduleDates);
            } catch (InternalException ie) {
                // validate schedule condition can fail to render variables
                // in this case, we return a failed execution so the trigger is not evaluated each second
                runContext.logger().error("Unable to evaluate the Schedule trigger '{}'", this.getId(), ie);
                return Optional.of(SchedulableExecutionFactory.createFailedExecution(this, conditionContext, triggerContext));
            }
        }

        Map<String, Object> variables;
        if (this.timezone != null) {
            variables = scheduleDates.toMap(ZoneId.of(this.timezone));
        } else {
            variables = scheduleDates.toMap();
        }

        Execution execution = SchedulableExecutionFactory.createExecution(
            this,
            conditionContext,
            triggerContext,
            variables,
            null
        );

        return Optional.of(execution);
    }

    /**
     * Parses and validates this trigger's cron expression.
     * <p>
     * The parsed {@link Cron} is memoized on the instance so repeat callers (e.g. every
     * {@link ScheduleValidation} invocation on the scheduling hot path) do not reconstruct
     * a new {@link CronParser} and rebuild its internal regex patterns each time. Throws
     * {@link IllegalArgumentException} on the first call if the cron is invalid; once the
     * cached value is returned the call is O(1).
     */
    public synchronized Cron parseCron() {
        if (this.cachedCron == null) {
            CronParser parser = Boolean.TRUE.equals(withSeconds) ? CRON_PARSER_WITH_SECONDS : CRON_PARSER;
            Cron parsed = parser.parse(this.cron);
            parsed.validate();
            this.cachedCron = parsed;
        }
        return this.cachedCron;
    }

    private Optional<Output> scheduleDates(ExecutionTime executionTime, ZonedDateTime date) {
        Optional<ZonedDateTime> next = executionTime.nextExecution(date.minus(Duration.ofSeconds(1)));

        if (next.isEmpty()) {
            return Optional.empty();
        }

        Output.OutputBuilder<?, ?> outputDatesBuilder = Output.builder()
            .date(convertDateTime(next.get()));

        computeNextEvaluationDate(executionTime, next.get())
            .map(this::convertDateTime)
            .ifPresent(outputDatesBuilder::next);

        executionTime.lastExecution(date)
            .map(this::convertDateTime)
            .ifPresent(outputDatesBuilder::previous);

        Output scheduleDates = outputDatesBuilder.build();

        return Optional.of(scheduleDates);
    }

    private ConditionContext conditionContext(ConditionContext conditionContext, Output output) {
        return conditionContext.withVariables(
            ImmutableMap.of(
                "schedule", output.toMap(),
                "trigger", output.toMap()
            )
        );
    }

    @VisibleForTesting
    synchronized ExecutionTime 
    executionTime() {
        if (this.executionTime == null) {
            Cron parsed = parseCron();
            this.executionTime = ExecutionTime.forCron(parsed);
        }

        return this.executionTime;
    }

    private ZonedDateTime convertDateTime(ZonedDateTime date) {
        if (this.timezone == null) {
            return date;
        }

        return date.withZoneSameInstant(ZoneId.of(this.timezone));
    }

    private Optional<ZonedDateTime> computeNextEvaluationDate(ExecutionTime executionTime, ZonedDateTime date) {
        return executionTime.nextExecution(date).map(zonedDateTime -> zonedDateTime.truncatedTo(ChronoUnit.SECONDS));
    }

    private Optional<ZonedDateTime> computePreviousEvaluationDate(ExecutionTime executionTime, ZonedDateTime date) {
        return executionTime.lastExecution(date).map(zonedDateTime -> zonedDateTime.truncatedTo(ChronoUnit.SECONDS));
    }

    private Output trueOutputWithCondition(ExecutionTime executionTime, ConditionContext conditionContext, Output output) throws InternalException {
        Output.OutputBuilder<?, ?> outputBuilder = Output.builder()
            .date(output.getDate());

        this.findNextDateMatchingConditions(executionTime, conditionContext, ZonedDateTime.from(output.getDate()))
            .ifPresent(outputBuilder::next);

        this.findPreviousDateMatchingConditions(executionTime, conditionContext, ZonedDateTime.from(output.getDate()))
            .ifPresent(outputBuilder::previous);

        return outputBuilder.build();
    }

    /**
     * Walks forward from {@code fromDate} through successive cron executions and returns the
     * first one where all schedule conditions match. Gives up after 10 years of lookahead.
     */
    @VisibleForTesting
    Optional<ZonedDateTime> findNextDateMatchingConditions(ExecutionTime executionTime, ConditionContext conditionContext, ZonedDateTime fromDate) throws InternalException {
        int upperYearBound = SchedulerClock.now().getYear() + 10;

        while (fromDate.getYear() < upperYearBound) {
            Optional<ZonedDateTime> candidate = executionTime.nextExecution(fromDate);
            if (candidate.isEmpty()) {
                return candidate;
            }

            Optional<Output> candidateOutput = this.scheduleDates(executionTime, candidate.get());
            if (candidateOutput.isEmpty()) {
                return Optional.empty();
            }

            if (allScheduleConditionsMatch(conditionContext, candidateOutput.get())) {
                return candidate;
            }

            fromDate = candidate.get().plusSeconds(1);
        }

        return Optional.empty();
    }

    /**
     * Walks backward from {@code fromDate} through preceding cron executions and returns the
     * first one where all schedule conditions match. Gives up after 10 years of lookback.
     */
    @VisibleForTesting
    Optional<ZonedDateTime> findPreviousDateMatchingConditions(ExecutionTime executionTime, ConditionContext conditionContext, ZonedDateTime fromDate) throws InternalException {
        int lowerYearBound = SchedulerClock.now().getYear() - 10;

        while (fromDate.getYear() > lowerYearBound) {
            Optional<ZonedDateTime> candidate = executionTime.lastExecution(fromDate);
            if (candidate.isEmpty()) {
                return candidate;
            }

            Optional<Output> candidateOutput = this.scheduleDates(executionTime, candidate.get());
            if (candidateOutput.isEmpty()) {
                return Optional.empty();
            }

            if (allScheduleConditionsMatch(conditionContext, candidateOutput.get())) {
                return candidate;
            }

            fromDate = candidate.get().minusSeconds(1);
        }

        return Optional.empty();
    }

    /**
     * Evaluates all {@link ScheduleCondition}s for a single cron-execution candidate, after
     * injecting {@code schedule} and {@code trigger} variables derived from that candidate's
     * schedule dates (so conditions like {@link io.kestra.plugin.core.condition.DayWeek} can
     * render {@code {{ trigger.date }}}).
     */
    private boolean allScheduleConditionsMatch(ConditionContext baseContext, Output candidateOutput) throws InternalException {
        ConditionContext contextWithSchedule = this.conditionContext(baseContext, candidateOutput);
        return this.validateScheduleCondition(contextWithSchedule);
    }

    private Output handleMaxDelay(Output output) {
        if (output == null) {
            return null;
        }

        if (this.lateMaximumDelay == null) {
            return output;
        }

        while (
            (output.getDate().getYear() < SchedulerClock.now().getYear() + 10) &&
                (output.getDate().getYear() > SchedulerClock.now().getYear() - 10)
        ) {
            if (output.getDate().plus(this.lateMaximumDelay).compareTo(SchedulerClock.now()) < 0) {
                output = this.scheduleDates(executionTime, output.getNext()).orElse(null);
                if (output == null) {
                    return null;
                }
            } else {
                return output;
            }
        }

        return output;
    }

    private boolean validateScheduleCondition(ConditionContext conditionContext) throws InternalException {
        final ConditionContext finalConditionContext;
        if (!conditionContext.getVariables().containsKey("trigger") && conditionContext.getVariables().containsKey("schedule")) {
            finalConditionContext = conditionContext.withVariables(
                ImmutableMap.<String, Object> builder()
                    .putAll(conditionContext.getVariables())
                    .put("trigger", conditionContext.getVariables().get("schedule"))
                    .build()
            );
        } else {
            finalConditionContext = conditionContext;
        }

        if (conditions != null) {
            return conditions.stream()
                .filter(c -> c instanceof ScheduleCondition)
                .map(c -> (ScheduleCondition) c)
                .allMatch(throwPredicate(condition -> condition.test(finalConditionContext)));
        }

        return true;
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The date of the current schedule.")
        @NotNull
        private ZonedDateTime date;

        @Schema(title = "The date of the next schedule")
        @NotNull
        private ZonedDateTime next;

        @Schema(title = "The date of the previous schedule")
        @NotNull
        private ZonedDateTime previous;
    }
}
