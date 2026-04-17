package io.kestra.executor;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStateStore;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.FlowService;

import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.throwPredicate;

@Singleton
@Slf4j
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
     * It only computes those depending on standard (non-preconditions / non-dependsOn) conditions, so it must be used
     * in conjunction with {@link #computeExecutionsFromFlowTriggerPreconditionsAndDependsOn(Execution, Flow, MultipleConditionStateStore)}.
     */
    public List<Execution> computeExecutionsFromFlowTriggerConditions(Execution execution, Flow flow) {
        List<FlowWithFlowTrigger> flowWithFlowTriggers = computeFlowTriggers(execution, flow)
            .stream()
            // we must filter on no preconditions and no dependsOn to avoid evaluating two times triggers that have standard conditions and multiple conditions
            .filter(it -> it.getTrigger().getPreconditions() == null && ListUtils.isEmpty(it.getTrigger().getDependsOn()))
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
     * It only computes those depending on preconditions or dependsOn, so it must be used
     * in conjunction with {@link #computeExecutionsFromFlowTriggerConditions(Execution, Flow)}.
     */
    public List<Execution> computeExecutionsFromFlowTriggerPreconditionsAndDependsOn(Execution execution, Flow flow, MultipleConditionStateStore multipleConditionStorage) {
        List<FlowWithFlowTrigger> flowWithFlowTriggers = computeFlowTriggers(execution, flow)
            .stream()
            // we must filter on preconditions or dependsOn to avoid evaluating two times triggers that only have standard conditions
            .filter(flowWithFlowTrigger -> flowWithFlowTrigger.getTrigger().getPreconditions() != null || !ListUtils.isEmpty(flowWithFlowTrigger.getTrigger().getDependsOn()))
            .toList();

        // short-circuit empty triggers to evaluate
        if (flowWithFlowTriggers.isEmpty()) {
            return Collections.emptyList();
        }

        List<FlowWithFlowTriggerAndMultipleCondition> flowWithMultipleConditionsToEvaluate = flowWithFlowTriggers.stream()
            .flatMap(
                flowWithFlowTrigger -> flowTriggerMultipleConditions(flowWithFlowTrigger)
                    .map(
                        multipleCondition -> new FlowWithFlowTriggerAndMultipleCondition(
                            flowWithFlowTrigger.getFlow(),
                            multipleConditionStorage.getOrCreate(flowWithFlowTrigger.getFlow(), multipleCondition, buildOutputs(execution)),
                            flowWithFlowTrigger.getTrigger(),
                            multipleCondition
                        )
                    )
            )
            // avoid evaluating expired windows (for ex for daily time window or deadline)
            .filter(flowWithFlowTriggerAndMultipleCondition -> flowWithFlowTriggerAndMultipleCondition.getMultipleConditionWindow().isValid(ZonedDateTime.now()))
            .toList();

        // evaluate multiple conditions
        Map<FlowWithFlowTriggerAndMultipleCondition, MultipleConditionWindow> multipleConditionWindowsByFlow = flowWithMultipleConditionsToEvaluate.stream().map(f ->
        {
            Map<String, Boolean> results = f.getMultipleCondition()
                .getConditions()
                .entrySet()
                .stream()
                .map(
                    e -> new AbstractMap.SimpleEntry<>(
                        e.getKey(),
                        conditionService.isValid(e.getValue(), f.getFlow(), execution)
                    )
                )
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
            .filter(throwPredicate(
                flowWithFlowTrigger -> conditionService.isValid(
                    flowWithFlowTrigger.getTrigger(),
                    flowWithFlowTrigger.getFlow(),
                    execution,
                    multipleConditionStorage
                ))
            )
            // will evaluate preconditions and dependsOn
            .filter(
                flowWithFlowTrigger -> conditionService.isValid(
                    flowWithFlowTrigger.getTrigger().getPreconditions(),
                    flowWithFlowTrigger.getFlow(),
                    execution,
                    multipleConditionStorage
                ) && conditionService.isValid(
                        flowWithFlowTrigger.getTrigger().dependsOnAsMultipleCondition(),
                        flowWithFlowTrigger.getFlow(),
                        execution,
                        multipleConditionStorage
                    )
            )
            .map(
                f -> f.getTrigger().evaluate(
                    Optional.of(multipleConditionStorage),
                    runContextFactory.of(f.getFlow(), execution),
                    f.getFlow(),
                    execution
                )
            )
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

        // purge fulfilled or expired multiple condition windows
        Stream.concat(
            multipleConditionWindowsByFlow.entrySet().stream()
                .map(
                    e -> Map.entry(
                        e.getKey().getMultipleCondition(),
                        e.getValue()
                    )
                )
                .filter(
                    e -> !Boolean.FALSE.equals(e.getKey().getResetOnSuccess()) && isConditionSatisfied(e.getKey(), e.getValue())
                )
                .map(Map.Entry::getValue),
            multipleConditionStorage.expired(execution.getTenantId()).stream()
        ).forEach(multipleConditionStorage::delete);

        return executions;
    }

    private Map<String, Object> buildOutputs(Execution execution) {
        if (execution.getOutputs() == null) {
            return null;
        }

        return Map.of(
            execution.getNamespace(), Map.of(
                execution.getFlowId(), execution.getOutputs()
        ));
    }

    private List<FlowWithFlowTrigger> computeFlowTriggers(Execution execution, Flow flow) {
        if (
            // prevent recursive flow triggers
            !flowService.removeUnwanted(flow, execution) ||
            // filter out Test Executions
                execution.getKind() != null ||
                // ensure flow & triggers are enabled
                flow.isDisabled() || flow instanceof FlowWithException ||
                flow.getTriggers() == null || flow.getTriggers().isEmpty()
        ) {
            return Collections.emptyList();
        }

        RunContext runContext = runContextFactory.of(flow, execution);
        return flowTriggers(flow).map(trigger -> new FlowWithFlowTrigger(flow, trigger))
            // filter on the execution state the flow listen to
            .filter(flowWithFlowTrigger -> flowWithFlowTrigger.getTrigger().getStates().contains(execution.getState().getCurrent()))
            // validate flow triggers conditions excluding multiple conditions
            .filter(
                flowWithFlowTrigger -> conditionService.isValid(
                    flowWithFlowTrigger.getTrigger(),
                    flowWithFlowTrigger.getFlow(),
                    execution,
                    runContext,
                    null
                )
            ).toList();
    }

    private Stream<MultipleCondition> flowTriggerMultipleConditions(FlowWithFlowTrigger flowWithFlowTrigger) {
        return Stream.concat(
            Optional.ofNullable(flowWithFlowTrigger.getTrigger().getPreconditions()).stream(),
            Optional.ofNullable(flowWithFlowTrigger.getTrigger().dependsOnAsMultipleCondition()).stream()
        );

    }

    /**
     * Determines whether a multiple condition is satisfied based on its mode and the current results.
     * Used to decide whether to purge the condition window after a successful evaluation.
     */
    private boolean isConditionSatisfied(MultipleCondition condition, MultipleConditionWindow window) {
        int satisfiedCount = Optional.ofNullable(window.getResults()).map(Map::size).orElse(0);
        return switch (condition.getMode()) {
            case ALL -> condition.getConditions().size() == satisfiedCount;
            case ANY -> satisfiedCount > 0;
            case AT_LEAST -> satisfiedCount >= condition.getMinSatisfied();
        };
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
