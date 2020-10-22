package org.kestra.core.services;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.runners.RunContextFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
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
        return stream
            .collect(keepLastVersionCollector())
            .values()
            .stream();
    }

    public Collection<Flow> keepLastVersion(List<Flow> flows) {
        return flows
            .stream()
            .collect(keepLastVersionCollector())
            .values();
    }

    private java.util.stream.Collector<Flow, ?, HashMap<String, Flow>> keepLastVersionCollector() {
        return Collectors.toMap(
            Flow::uidWithRevision,
            e -> e,
            (left, right) -> left.getRevision() > right.getRevision() ? left : right,
            HashMap::new
        );
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
