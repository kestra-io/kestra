package io.kestra.core.models.triggers.types;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
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
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.validations.CronExpression;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Schedule a flow based on cron date",
    description = "Kestra is able to trigger flow based on Schedule (aka the time). If you need to wait another system " +
        "to be ready and can't use any event mechanism, you can schedule 1 or more time for the current flow.\n" +
        "\n" +
        "The scheduler will keep the last execution date for this schedule based on the id. This allow you to change the " +
        "cron expression without restart all the past execution (if backfill exists)\n" +
        "If you changed the current id, the scheduler will think it's a new schedule and will start with a fresh date and " +
        "replay the all backfill date (if backfill exists)"
)
@Plugin(
    examples = {
        @Example(
            title = "A schedule with a backfill",
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
            title = "A schedule that run only the first monday on every month at 11 AM",
            code = {
                "triggers:",
                "  - id: schedule",
                "    cron: \"0 11 * * 1\"",
                "    scheduleConditions:",
                "      - id: monday",
                "        date: \"{{ trigger.date }}\"" +
                "        dayOfWeek: \"MONDAY\"",
                "        dayInMonth: \"FIRST\"",
            },
            full = true
        )
    }

)
public class Schedule extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Schedule.Output> {
    private static final CronParser CRON_PARSER = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

    @NotNull
    @CronExpression
    @Schema(
        title = "the cron expression you need tyo ",
        description = "a standard [unix cron expression](https://en.wikipedia.org/wiki/Cron) without second."
    )
    private String cron;

    @Schema(
        title = "Backfill options in order to fill missing previous past date",
        description = "Kestra will handle optionnaly a backfill. The concept of backfill is the replay the missing schedule because we create the flow later.\n" +
            "\n" +
            "Backfill will do all schedules between define date & current date and will start after the normal schedule."
    )
    private ScheduleBackfill backfill;

    @Builder.Default
    private final Duration interval = null;

    @Valid
    @Schema(
        title = "List of schedule Conditions in order to limit schedule date."
    )
    private List<ScheduleCondition> scheduleConditions;

    @Override
    public ZonedDateTime nextEvaluationDate(Optional<? extends TriggerContext> last) {
        if (last.isPresent()) {
            return computeNextEvaluationDate(last.get().getDate()).orElse(null);
        } else {
            if (backfill != null && backfill.getStart() != null) {
                return backfill.getStart();
            }

            return computeNextEvaluationDate(ZonedDateTime.now()).orElse(null);
        }
    }

    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        Cron parse = CRON_PARSER.parse(this.cron);
        RunContext runContext = conditionContext.getRunContext();

        ExecutionTime executionTime = ExecutionTime.forCron(parse);
        Output output = this.output(executionTime, context.getDate()).orElse(null);

        if (output == null || output.getDate() == null) {
            return Optional.empty();
        }

        ZonedDateTime next = output.getDate();

        // we try at the exact time / standard behaviour
        boolean isReady = next.compareTo(context.getDate()) == 0;

        // in case on cron expression changed, the next date will never match, so we allow past operation to start
        boolean isLate = next.compareTo(ZonedDateTime.now().minus(Duration.ofMinutes(1))) < 0;

        if (!isReady && !isLate) {
            return Optional.empty();
        }

        // we are in the future don't allow
        if (next.compareTo(ZonedDateTime.now().plus(Duration.ofSeconds(1))) > 0) {
            return Optional.empty();
        }

        // inject outputs variables for scheduleCondition
        conditionContext = conditionContext(conditionContext, output);

        // control scheduleConditions
        if (scheduleConditions != null) {
            boolean conditionResults = this.validateScheduleCondition(conditionContext);
            if (!conditionResults) {
                return Optional.empty();
            }

            // recalculate true output for previous and next based on conditions
            output = this.trueOutputWithCondition(executionTime, conditionContext, output);
        }


        ExecutionTrigger executionTrigger = ExecutionTrigger.of(this, output);

        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .namespace(context.getNamespace())
            .flowId(context.getFlowId())
            .flowRevision(context.getFlowRevision())
            .state(new State())
            .trigger(executionTrigger)
            // keep to avoid breaking compatibility
            .variables(ImmutableMap.of(
                "schedule", executionTrigger.getVariables()
            ))
            .build();

        return Optional.of(execution);
    }

    private Optional<Output> output(ExecutionTime executionTime, ZonedDateTime date) {
        Optional<ZonedDateTime> next = executionTime.nextExecution(date.minus(Duration.ofSeconds(1)));

        if (next.isEmpty()) {
            return Optional.empty();
        }

        Output.OutputBuilder<?, ?> outputBuilder = Output.builder()
            .date(next.get());

        computeNextEvaluationDate(next.get())
            .ifPresent(outputBuilder::next);

        executionTime.lastExecution(date)
            .ifPresent(outputBuilder::previous);

        Output output = outputBuilder.build();

        return Optional.of(output);
    }

    private ConditionContext conditionContext(ConditionContext conditionContext, Output output) {
        return conditionContext.withVariables(ImmutableMap.of(
            "schedule", output.toMap(),
            "trigger", output.toMap()
        ));
    }

    private Optional<ZonedDateTime> computeNextEvaluationDate(ZonedDateTime date) {
        Cron parse = CRON_PARSER.parse(this.cron);
        ExecutionTime executionTime = ExecutionTime.forCron(parse);

        return executionTime.nextExecution(date).map(zonedDateTime -> zonedDateTime.truncatedTo(ChronoUnit.SECONDS));
    }

    private Output trueOutputWithCondition(ExecutionTime executionTime, ConditionContext conditionContext, Output output) {
        Output.OutputBuilder<?, ?> outputBuilder = Output.builder()
            .date(output.getDate());

        this.truePreviousNextDateWithCondition(executionTime, conditionContext, output, true)
            .ifPresent(outputBuilder::next);

        this.truePreviousNextDateWithCondition(executionTime, conditionContext, output, false)
            .ifPresent(outputBuilder::previous);

        return outputBuilder.build();
    }

    private Optional<ZonedDateTime> truePreviousNextDateWithCondition(ExecutionTime executionTime, ConditionContext conditionContext, Output output, boolean next) {
        ZonedDateTime toTestDate = output.getDate();

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

            Optional<Output> currentOutput = this.output(executionTime, currentDate.get());

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
        @Schema(title = "The date of current schedule ")
        @NotNull
        private ZonedDateTime date;

        @Schema(title = "The date of next schedule ")
        @NotNull
        private ZonedDateTime next;

        @Schema(title = "The date of previous schedule ")
        @NotNull
        private ZonedDateTime previous;
    }

    @Value
    @Builder
    public static class ScheduleBackfill {
        @Schema(
            title = "The first start date"
        )
        ZonedDateTime start;
    }
}
