package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.State;
import io.micronaut.data.model.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ExecutionRepositoryInterface extends SaveRepositoryInterface<Execution> {
    Optional<Execution> findById(String id);

    ArrayListTotal<Execution> findByFlowId(String namespace, String id, Pageable pageable);

    ArrayListTotal<Execution> find(String query, Pageable pageable, List<State.Type> state);

    ArrayListTotal<TaskRun> findTaskRun(String query, Pageable pageable, List<State.Type> state);

    Integer maxTaskRunSetting();

    List<DailyExecutionStatistics> dailyStatistics(String query, ZonedDateTime startDate, ZonedDateTime endDate, boolean isTaskRun);

    Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(String query, ZonedDateTime startDate, ZonedDateTime endDate, boolean groupByNamespaceOnly);

    List<ExecutionCount> executionCounts(List<Flow> flows, String query, ZonedDateTime startDate, ZonedDateTime endDate);

    Execution save(Execution flow);
}
