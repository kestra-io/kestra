package io.kestra.executor;

import io.kestra.core.models.executions.Execution;
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

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            .map(f -> f.getTrigger().evaluate(
                Optional.empty(),
                runContextFactory.of(f.getFlow(), execution),
                f.getFlow(),
                execution
            ))
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
            .filter(flowWithFlowTrigger -> flowWithFlowTrigger.getTrigger().getPreconditions() != null || ListUtils.emptyOnNull(flowWithFlowTrigger.getTrigger().getConditions()).stream().anyMatch(MultipleCondition.class::isInstance))
            .toList();

        // short-circuit empty triggers to evaluate
        if (flowWithFlowTriggers.isEmpty()) {
            return Collections.emptyList();
        }

        List<FlowWithFlowTriggerAndMultipleCondition> flowWithMultipleConditionsToEvaluate = flowWithFlowTriggers.stream()
            .flatMap(flowWithFlowTrigger -> flowTriggerMultipleConditions(flowWithFlowTrigger)
                .map(multipleCondition -> new FlowWithFlowTriggerAndMultipleCondition(
                        flowWithFlowTrigger.getFlow(),
                        multipleConditionStorage.getOrCreate(flowWithFlowTrigger.getFlow(), multipleCondition, execution.getOutputs()),
                        flowWithFlowTrigger.getTrigger(),
                        multipleCondition
                    )
                )
            )
            // avoid evaluating expired windows (for ex for daily time window or deadline)
            .filter(flowWithFlowTriggerAndMultipleCondition -> flowWithFlowTriggerAndMultipleCondition.getMultipleConditionWindow().isValid(ZonedDateTime.now()))
            .toList();

        // evaluate multiple conditions
        Map<FlowWithFlowTriggerAndMultipleCondition, MultipleConditionWindow> multipleConditionWindowsByFlow = flowWithMultipleConditionsToEvaluate.stream().map(f -> {
                Map<String, Boolean> results = f.getMultipleCondition()
                    .getConditions()
                    .entrySet()
                    .stream()
                    .map(e -> new AbstractMap.SimpleEntry<>(
                        e.getKey(),
                        conditionService.isValid(e.getValue(), f.getFlow(), execution)
                    ))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                return Map.entry(f, f.getMultipleConditionWindow().with(results));
            })
            .filter(e -> !e.getValue().getResults().isEmpty())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // persist results
        multipleConditionStorage.save(new ArrayList<>(multipleConditionWindowsByFlow.values()));

        // compute all executions to create from flow triggers now that multiple conditions storage is populated
        List<Execution> executions = flowWithFlowTriggers.stream()
            // will evaluate conditions
            .filter(flowWithFlowTrigger ->
                conditionService.isValid(
                    flowWithFlowTrigger.getTrigger(),
                    flowWithFlowTrigger.getFlow(),
                    execution,
                    multipleConditionStorage
                )
            )
            // will evaluate preconditions
            .filter(flowWithFlowTrigger ->
                conditionService.isValid(
                    flowWithFlowTrigger.getTrigger().getPreconditions(),
                    flowWithFlowTrigger.getFlow(),
                    execution,
                    multipleConditionStorage
                )
            )
            .map(f -> f.getTrigger().evaluate(
                Optional.of(multipleConditionStorage),
                runContextFactory.of(f.getFlow(), execution),
                f.getFlow(),
                execution
            ))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

        // purge fulfilled or expired multiple condition windows
        Stream.concat(
            multipleConditionWindowsByFlow.entrySet().stream()
                .map(e -> Map.entry(
                    e.getKey().getMultipleCondition(),
                    e.getValue()
                ))
                .filter(e -> !Boolean.FALSE.equals(e.getKey().getResetOnSuccess()) &&
                    e.getKey().getConditions().size() == Optional.ofNullable(e.getValue().getResults()).map(Map::size).orElse(0)
                )
                .map(Map.Entry::getValue),
            multipleConditionStorage.expired(execution.getTenantId()).stream()
        ).forEach(multipleConditionStorage::delete);

        return executions;
    }

    private List<FlowWithFlowTrigger> computeFlowTriggers(Execution execution, Flow flow) {
        if (
            // prevent recursive flow triggers
            !flowService.removeUnwanted(flow, execution) ||
                // filter out Test Executions
                execution.getKind() != null ||
                // ensure flow & triggers are enabled
                flow.isDisabled() || flow instanceof FlowWithException ||
                flow.getTriggers() == null || flow.getTriggers().isEmpty()) {
            return Collections.emptyList();
        }

        return flowTriggers(flow).map(trigger -> new FlowWithFlowTrigger(flow, trigger))
            // filter on the execution state the flow listen to
            .filter(flowWithFlowTrigger -> flowWithFlowTrigger.getTrigger().getStates().contains(execution.getState().getCurrent()))
            // validate flow triggers conditions excluding multiple conditions
            .filter(flowWithFlowTrigger -> conditionService.valid(
                flowWithFlowTrigger.getFlow(),
                Optional.ofNullable(flowWithFlowTrigger.getTrigger().getConditions()).stream().flatMap(Collection::stream)
                    .filter(Predicate.not(MultipleCondition.class::isInstance))
                    .toList(),
                conditionService.conditionContext(
                    runContextFactory.of(flowWithFlowTrigger.getFlow(), execution),
                    flowWithFlowTrigger.getFlow(),
                    execution
                )
            )).toList();
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
    protected static class FlowWithFlowTriggerAndMultipleCondition {
        private final Flow flow;
        private final MultipleConditionWindow multipleConditionWindow;
        private final io.kestra.plugin.core.trigger.Flow trigger;
        private final MultipleCondition multipleCondition;
    }

    @AllArgsConstructor
    @Getter
    @ToString
    public static class FlowWithFlowTrigger {
        private final Flow flow;
        private final io.kestra.plugin.core.trigger.Flow trigger;
    }
}
