package io.kestra.core.models.collectors;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final Map<String, Long> taskRunnerTypeCount;

    public static FlowUsage of(String tenantId, FlowRepositoryInterface flowRepository) {
        return FlowUsage.of(flowRepository.findAll(tenantId));
    }

    public static FlowUsage of(FlowRepositoryInterface flowRepository) {
        return FlowUsage.of(flowRepository.findAllForAllTenants());
    }

    public static FlowUsage of(List<Flow> flows) {
        return FlowUsage.builder()
            .count(count(flows))
            .namespacesCount(namespacesCount(flows))
            .taskTypeCount(taskTypeCount(flows))
            .triggerTypeCount(triggerTypeCount(flows))
            .taskRunnerTypeCount(taskRunnerTypeCount(flows))
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

    protected static Map<String, Long> taskRunnerTypeCount(List<Flow> allFlows) {
        return allFlows
            .stream()
            .flatMap(Flow::allTasks)
            .filter(t -> {
                try {
                    return t.getClass().getMethod("getTaskRunner") != null;
                } catch (NoSuchMethodException e) {
                    return false;
                }
            })
            .map(t -> {
                try {
                    TaskRunner<?> taskRunner = (TaskRunner<?>) t.getClass().getMethod("getTaskRunner").invoke(t);
                    return taskRunner != null ? taskRunner.getType() : null;
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(f -> f, Collectors.counting()));
    }
}
