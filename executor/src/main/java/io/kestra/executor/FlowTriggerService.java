package io.kestra.executor;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.ListUtils;

import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Singleton
public class FlowTriggerService {
    private final ConditionService conditionService;
    private final RunContextFactory runContextFactory;
    private final FlowService flowService;

    public FlowTriggerService(ConditionService conditionService, RunContextFactory runContextFactory, FlowService flowService) {
        this.conditionService = conditionService;
        this.runContextFactory = runContextFactory;
        this.flowService = flowService;
    }

    public Stream<FlowWithFlowTrigger> withFlowTriggersOnly(Stream<FlowWithSource> allFlows) {
        return allFlows
            .filter(flow -> !flow.isDisabled())
            .filter(flow -> flow.getTriggers() != null && !flow.getTriggers().isEmpty())
            .flatMap(flow -> flowTriggers(flow).map(trigger -> new FlowWithFlowTrigger(flow, trigger)));
    }

    public Stream<io.kestra.plugin.core.trigger.Flow> flowTriggers(Flow flow) {
        return flow.getTriggers()
            .stream()
            .filter(Predicate.not(AbstractTrigger::isDisabled))
            .filter(io.kestra.plugin.core.trigger.Flow.class::isInstance)
            .map(io.kestra.plugin.core.trigger.Flow.class::cast);
    }

    /**
     * This method computes executions to trigger from flow triggers from a given execution.
     * It only computes those depending on standard (non-multiple / non-preconditions) conditions, so it must be used
     * in conjunction with {@link #computeExecutionsFromFlowTriggerPreconditions(Execution, Flow, MultipleConditionStorageInterface)}.
     */
    public List<Execution> computeExecutionsFromFlowTriggerConditions(Execution execution, Flow flow) {
        List<FlowWithFlowTrigger> flowWithFlowTriggers = computeFlowTriggers(execution, flow)
            .stream()
            // we must filter on no multiple conditions and no preconditions to avoid evaluating two times triggers that have standard conditions and multiple conditions
            .filter(it -> it.getTrigger().getPreconditions() == null && ListUtils.emptyOnNull(it.getTrigger().getConditions()).stream().noneMatch(MultipleCondition.class::isInstance))
            .toList();

        // short-circuit empty triggers to evaluate
        if (flowWithFlowTriggers.isEmpty()) {
            return Collections.emptyList();
        }

        // compute all executions to create from flow triggers without taken into account multiple conditions
        return flowWithFlowTriggers.stream()
            .map(
                f -> f.getTrigger().evaluate(
                    Optional.empty(),
                    runContextFactory.of(f.getFlow(), execution),
                    f.getFlow(),
                    execution
                )
            )
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }

    /**
     * This method computes executions to trigger from flow triggers from a given execution.
     * It only computes those depending on multiple conditions and preconditions, so it must be used
     * in conjunction with {@link #computeExecutionsFromFlowTriggerConditions(Execution, Flow)}.
     */
    public List<Execution> computeExecutionsFromFlowTriggerPreconditions(Execution execution, Flow flow, MultipleConditionStorageInterface multipleConditionStorage) {
        List<FlowWithFlowTrigger> flowWithFlowTriggers = computeFlowTriggers(execution, flow)
            .stream()
            // we must filter on multiple conditions or preconditions to avoid evaluating two times triggers that only have standard conditions
            .filter(
                flowWithFlowTrigger -> flowWithFlowTrigger.getTrigger().getPreconditions() != null
                    || ListUtils.emptyOnNull(flowWithFlowTrigger.getTrigger().getConditions()).stream().anyMatch(MultipleCondition.class::isInstance)
            )
            .toList();

        // short-circuit empty triggers to evaluate
        if (flowWithFlowTriggers.isEmpty()) {
            return Collections.emptyList();
        }

        // each trigger is evaluated while holding the lock on its multiple condition windows, so two executions cannot both trigger it
        List<Execution> executions = flowWithFlowTriggers.stream()
            .map(
                f -> multipleConditionStorage.process(
                    f.getFlow(),
                    flowTriggerMultipleConditions(f).toList(),
                    execution.getOutputs(),
                    (txStorage, windows) -> processMultipleConditionWindows(f, windows, execution, txStorage)
                )
            )
            .filter(Objects::nonNull)
            .toList();

        // purge expired multiple condition windows
        multipleConditionStorage.expired(execution.getTenantId()).forEach(multipleConditionStorage::delete);

        return executions;
    }

