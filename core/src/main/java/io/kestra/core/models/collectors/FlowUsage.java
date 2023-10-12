package io.kestra.core.models.collectors;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuperBuilder
@Getter
@Jacksonized
@Introspected
public class FlowUsage {
    private final Integer count;
    private final Long namespacesCount;
    private final Map<String, Long> taskTypeCount;
    private final Map<String, Long> triggerTypeCount;

    public static FlowUsage of(FlowRepositoryInterface flowRepository) {
        List<Flow> allFlows = flowRepository.findAllForAllTenants();

        return FlowUsage.builder()
            .count(count(allFlows))
            .namespacesCount(namespacesCount(allFlows))
            .taskTypeCount(taskTypeCount(allFlows))
            .triggerTypeCount(triggerTypeCount(allFlows))
            .build();
    }

    protected static int count(List<Flow> allFlows) {
        return allFlows.size();
    }

    protected static long namespacesCount(List<Flow> allFlows) {
        return allFlows
            .stream()
            .map(Flow::getNamespace)
            .distinct()
            .count();
    }

    protected static Map<String, Long> taskTypeCount(List<Flow> allFlows) {
        return allFlows
            .stream()
            .flatMap(f -> f.allTasks().map(Task::getType))
            .collect(Collectors.groupingBy(f -> f, Collectors.counting()));
    }

    protected static Map<String, Long> triggerTypeCount(List<Flow> allFlows) {
        return allFlows
            .stream()
            .flatMap(f -> f.getTriggers() != null ? f.getTriggers().stream().map(AbstractTrigger::getType) : Stream.empty())
            .collect(Collectors.groupingBy(f -> f, Collectors.counting()));
    }


}
