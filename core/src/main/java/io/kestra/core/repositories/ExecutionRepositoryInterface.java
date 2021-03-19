package io.kestra.core.repositories;

import io.micronaut.data.model.Pageable;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.flows.State;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ExecutionRepositoryInterface extends SaveRepositoryInterface<Execution> {
    Optional<Execution> findById(String id);

    ArrayListTotal<Execution> findByFlowId(String namespace, String id, Pageable pageable);

    ArrayListTotal<Execution> find(String query, Pageable pageable, List<State.Type> state);

    ArrayListTotal<TaskRun> findTaskRun(String query, Pageable pageable, List<State.Type> state);

    Integer maxTaskRunSetting();

    List<DailyExecutionStatistics> dailyStatistics(String query, LocalDate startDate, LocalDate endDate, boolean isTaskRun);

    Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(String query, LocalDate startDate, LocalDate endDate);

    Execution save(Execution flow);
}
