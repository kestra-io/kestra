package io.kestra.core.models.triggers.types;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.services.ConditionService;
import io.kestra.core.validations.CronExpression;
import io.kestra.core.validations.TimezoneId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

@Slf4j
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@io.kestra.core.validations.Schedule
@Schema(
    title = "Schedule a flow based on a cron expression.",
    description = "Kestra is able to trigger a flow based on a schedule. If you need to wait for another system " +
        "to be ready and can't use any event mechanism, you can add one or more schedule(s) to a flow.\n" +
        "\n" +
        "The scheduler will keep the last execution date for this schedule. This allow you to change the " +
        "cron expression without restarting all past executions (if backfill exists).\n" +
        "If you changed the current ID, the scheduler will think it's a new schedule and will start with a fresh date and " +
        "replay all backfill dates (if backfill exists)."
)
@Plugin(
    examples = {
        @Example(
            title = "A schedule with a backfill.",
            code = {
                "triggers:",
                "  - id: schedule",
                "    type: io.kestra.core.models.triggers.types.Schedule",
                "    cron: \"*/15 * * * *\"",
                "    backfill:",
                "      start: 2020-06-25T14:00:00Z"
            },
            full = true
        ),
        @Example(
            title = "A schedule with a nickname.",
            code = {
                "triggers:",
                "  - id: schedule",
                "    type: io.kestra.core.models.triggers.types.Schedule",
                "    cron: \"@hourly\"",
            },
            full = true
        ),
        @Example(
            title = "A schedule that runs only on the first Monday on every month at 11 AM.",
            code = {
                "triggers:",
                "  - id: schedule",
                "    cron: \"0 11 * * 1\"",
                "    scheduleConditions:",
                "      - id: monday",
                "        type: io.kestra.core.models.conditions.types.DayWeekInMonthCondition",
                "        date: \"{{ trigger.date }}\"",
                "        dayOfWeek: \"MONDAY\"",
                "        dayInMonth: \"FIRST\"",
            },
            full = true
        )
    }

)
public class Schedule extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Schedule.Output> {
    public static final CronParser CRON_PARSER = new CronParser(CronDefinitionBuilder.defineCron()
        .withMinutes().withValidRange(0, 59).withStrictRange().and()
        .withHours().withValidRange(0, 23).withStrictRange().and()
        .withDayOfMonth().withValidRange(1, 31).withStrictRange().and()
        .withMonth().withValidRange(1, 12).withStrictRange().and()
        .withDayOfWeek().withValidRange(0, 7).withMondayDoWValue(1).withIntMapping(7, 0).withStrictRange().and()
        .withSupportedNicknameYearly()
        .withSupportedNicknameAnnually()
        .withSupportedNicknameMonthly()
        .withSupportedNicknameWeekly()
        .withSupportedNicknameDaily()
        .withSupportedNicknameMidnight()
        .withSupportedNicknameHourly()
        .instance()
    );

    @NotNull
    @CronExpression
    @Schema(
        title = "The cron expression.",
        description = "A standard [unix cron expression](https://en.wikipedia.org/wiki/Cron) without second.\n" +
            "Can also be a cron extension / nickname:\n" +
            "* `@yearly`\n" +
            "* `@annually`\n" +
            "* `@monthly`\n" +
            "* `@weekly`\n" +
            "* `@daily`\n" +
            "* `@midnight`\n" +
            "* `@hourly`"
    )
    @PluginProperty
    private String cron;

    @TimezoneId
    @Schema(
        title = "The time zone ID to use for evaluating the cron expression. Default value is the server default zone ID."
    )
    @PluginProperty
    @Builder.Default
    private String timezone = ZoneId.systemDefault().toString();

    @Schema(
        title = "Backfill option in order to fill missing past dates.",
        description = "Kestra could optionally handle a backfill. The concept of a backfill is to replay missing schedules when a flow is created but we need to schedule it before its creation date.\n" +
            "\n" +
            "A backfill will execute all schedules between a defined date and the current date, then the normal schedule will be executed."
    )
    @PluginProperty
    private ScheduleBackfill backfill;

