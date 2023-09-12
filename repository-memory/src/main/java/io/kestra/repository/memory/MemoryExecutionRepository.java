package io.kestra.repository.memory;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.utils.DateUtils;
import io.micronaut.core.value.ValueException;
import io.micronaut.data.model.Pageable;
import io.reactivex.Flowable;
import jakarta.inject.Singleton;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

@Singleton
@MemoryRepositoryEnabled
public class MemoryExecutionRepository implements ExecutionRepositoryInterface {
    private final Map<String, Execution> executions = new HashMap<>();

    public Boolean isTaskRunEnabled() {
        return false;
    }

    @Override
    public ArrayListTotal<Execution> find(Pageable pageable, String query, String namespace, String flowId, ZonedDateTime startDate, ZonedDateTime endDate, List<State.Type> state, @Nullable Map<String, String> labels) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Flowable<Execution> find(@Nullable String query, @Nullable String namespace, @Nullable String flowId, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate, @Nullable List<State.Type> state, @Nullable Map<String, String> labels) {
        return null;
    }

    @Override
    public ArrayListTotal<TaskRun> findTaskRun(Pageable pageable, @Nullable String query, @Nullable String namespace, @Nullable String flowId, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate, @Nullable List<State.Type> states, @Nullable Map<String, String> labels) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Execution delete(Execution execution) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Integer purge(Execution execution) {
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
    public Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable List<FlowFilter> flows,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        boolean groupByNamespaceOnly
    ) {
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
        @Nullable DateUtils.GroupType groupBy,
        boolean isTaskRun
    ) {
        return Collections.emptyList();
    }

    @Override
    public Integer maxTaskRunSetting() {
        throw new UnsupportedOperationException();
    }
}
