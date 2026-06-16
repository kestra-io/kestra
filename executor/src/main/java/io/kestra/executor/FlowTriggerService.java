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
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStateStore;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.TransactionContext;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.FlowService;

import io.kestra.core.utils.ListUtils;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

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
     * It only computes those depending on standard (non-dependsOn) conditions, so it must be used
     * in conjunction with {@link #computeExecutionsFromFlowTriggerDependsOn(Execution, Flow, MultipleConditionStateStore)}.
     */
    public List<Execution> computeExecutionsFromFlowTriggerConditions(Execution execution, Flow flow) {
        List<FlowWithFlowTrigger> flowWithFlowTriggers = computeFlowTriggers(execution, flow)
            .stream()
            // we must filter on no dependsOn to avoid evaluating two times triggers that have standard conditions and multiple conditions
            .filter(it -> ListUtils.isEmpty(it.getTrigger().getDependsOn()))
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
     * It only computes those depending on dependsOn, so it must be used
     * in conjunction with {@link #computeExecutionsFromFlowTriggerConditions(Execution, Flow)}.
     */
    public List<Execution> computeExecutionsFromFlowTriggerDependsOn(Execution execution, Flow flow, MultipleConditionStateStore multipleConditionStorage) {
        List<FlowWithFlowTrigger> flowWithFlowTriggers = computeFlowTriggers(execution, flow)
            .stream()
            // we must filter on dependsOn to avoid evaluating two times triggers that only have standard conditions
            .filter(flowWithFlowTrigger -> !ListUtils.isEmpty(flowWithFlowTrigger.getTrigger().getDependsOn()))
            .toList();

        // short-circuit empty triggers to evaluate
        if (flowWithFlowTriggers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Execution> executions = flowWithFlowTriggers.stream()
            .flatMap(flowWithFlowTrigger -> Optional.ofNullable(flowWithFlowTrigger.getTrigger().dependsOnAsMultipleCondition()).stream()
                .map(multipleCondition -> new FlowWithFlowTriggerAndMultipleCondition(
                    flowWithFlowTrigger.getFlow(),
                    flowWithFlowTrigger.getTrigger(),
                    multipleCondition)
            ))
            .map(flowWithMultipleCondition ->
                multipleConditionStorage.process(
                    flowWithMultipleCondition.getFlow(),
                    flowWithMultipleCondition.getMultipleCondition(),
                    buildOutputs(execution),
                        (txContext, multipleConditionWindow) -> processMultipleConditionWindow(txContext, flowWithMultipleCondition, multipleConditionWindow, execution, multipleConditionStorage)
                )
            )
            .filter(Objects::nonNull)
            .toList();

        // purge expired multiple condition windows
        multipleConditionStorage.expired(execution.getTenantId()).forEach(multipleConditionStorage::delete);

        return executions;
    }

    private Execution processMultipleConditionWindow(TransactionContext txContext, FlowWithFlowTriggerAndMultipleCondition flowWithMultipleCondition, MultipleConditionWindow multipleConditionWindow, Execution execution, MultipleConditionStateStore multipleConditionStateStore) {
        if (!multipleConditionWindow.isValid(ZonedDateTime.now())) {
            return null;
        }

        RunContext runContext = runContextFactory.of(null, execution);

        // evaluate multiple conditions and accumulate with previously stored results
        Map<String, Boolean> results = flowWithMultipleCondition.getMultipleCondition()
            .getConditions()
            .entrySet()
            .stream()
            .map(
                e -> new AbstractMap.SimpleEntry<>(
                    e.getKey(),
                    conditionService.isValid(e.getValue(), flowWithMultipleCondition.getFlow(), execution, runContext)
                )
            )
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // merge current results into the window (with() preserves previously true results across executions)
        MultipleConditionWindow updatedWindow = multipleConditionWindow.with(results);
        multipleConditionStateStore.save(txContext, updatedWindow);

        if (
            // evaluate conditions
            conditionService.isValid(flowWithMultipleCondition.getTrigger(), flowWithMultipleCondition.getFlow(), runContext) &&
                // evaluate dependsOn against the updated accumulated window
                conditionService.isValid(flowWithMultipleCondition.getTrigger().dependsOnAsMultipleCondition(), flowWithMultipleCondition.getFlow(), execution, Optional.of(updatedWindow), runContext)
        ) {
            Optional<Execution> maybeExecution = flowWithMultipleCondition.getTrigger().evaluate(
                Optional.of(updatedWindow),
                runContext,
                flowWithMultipleCondition.getFlow(),
                execution
            );

            return maybeExecution.orElse(null);
        }

        return null;
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
            (execution.getKind() != null && execution.getKind() != ExecutionKind.NORMAL) ||
            // ensure flow & triggers are enabled
            flow.isDisabled() || flow instanceof FlowWithException ||
            flow.getTriggers() == null || flow.getTriggers().isEmpty()
        ) {
            return Collections.emptyList();
        }

        RunContext runContext = runContextFactory.of(null, execution);
        return flowTriggers(flow).map(trigger -> new FlowWithFlowTrigger(flow, trigger))
            // filter on the execution state the flow listen to
            .filter(flowWithFlowTrigger -> flowWithFlowTrigger.getTrigger().getStates().contains(execution.getState().getCurrent()))
            // validate flow triggers conditions excluding multiple conditions
            .filter(
                flowWithFlowTrigger -> conditionService.isValid(
                    flowWithFlowTrigger.getTrigger(),
                    flowWithFlowTrigger.getFlow(),
                    runContext
                )
            ).toList();
    }

    @AllArgsConstructor
    @Getter
    @ToString
    protected static class FlowWithFlowTriggerAndMultipleCondition {
        private final Flow flow;
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
