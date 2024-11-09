package io.kestra.plugin.core.condition;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.TimeSLA;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Slf4j
public abstract class AbstractMultipleCondition extends Condition implements MultipleCondition {
    @NotNull
    @NotBlank
    @Pattern(regexp="^[a-zA-Z0-9][a-zA-Z0-9_-]*")
    @Schema(title = "A unique id for the condition")
    @PluginProperty
    private String id;

    @Schema(
        title = "SLA to define the time period (or window) for evaluating preconditions.",
        description = """
        You can set the `type` of `sla` to one of the following values:
        1. `DURATION_WINDOW`: this is the default `type`. It uses a start time (`windowAdvance`) and end time (`window`) that are moving forward to the next interval whenever the evaluation time reaches the end time, based on the defined duration `window`. For example, with a 1-day window (the default option: `window: PT1D`), the SLA conditions are always evaluated during 24h starting at midnight (i.e. at time 00:00:00) each day. If you set `windowAdvance: PT6H`, the window will start at 6 AM each day. If you set `windowAdvance: PT6H` and you also override the `window` property to `PT6H`, the window will start at 6 AM and last for 6 hours — as a result, Kestra will check the SLA conditions during the following time periods: 06:00 to 12:00, 12:00 to 18:00, 18:00 to 00:00, and 00:00 to 06:00, and so on.
        2. `SLIDING_WINDOW`: this option also evaluates SLA conditions over a fixed time `window`, but it always goes backward from the current time. For example, a sliding window of 1 hour (`window: PT1H`) will evaluate executions for the past hour (so between now and one hour before now). It uses a default window of 1 day.
        3. `DAILY_TIME_DEADLINE`: this option declares that some SLA conditions should be met "before a specific time in a day". With the string property `deadline`, you can configure a daily cutoff for checking conditions. For example, `deadline: "09:00:00"` means that the defined SLA conditions should be met from midnight until 9 AM each day; otherwise, the flow will not be triggered.
        4. `DAILY_TIME_WINDOW`: this option declares that some SLA conditions should be met "within a given time range in a day". For example, a window from `startTime: "06:00:00"` to `endTime: "09:00:00"` evaluates executions within that interval each day. This option is particularly useful for declarative definition of freshness conditions when building data pipelines. For example, if you only need one successful execution within a given time range to guarantee that some data has been successfully refreshed in order for you to proceed with the next steps of your pipeline, this option can be more useful than a strict DAG-based approach. Usually, each failure in your flow would block the entire pipeline, whereas with this option, you can proceed with the next steps of the pipeline as soon as the data is successfully refreshed at least once within the given time range.
        """
    )
    @PluginProperty
    @Builder.Default
    @Valid
    protected TimeSLA timeSLA = TimeSLA.builder().build();

    @Schema(
        title = "Whether to reset the evaluation results of SLA conditions after a first successful evaluation within the given time period.",
        description = """
            By default, after a successful evaluation of the set of SLA conditions, the evaluation result is reset, so, the same set of conditions needs to be successfully evaluated again in the same time period to trigger a new execution.
            This means that to create multiple executions, the same set of conditions needs to be evaluated to `true` multiple times.
            You can disable this by setting this property to `false` so that, within the same period, each time one of the conditions is satisfied again after a successful evaluation, it will trigger a new execution."""
    )
    @PluginProperty
    @NotNull
    @Builder.Default
    private Boolean resetOnSuccess = true;

    /**
     * This conditions will only validate previously calculated value on
     * {@link io.kestra.core.services.FlowTriggerService#computeExecutionsFromFlowTriggers(Execution, List, Optional)}} and {@link MultipleConditionStorageInterface#save(List)} by the executor.
     * The real validation is done here.
     */
    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        MultipleConditionStorageInterface multipleConditionStorage = conditionContext.getMultipleConditionStorage();
        Objects.requireNonNull(multipleConditionStorage);

        Optional<MultipleConditionWindow> triggerExecutionWindow = multipleConditionStorage.get(conditionContext.getFlow(), this.getId());

        Map<String, Boolean> results = getConditions()
            .keySet()
            .stream()
            .map(condition -> new AbstractMap.SimpleEntry<>(
                condition,
                (triggerExecutionWindow.isPresent() &&
                    triggerExecutionWindow.get().getResults() != null &&
                    triggerExecutionWindow.get().getResults().containsKey(condition) &&
                    triggerExecutionWindow.get().getResults().get(condition)
                )
            ))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        long validatedCount = results
            .entrySet()
            .stream()
            .filter(Map.Entry::getValue)
            .count();

        boolean result = getConditions().size() == validatedCount;

        if (result && log.isDebugEnabled()) {
            log.debug(
                "[namespace: {}] [flow: {}] Multiple conditions validated!",
                conditionContext.getFlow().getNamespace(),
                conditionContext.getFlow().getId()
            );
        } else if (log.isTraceEnabled()) {
            log.trace(
                "[namespace: {}] [flow: {}] Multiple conditions failed ({}/{}) with '{}'",
                conditionContext.getFlow().getNamespace(),
                conditionContext.getFlow().getId(),
                validatedCount,
                getConditions().size(),
                results
            );
        }

        return result;
    }
}
