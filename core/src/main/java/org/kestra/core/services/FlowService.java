package org.kestra.core.services;

import io.micronaut.context.ApplicationContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.kestra.core.models.conditions.types.MultipleCondition;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import org.kestra.core.runners.RunContextFactory;
import org.kestra.core.utils.ListUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides business logic to manipulate {@link Flow}
 */
@Singleton
public class FlowService {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    ConditionService conditionService;

    @Inject
    ApplicationContext applicationContext;

    MultipleConditionStorageInterface multipleConditionStorage;

    public Stream<Flow> keepLastVersion(Stream<Flow> stream) {
        return keepLastVersionCollector(stream);
    }

    public Collection<Flow> keepLastVersion(List<Flow> flows) {
        return keepLastVersionCollector(flows.stream())
            .collect(Collectors.toList());
    }
    private Stream<Flow> keepLastVersionCollector(Stream<Flow> stream) {
        return stream
            .sorted((left, right) -> left.getRevision() > right.getRevision() ? -1 : (left.getRevision().equals(right.getRevision()) ? 0 : 1))
            .collect(Collectors.groupingBy(Flow::uidWithoutRevision))
            .values()
            .stream()
            .map(flows -> {
                Flow flow = flows.stream().findFirst().orElseThrow();

                // edge case, 2 flows with same revision, we keep the deleted
                final Flow finalFlow = flow;
                Optional<Flow> deleted = flows.stream()
                    .filter(f -> f.getRevision().equals(finalFlow.getRevision()) && f.isDeleted())
                    .findFirst();

                if (deleted.isPresent()) {
                    return null;
                }

                return flow.isDeleted() ? null : flow;
            })
            .filter(Objects::nonNull);
    }

    public List<Execution> flowTriggerExecution(Stream<Flow> flowStream, Execution execution) {
        return flowStream
            .filter(flow -> flow.getTriggers() != null && flow.getTriggers().size() > 0)
            .flatMap(flow -> flow.getTriggers()
                .stream()
                .map(trigger -> new FlowWithTrigger(flow, trigger))
            )
            .filter(f -> conditionService.isValid(
                f.getTrigger(),
                f.getFlow(),
                execution
            ))
            .filter(f -> f.getTrigger() instanceof org.kestra.core.models.triggers.types.Flow)
            .map(f -> new FlowWithFlowTrigger(
                    f.getFlow(),
                    (org.kestra.core.models.triggers.types.Flow) f.getTrigger()
                )
            )
            .map(f -> f.getTrigger().evaluate(
                runContextFactory.of(f.getFlow(), execution),
                f.getFlow(),
                execution
            ))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    private Stream<FlowWithFlowTriggerAndMultipleCondition> multipleFlowStream(Stream<Flow> flowStream) {
        // lazy init on kafka, we can't inject it
        if (this.multipleConditionStorage == null) {
            this.multipleConditionStorage = applicationContext.getBean(MultipleConditionStorageInterface.class);
        }

        return flowStream
            .filter(f -> f.getTriggers() != null && f.getTriggers().size() > 0)
            .flatMap(f -> f.getTriggers()
                .stream()
                .map(trigger -> new FlowWithTrigger(f, trigger))
            )
            .filter(f -> f.getTrigger() instanceof org.kestra.core.models.triggers.types.Flow)
            .map(e -> new FlowWithFlowTrigger(
                    e.getFlow(),
                    (org.kestra.core.models.triggers.types.Flow) e.getTrigger()
                )
            )
            .flatMap(e -> e.getTrigger()
                .getConditions()
                .stream()
                .filter(condition -> condition instanceof MultipleCondition)
                .map(condition -> {
                    MultipleCondition multipleCondition = (MultipleCondition) condition;

                    return new FlowWithFlowTriggerAndMultipleCondition(
                        e.getFlow(),
                        multipleConditionStorage.getOrCreate(e.getFlow(), multipleCondition),
                        e.getTrigger(),
                        multipleCondition
                    );
                })
            );
    }

    public List<MultipleConditionWindow> multipleFlowTrigger(Stream<Flow> flowStream, Flow flow, Execution execution) {
         return multipleFlowStream(flowStream)
            .map(f -> {
                Map<String, Boolean> results = f.getMultipleCondition()
                    .getConditions()
                    .entrySet()
                    .stream()
                    .map(e -> new AbstractMap.SimpleEntry<>(
                        e.getKey(),
                        conditionService.isValid(e.getValue(), flow, execution)
                    ))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                return f.getMultipleConditionWindow().with(results);
            })
             .collect(Collectors.toList());
    }

    public List<MultipleConditionWindow> multipleFlowToDelete(Stream<Flow> flowStream) {
        return multipleFlowStream(flowStream)
            .filter(f -> f.getMultipleCondition().getConditions().size() == f.getMultipleConditionWindow()
                .getResults()
                .entrySet()
                .stream()
                .filter(Map.Entry::getValue)
                .count()
            )
            .map(FlowWithFlowTriggerAndMultipleCondition::getMultipleConditionWindow)
            .collect(Collectors.toList());
    }


    public static List<AbstractTrigger> findRemovedTrigger(Flow flow, Flow previous) {
        return ListUtils.emptyOnNull(previous.getTriggers())
            .stream()
            .filter(p -> ListUtils.emptyOnNull(flow.getTriggers())
                .stream()
                .noneMatch(c -> c.getId().equals(p.getId()))
            )
            .collect(Collectors.toList());
    }

    @AllArgsConstructor
    @Getter
    private static class FlowWithTrigger {
        private final Flow flow;
        private final AbstractTrigger trigger;
    }

    @AllArgsConstructor
    @Getter
    @ToString
    private static class FlowWithFlowTrigger {
        private final Flow flow;
        private final org.kestra.core.models.triggers.types.Flow trigger;
    }

    @AllArgsConstructor
    @Getter
    @ToString
    private static class FlowWithFlowTriggerAndMultipleCondition {
        private final Flow flow;
        private final MultipleConditionWindow multipleConditionWindow;
        private final org.kestra.core.models.triggers.types.Flow trigger;
        private final MultipleCondition multipleCondition;
    }
}
