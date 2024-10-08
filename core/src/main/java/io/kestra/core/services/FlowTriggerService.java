package io.kestra.core.services;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.plugin.core.condition.MultipleCondition;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class FlowTriggerService {
    @Inject
    private ConditionService conditionService;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private FlowService flowService;

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

    public List<Execution> computeExecutionsFromFlowTriggers(Execution execution, List<Flow> allFlows, Optional<MultipleConditionStorageInterface> multipleConditionStorage) {
        List<FlowWithFlowTrigger> validTriggersBeforeMultipleConditionEval = allFlows.stream()
            // prevent recursive flow triggers
            .filter(flow -> flowService.removeUnwanted(flow, execution))
            // ensure flow & triggers are enabled
            .filter(flow -> !flow.isDisabled() && !(flow instanceof FlowWithException))
            .filter(flow -> flow.getTriggers() != null && !flow.getTriggers().isEmpty())
            .flatMap(flow -> flowTriggers(flow).map(trigger -> new FlowWithFlowTrigger(flow, trigger)))
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

        Map<FlowWithFlowTriggerAndMultipleCondition, MultipleConditionWindow> multipleConditionWindowsByFlow = null;
        if (multipleConditionStorage.isPresent()) {
            List<FlowWithFlowTriggerAndMultipleCondition> flowWithMultipleConditionsToEvaluate = validTriggersBeforeMultipleConditionEval.stream()
                .flatMap(flowWithFlowTrigger ->
                    Optional.ofNullable(flowWithFlowTrigger.getTrigger().getConditions()).stream().flatMap(Collection::stream)
                        .filter(MultipleCondition.class::isInstance)
                        .map(MultipleCondition.class::cast)
                        .map(multipleCondition -> new FlowWithFlowTriggerAndMultipleCondition(
                                flowWithFlowTrigger.getFlow(),
                                multipleConditionStorage.get().getOrCreate(flowWithFlowTrigger.getFlow(), multipleCondition),
                                flowWithFlowTrigger.getTrigger(),
                                multipleCondition
                            )
                        )
                ).toList();

            // evaluate multiple conditions
            multipleConditionWindowsByFlow = flowWithMultipleConditionsToEvaluate.stream().map(f -> {
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
            multipleConditionStorage.get().save(new ArrayList<>(multipleConditionWindowsByFlow.values()));
        }

        // compute all executions to create from flow triggers now that multiple conditions storage is populated
        List<Execution> executions = validTriggersBeforeMultipleConditionEval.stream().filter(flowWithFlowTrigger ->
                conditionService.isValid(
                    flowWithFlowTrigger.getTrigger(),
                    flowWithFlowTrigger.getFlow(),
                    execution,
                    multipleConditionStorage.orElse(null)
                )
            ).map(f -> f.getTrigger().evaluate(
                runContextFactory.of(f.getFlow(), execution),
                f.getFlow(),
                execution
            ))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

        if(multipleConditionStorage.isPresent() && multipleConditionWindowsByFlow != null) {
            // purge fulfilled or expired multiple condition windows
            Stream.concat(
                multipleConditionWindowsByFlow.keySet().stream()
                    .map(f -> Map.entry(
                        f.getMultipleCondition().getConditions(),
                        multipleConditionStorage.get().getOrCreate(f.getFlow(), f.getMultipleCondition())
                    ))
                    .filter(e -> e.getKey().size() == Optional.ofNullable(e.getValue().getResults())
                        .map(Map::size)
                        .orElse(0))
                    .map(Map.Entry::getValue),
                multipleConditionStorage.get().expired(execution.getTenantId()).stream()
            ).forEach(multipleConditionStorage.get()::delete);
        }

        return executions;
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
