package io.kestra.plugin.core.trigger;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.kestra.core.validations.TimezoneId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Slf4j
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Schedule a flow on specific dates."
)
@Plugin
public class ScheduleOnDates extends AbstractTrigger implements Schedulable, TriggerOutput<VoidOutput> {
    private static final String PLUGIN_PROPERTY_RECOVER_MISSED_SCHEDULES = "recoverMissedSchedules";

    @Schema(hidden = true)
    @Builder.Default
    @Null
    private final Duration interval = null;

    @Schema(
        title = "The inputs to pass to the scheduled flow."
    )
    @PluginProperty(dynamic = true)
    private Map<String, Object> inputs;

    @TimezoneId
    @Schema(
        title = "The [time zone identifier](https://en.wikipedia.org/wiki/List_of_tz_database_time_zones) (i.e. the second column in [the Wikipedia table](https://en.wikipedia.org/wiki/List_of_tz_database_time_zones#List)) to use for evaluating the cron expression. Default value is the server default zone ID."
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private String timezone = ZoneId.systemDefault().toString();

    @NotNull
    private Property<List<ZonedDateTime>> dates;

    @Schema(
        title = "What to do in case of missed schedules",
        description = "`ALL` will recover all missed schedules, `LAST`  will only recovered the last missing one, `NONE` will not recover any missing schedule.\n" +
            "The default is `ALL` unless a different value is configured using the global plugin configuration."
    )
    @PluginProperty
    private RecoverMissedSchedules recoverMissedSchedules;

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext triggerContext) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        ZonedDateTime lastEvaluation = triggerContext.getDate();
        Optional<ZonedDateTime> nextDate = nextDate(runContext, date -> date.isEqual(lastEvaluation) || date.isAfter(lastEvaluation));

        if (nextDate.isPresent()) {
            log.info("Schedule execution on {}", nextDate.get());

            Execution execution = TriggerService.generateScheduledExecution(
                this,
                conditionContext,
                triggerContext,
                generateLabels(conditionContext),
                this.inputs != null ? runContext.render(this.inputs) : Collections.emptyMap(),
                Collections.emptyMap(),
                nextDate
            );

            return Optional.of(execution);
        }

        return Optional.empty();
    }

    @Override
    public ZonedDateTime nextEvaluationDate(ConditionContext conditionContext, Optional<? extends TriggerContext> last) throws Exception {
        // lastEvaluation date is the last one from the trigger context or the first date of the list
        return last
            .map(throwFunction(context -> nextDate(conditionContext.getRunContext(), date -> date.isAfter(context.getDate()))
                .orElse(ZonedDateTime.now().plusYears(1) // it's not ideal, but we need a date or the trigger will keep evaluated
            )))
            .orElse(dates.asList(conditionContext.getRunContext(), ZonedDateTime.class).stream().sorted().findFirst().orElse(ZonedDateTime.now()))
            .truncatedTo(ChronoUnit.SECONDS);
    }

    @Override
    public ZonedDateTime nextEvaluationDate() {
        // TODO this may be the next date from now?
        return ZonedDateTime.now();
    }

    @Override
    public ZonedDateTime previousEvaluationDate(ConditionContext conditionContext) throws IllegalVariableEvaluationException {
        // the previous date is "the previous date of the next date"
        ZonedDateTime now = ZonedDateTime.now();
        List<ZonedDateTime> previousDates = dates.asList(conditionContext.getRunContext(), ZonedDateTime.class).stream()
            .sorted()
            .takeWhile(date -> date.isBefore(now))
            .toList()
            .reversed();

        return previousDates.isEmpty() ? ZonedDateTime.now() : previousDates.getFirst();
    }

    private Optional<ZonedDateTime> nextDate(RunContext runContext, Predicate<ZonedDateTime> filter) throws IllegalVariableEvaluationException {
        return dates.asList(runContext, ZonedDateTime.class).stream().sorted()
            .filter(date -> filter.test(date))
            .map(throwFunction(date -> timezone == null ? date : date.withZoneSameInstant(ZoneId.of(runContext.render(timezone)))))
            .findFirst()
            .map(date -> date.truncatedTo(ChronoUnit.SECONDS));
    }

    private List<Label> generateLabels(ConditionContext conditionContext) {
        List<Label> labels = new ArrayList<>();

        if (conditionContext.getFlow().getLabels() != null) {
            labels.addAll(conditionContext.getFlow().getLabels());
        }

        if (this.getLabels() != null) {
            labels.addAll(this.getLabels());
        }

        return labels;
    }
}