    @Schema(hidden = true)
    @Builder.Default
    @Null
    private final Duration interval = null;

    @Valid
    @Schema(
        title = "List of schedule conditions in order to limit schedule date."
    )
    @PluginProperty
    private List<ScheduleCondition> scheduleConditions;

    @Schema(
        title = "The input to pass to the triggered flow."
    )
    @PluginProperty(dynamic = true)
    private Map<String, Object> inputs;

    @Schema(
        title = "The maximum late delay accepted.",
        description = "If the schedule didn't start after this delay, the execution will be skipped."
    )
    @PluginProperty
    private Duration lateMaximumDelay;

    @Getter(AccessLevel.NONE)
    private transient ExecutionTime executionTime;

    @Override
    public ZonedDateTime nextEvaluationDate(ConditionContext conditionContext, Optional<? extends TriggerContext> last) throws Exception {
        ExecutionTime executionTime = this.executionTime();
        ZonedDateTime nextDate;
        if (last.isPresent()) {
            ZonedDateTime lastDate = convertDateTime(last.get().getDate());
            // previous present & scheduleConditions
            if (this.scheduleConditions != null) {
                Optional<ZonedDateTime> next = this.truePreviousNextDateWithCondition(
                    executionTime,
                    conditionContext,
                    lastDate,
                    true
                );
                if (next.isPresent()) {

                    return next.get().truncatedTo(ChronoUnit.SECONDS);
                }
            }
            // previous present but no scheduleConditions
            nextDate = computeNextEvaluationDate(executionTime, lastDate).orElse(null);
        }
        // no previous present but backfill
        else if (backfill != null && backfill.getStart() != null) {
            nextDate = this.timezone != null ?
                backfill.getStart().withZoneSameLocal(ZoneId.of(this.timezone)) :
                backfill.getStart();
        }
        // no previous present & no backfill, just provide now
        else {
            nextDate = computeNextEvaluationDate(executionTime, ZonedDateTime.now()).orElse(null);
        }
        // if max delay reach, we calculate a new date
        if (this.lateMaximumDelay != null && nextDate != null) {
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
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        ExecutionTime executionTime = this.executionTime();
        ZonedDateTime currentDateTimeExecution = convertDateTime(context.getDate());

        Output scheduleDates = this.scheduleDates(executionTime, currentDateTimeExecution).orElse(null);

        if (scheduleDates == null || scheduleDates.getDate() == null) {
            return Optional.empty();
        }

        ZonedDateTime next = scheduleDates.getDate();

        // we are in the future don't allow
        // No use case, just here for prevention but it should never happen
        if (next.compareTo(ZonedDateTime.now().plus(Duration.ofSeconds(1))) > 0) {
            if (log.isTraceEnabled()) {
                log.trace("Schedule is in the future, execution skipped, this behavior should never happen.");
            }
            return Optional.empty();
        }

        // inject outputs variables for scheduleCondition
        conditionContext = conditionContext(conditionContext, scheduleDates);

        // FIXME make scheduleConditions generic
        // control scheduleConditions
        if (scheduleConditions != null) {
            boolean conditionResults = this.validateScheduleCondition(conditionContext);
            if (!conditionResults) {
                return Optional.empty();
            }

            // recalculate true output for previous and next based on conditions
            scheduleDates = this.trueOutputWithCondition(executionTime, conditionContext, scheduleDates);
        }

        Map<String, Object> inputs = new HashMap<>();

        // add flow inputs with default value
        var flow = conditionContext.getFlow();
        if (flow.getInputs() != null) {
            flow.getInputs().stream()
                .filter(input -> input.getDefaults() != null)
                .forEach(input -> inputs.put(input.getId(), input.getDefaults()));
        }

        if (this.inputs != null) {
            inputs.putAll(runContext.render(this.inputs));
        }

        Map<String, Object> variables;
        if (this.timezone != null) {
            variables = scheduleDates.toMap(ZoneId.of(this.timezone));
        } else {
            variables = scheduleDates.toMap();
        }

        ExecutionTrigger executionTrigger = ExecutionTrigger.of(this, variables);

        Execution execution = Execution.builder()
            .id(runContext.getTriggerExecutionId())
            .tenantId(context.getTenantId())
            .namespace(context.getNamespace())
            .flowId(context.getFlowId())
            .flowRevision(context.getFlowRevision())
            .labels(conditionContext.getFlow().getLabels())
            .state(new State())
            .trigger(executionTrigger)
            // keep to avoid breaking compatibility
            .variables(ImmutableMap.of(
                "schedule", executionTrigger.getVariables()
            ))
            .build();

        // add inputs and inject defaults
        if (!inputs.isEmpty()) {
            RunnerUtils runnerUtils = runContext.getApplicationContext().getBean(RunnerUtils.class);
            execution = execution.withInputs(runnerUtils.typedInputs(conditionContext.getFlow(), execution, inputs));
        }

        return Optional.of(execution);
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
        return conditionContext.withVariables(ImmutableMap.of(
            "schedule", output.toMap(),
            "trigger", output.toMap()
        ));
    }

    private synchronized ExecutionTime executionTime() {
        if (this.executionTime == null) {
            Cron parse = CRON_PARSER.parse(this.cron);

            this.executionTime = ExecutionTime.forCron(parse);
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

    private Output trueOutputWithCondition(ExecutionTime executionTime, ConditionContext conditionContext, Output output) {
        Output.OutputBuilder<?, ?> outputBuilder = Output.builder()
            .date(output.getDate());

        this.truePreviousNextDateWithCondition(executionTime, conditionContext, output.getDate(), true)
            .ifPresent(outputBuilder::next);

        this.truePreviousNextDateWithCondition(executionTime, conditionContext, output.getDate(), false)
            .ifPresent(outputBuilder::previous);

        return outputBuilder.build();
    }

    private Optional<ZonedDateTime> truePreviousNextDateWithCondition(ExecutionTime executionTime, ConditionContext conditionContext, ZonedDateTime toTestDate, boolean next) {
        while (
            (next && toTestDate.getYear() < ZonedDateTime.now().getYear() + 10) ||
                (!next && toTestDate.getYear() > ZonedDateTime.now().getYear() - 10)
        ) {
            Optional<ZonedDateTime> currentDate = next ?
                executionTime.nextExecution(toTestDate) :
                executionTime.lastExecution(toTestDate);

            if (currentDate.isEmpty()) {
                return currentDate;
            }

            Optional<Output> currentOutput = this.scheduleDates(executionTime, currentDate.get());

            if (currentOutput.isEmpty()) {
                return Optional.empty();
            }

            ConditionContext currentConditionContext = this.conditionContext(conditionContext, currentOutput.get());

            boolean conditionResults = this.validateScheduleCondition(currentConditionContext);
            if (conditionResults) {
                return currentDate;
            }

            toTestDate = currentDate.get();
        }

        return Optional.empty();
    }

    private Output handleMaxDelay(Output output) {
        if (output == null) {
            return null;
        }

        if (this.lateMaximumDelay == null) {
            return output;
        }

        while (
            (output.getDate().getYear() < ZonedDateTime.now().getYear() + 10) ||
                (output.getDate().getYear() > ZonedDateTime.now().getYear() - 10)
        ) {
            if (output.getDate().plus(this.lateMaximumDelay).compareTo(ZonedDateTime.now()) < 0) {
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

    private boolean validateScheduleCondition(ConditionContext conditionContext) {
        if (scheduleConditions != null) {
            ConditionService conditionService = conditionContext.getRunContext().getApplicationContext().getBean(ConditionService.class);
            return conditionService.isValid(
                conditionContext.getFlow(),
                scheduleConditions,
                conditionContext
            );
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

        @Schema(title = "The date of the next schedule.")
        @NotNull
        private ZonedDateTime next;

        @Schema(title = "The date of the previous schedule.")
        @NotNull
        private ZonedDateTime previous;
    }

    @Value
    @Builder
    public static class ScheduleBackfill {
        @Schema(
            title = "The first start date."
        )
        ZonedDateTime start;
    }
}
