package io.kestra.repository.memory;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.micronaut.core.value.ValueException;
import io.micronaut.data.model.Pageable;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;

import javax.annotation.Nullable;

@Singleton
@MemoryRepositoryEnabled
public class MemoryExecutionRepository implements ExecutionRepositoryInterface {
    private final Map<String, Execution> executions = new HashMap<>();

    @Override
    public ArrayListTotal<Execution> find(Pageable pageable, String query, String namespace, String flowId, ZonedDateTime startDate, ZonedDateTime endDate, List<State.Type> state) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayListTotal<TaskRun> findTaskRun(Pageable pageable, @io.micronaut.core.annotation.Nullable String query, @io.micronaut.core.annotation.Nullable String namespace, @io.micronaut.core.annotation.Nullable String flowId, @io.micronaut.core.annotation.Nullable ZonedDateTime startDate, @io.micronaut.core.annotation.Nullable ZonedDateTime endDate, @io.micronaut.core.annotation.Nullable List<State.Type> states) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Execution> findById(String executionId) {
        return executions.containsKey(executionId) ? Optional.of(executions.get(executionId)) : Optional.empty();
    }

    @Override
    public ArrayListTotal<Execution> findByFlowId(String namespace, String flowId, Pageable pageable) {
        if (pageable.getNumber() < 1) {
            throw new ValueException("Page cannot be < 1");
        }

        List<Execution> filteredExecutions = executions
            .values()
            .stream()
            .filter(e -> Objects.nonNull(namespace))
            .filter(e -> e.getNamespace().equals(namespace))
            .filter(e -> Objects.nonNull(e.getFlowId()))
            .filter(e -> e.getFlowId().equals(flowId))
            .collect(Collectors.toList());

        return ArrayListTotal.of(pageable, filteredExecutions);
    }

    @Override
    public Execution save(Execution execution) {
        return executions.put(execution.getId(), execution);
    }

    @Override
    public Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(@Nullable String query, @Nullable String namespace, @Nullable String flowId, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate, boolean groupByNamespaceOnly) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ExecutionCount> executionCounts(
        List<Flow> flows,
        @Nullable List<State.Type> states,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<DailyExecutionStatistics> dailyStatistics(
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        boolean isTaskRun
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Integer maxTaskRunSetting() {
        throw new UnsupportedOperationException();
    }
}
