package io.kestra.core.services;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import io.kestra.core.models.conditions.types.MultipleCondition;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.ListUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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

    public Stream<Flow> keepLastVersion(Stream<Flow> stream) {
        return keepLastVersionCollector(stream);
    }

    public Flow keepLastVersion(Stream<Flow> stream, String namespace, String flowId) {
        return keepLastVersionCollector(
            stream
                .filter(flow -> flow.getNamespace().equals(namespace) && flow.getId().equals(flowId))
        )
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unable to find flow '" + namespace + "." + flowId + "'"));
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

    public List<FlowWithFlowTrigger> flowWithFlowTrigger(Stream<Flow> flowStream) {
        return flowStream
            .filter(flow -> flow.getTriggers() != null && flow.getTriggers().size() > 0)
            .filter(flow -> !flow.isDisabled())
            .flatMap(flow -> flow.getTriggers()
                .stream()
                .filter(abstractTrigger -> !abstractTrigger.isDisabled())
                .map(trigger -> new FlowWithTrigger(flow, trigger))
            )
            .filter(f -> f.getTrigger() instanceof io.kestra.core.models.triggers.types.Flow)
            .map(f -> new FlowWithFlowTrigger(
                    f.getFlow(),
                    (io.kestra.core.models.triggers.types.Flow) f.getTrigger()
                )
            )
            .collect(Collectors.toList());
    }

    protected boolean removeUnwanted(Flow f, Execution execution) {
        // we don't allow recursive
        return !f.uidWithoutRevision().equals(Flow.uidWithoutRevision(execution));
    }

    public List<Execution> flowTriggerExecution(Stream<Flow> flowStream, Execution execution, @Nullable MultipleConditionStorageInterface multipleConditionStorage) {
        return flowStream
            .filter(flow -> flow.getTriggers() != null && flow.getTriggers().size() > 0)
            .filter(flow -> !flow.isDisabled())
            .flatMap(flow -> flow.getTriggers()
                .stream()
                .filter(abstractTrigger -> !abstractTrigger.isDisabled())
                .map(trigger -> new FlowWithTrigger(flow, trigger))
            )
            .filter(f -> conditionService.isValid(
                f.getTrigger(),
                f.getFlow(),
                execution,
                multipleConditionStorage
            ))
            .filter(f -> f.getTrigger() instanceof io.kestra.core.models.triggers.types.Flow)
            .map(f -> new FlowWithFlowTrigger(
                    f.getFlow(),
                    (io.kestra.core.models.triggers.types.Flow) f.getTrigger()
                )
            )
            .filter(f -> this.removeUnwanted(f.getFlow(), execution))
            .map(f -> f.getTrigger().evaluate(
                runContextFactory.of(f.getFlow(), execution),
                f.getFlow(),
                execution
            ))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    private Stream<FlowWithFlowTriggerAndMultipleCondition> multipleFlowStream(
        Stream<Flow> flowStream,
        MultipleConditionStorageInterface multipleConditionStorage
    ) {
        return flowWithFlowTrigger(flowStream)
            .stream()
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

    public List<MultipleConditionWindow> multipleFlowTrigger(
        Stream<Flow> flowStream,
        Flow flow,
        Execution execution,
        MultipleConditionStorageInterface multipleConditionStorage
    ) {
        return multipleFlowStream(flowStream, multipleConditionStorage)
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
            .filter(multipleConditionWindow -> multipleConditionWindow.getResults().size() > 0)
            .collect(Collectors.toList());
    }

    public List<MultipleConditionWindow> multipleFlowToDelete(
        Stream<Flow> flowStream,
        MultipleConditionStorageInterface multipleConditionStorage
    ) {
        return Stream
            .concat(
                multipleFlowStream(flowStream, multipleConditionStorage)
                    .filter(f -> f.getMultipleCondition().getConditions().size() ==
                        (f.getMultipleConditionWindow().getResults() == null ? 0 :
                            f.getMultipleConditionWindow()
                                .getResults()
                                .entrySet()
                                .stream()
                                .filter(Map.Entry::getValue)
                                .count()
                        )
                    )
                    .map(FlowWithFlowTriggerAndMultipleCondition::getMultipleConditionWindow),
                multipleConditionStorage.expired().stream()
            )
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

    public static String cleanupSource(String source) {
        return source.replaceFirst("(?m)^revision: \\d+\n?","");
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
    public static class FlowWithFlowTrigger {
        private final Flow flow;
        private final io.kestra.core.models.triggers.types.Flow trigger;
    }

    @AllArgsConstructor
    @Getter
    @ToString
    private static class FlowWithFlowTriggerAndMultipleCondition {
        private final Flow flow;
        private final MultipleConditionWindow multipleConditionWindow;
        private final io.kestra.core.models.triggers.types.Flow trigger;
        private final MultipleCondition multipleCondition;
    }
}
