package org.kestra.repository.memory;

import io.micronaut.core.value.ValueException;
import io.micronaut.data.model.Pageable;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import org.kestra.core.models.flows.State;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.repositories.ExecutionRepositoryInterface;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
@MemoryRepositoryEnabled
public class MemoryExecutionRepository implements ExecutionRepositoryInterface {
    private Map<String, Execution> executions = new HashMap<>();

    @Override
    public ArrayListTotal<Execution> find(String query, Pageable pageable, State.Type state) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayListTotal<TaskRun> findTaskRun(String query, Pageable pageable, State.Type state) {
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
    public Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(String query, LocalDate startDate, LocalDate endDate) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<DailyExecutionStatistics> dailyStatistics(String query, LocalDate startDate, LocalDate endDate, boolean isTaskRun) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Integer maxTaskRunSetting() {
        throw new UnsupportedOperationException();
    }
}
