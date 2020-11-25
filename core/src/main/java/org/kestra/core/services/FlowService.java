package org.kestra.core.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.runners.RunContextFactory;

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

                // edge case, 2 flows with same revision, we keep the not deleted
                final Flow finalFlow = flow;
                Optional<Flow> notDeleted = flows.stream()
                    .filter(f -> f.getRevision().equals(finalFlow.getRevision()) && !f.isDeleted())
                    .findFirst();

                if (notDeleted.isPresent()) {
                    flow = notDeleted.get();
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
}