    private Execution processMultipleConditionWindows(FlowWithFlowTrigger flowWithFlowTrigger, List<MultipleConditionWindow> windows, Execution execution, MultipleConditionStorageInterface multipleConditionStorage) {
        Map<MultipleCondition, MultipleConditionWindow> multipleConditionWindows = new HashMap<>();
        // windows are sorted by condition id
        List<MultipleCondition> multipleConditions = flowTriggerMultipleConditions(flowWithFlowTrigger)
            .sorted(Comparator.comparing(MultipleCondition::getId))
            .toList();

        // evaluate multiple conditions, avoiding expired windows (for ex for daily time window or deadline)
        for (int i = 0; i < multipleConditions.size(); i++) {
            MultipleCondition multipleCondition = multipleConditions.get(i);
            MultipleConditionWindow window = windows.get(i);
            if (!window.isValid(ZonedDateTime.now())) {
                continue;
            }

            Map<String, Boolean> results = multipleCondition
                .getConditions()
                .entrySet()
                .stream()
                .map(
                    e -> new AbstractMap.SimpleEntry<>(
                        e.getKey(),
                        conditionService.isValid(e.getValue(), flowWithFlowTrigger.getFlow(), execution)
                    )
                )
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            MultipleConditionWindow updatedWindow = window.with(results);
            if (!updatedWindow.getResults().isEmpty()) {
                multipleConditionWindows.put(multipleCondition, updatedWindow);
            }
        }

        // persist results
        multipleConditionStorage.save(new ArrayList<>(multipleConditionWindows.values()));

        // compute the execution to create from the flow trigger now that multiple conditions storage is populated
        Execution triggered = null;
        if (
            // will evaluate conditions
            conditionService.isValid(flowWithFlowTrigger.getTrigger(), flowWithFlowTrigger.getFlow(), execution, multipleConditionStorage)
                // will evaluate preconditions
                && conditionService.isValid(flowWithFlowTrigger.getTrigger().getPreconditions(), flowWithFlowTrigger.getFlow(), execution, multipleConditionStorage)
        ) {
            triggered = flowWithFlowTrigger.getTrigger().evaluate(
                Optional.of(multipleConditionStorage),
                runContextFactory.of(flowWithFlowTrigger.getFlow(), execution),
                flowWithFlowTrigger.getFlow(),
                execution
            ).orElse(null);
        }

        // purge fulfilled multiple condition windows
        multipleConditionWindows.forEach((multipleCondition, window) ->
        {
            if (!Boolean.FALSE.equals(multipleCondition.getResetOnSuccess()) && multipleCondition.getConditions().size() == window.getResults().size()) {
                multipleConditionStorage.delete(window);
            }
        });

        return triggered;
    }

    private List<FlowWithFlowTrigger> computeFlowTriggers(Execution execution, Flow flow) {
        if (
            // prevent recursive flow triggers
            !flowService.removeUnwanted(flow, execution) ||
            // filter out Test Executions
            (execution.getKind() != null && execution.getKind() != ExecutionKind.NORMAL) ||
            // ensure flow & triggers are enabled
            flow.isDisabled() || flow instanceof FlowWithException ||
            flow.getTriggers() == null || flow.getTriggers().isEmpty()
        ) {
            return Collections.emptyList();
        }

        return flowTriggers(flow).map(trigger -> new FlowWithFlowTrigger(flow, trigger))
            // filter on the execution state the flow listen to
            .filter(flowWithFlowTrigger -> flowWithFlowTrigger.getTrigger().getStates().contains(execution.getState().getCurrent()))
            // validate flow triggers conditions excluding multiple conditions
            .filter(
                flowWithFlowTrigger -> conditionService.valid(
                    flowWithFlowTrigger.getFlow(),
                    Optional.ofNullable(flowWithFlowTrigger.getTrigger().getConditions()).stream().flatMap(Collection::stream)
                        .filter(Predicate.not(MultipleCondition.class::isInstance))
                        .toList(),
                    execution
                )
            ).toList();
    }

    private Stream<MultipleCondition> flowTriggerMultipleConditions(FlowWithFlowTrigger flowWithFlowTrigger) {
        Stream<MultipleCondition> legacyMultipleConditions = ListUtils.emptyOnNull(flowWithFlowTrigger.getTrigger().getConditions()).stream()
            .filter(MultipleCondition.class::isInstance)
            .map(MultipleCondition.class::cast);
        Stream<io.kestra.plugin.core.trigger.Flow.Preconditions> preconditions = Optional.ofNullable(flowWithFlowTrigger.getTrigger().getPreconditions()).stream();
        return Stream.concat(legacyMultipleConditions, preconditions);
    }

    @AllArgsConstructor
    @Getter
    @ToString
    public static class FlowWithFlowTrigger {
        private final Flow flow;
        private final io.kestra.plugin.core.trigger.Flow trigger;
    }
}
