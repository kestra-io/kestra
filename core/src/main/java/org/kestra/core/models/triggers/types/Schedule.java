package org.kestra.core.models.triggers.types;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.google.common.collect.ImmutableMap;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.Plugin;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.ExecutionTrigger;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.models.triggers.PollingTriggerInterface;
import org.kestra.core.models.triggers.TriggerContext;
import org.kestra.core.models.triggers.TriggerOutput;
import org.kestra.core.runners.RunContext;
import org.kestra.core.schedulers.validations.CronExpression;
import org.kestra.core.utils.IdUtils;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
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
    examples = @Example(
        code = {
            "triggers:",
            "  - id: schedule",
            "    type: org.kestra.core.models.triggers.types.Schedule",
            "    cron: \"*/15 * * * *\"",
            "    backfill:",
            "      start: 2020-06-25T14:00:00Z"
        }
    )
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

    public ZonedDateTime nextDate(Optional<? extends TriggerContext> last) {
        if (last.isPresent()) {
            return computeNextDate(last.get().getDate()).orElse(null);
        } else {
            if (backfill != null && backfill.getStart() != null) {
                return backfill.getStart();
            }

            return computeNextDate(ZonedDateTime.now()).orElse(null);
        }
    }

    public Optional<Execution> evaluate(RunContext runContext, TriggerContext context) throws Exception {
        Cron parse = CRON_PARSER.parse(this.cron);

        ExecutionTime executionTime = ExecutionTime.forCron(parse);
        Optional<ZonedDateTime> next = executionTime.nextExecution(context.getDate().minus(Duration.ofSeconds(1)));

        if (next.isEmpty()) {
            return Optional.empty();
        }

        // we try at the exact time / standard behaviour
        boolean isReady = next.get().compareTo(context.getDate()) == 0;

        // in case on cron expression changed, the next date will never match so we allow past operation to start
        boolean isLate = next.get().compareTo(ZonedDateTime.now().minus(Duration.ofMinutes(1))) < 0;

        if (!isReady && !isLate) {
            return Optional.empty();
        }

        // we are in the future don't allow
        if (next.get().compareTo(ZonedDateTime.now().plus(Duration.ofSeconds(1))) > 0) {
            return Optional.empty();
        }

        Output.OutputBuilder<?, ?> outputBuilder = Output.builder()
            .date(next.get());

        computeNextDate(next.get())
            .ifPresent(outputBuilder::next);

        executionTime.lastExecution(context.getDate())
            .ifPresent(outputBuilder::previous);

        Output output = outputBuilder.build();

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

    private Optional<ZonedDateTime> computeNextDate(ZonedDateTime date) {
        Cron parse = CRON_PARSER.parse(this.cron);
        ExecutionTime executionTime = ExecutionTime.forCron(parse);

        return executionTime.nextExecution(date).map(zonedDateTime -> zonedDateTime.truncatedTo(ChronoUnit.SECONDS));
    }


    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class Output implements org.kestra.core.models.tasks.Output {
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
