package org.kestra.core.repositories;

import io.micronaut.data.model.Pageable;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import org.kestra.core.models.flows.State;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ExecutionRepositoryInterface {
    Optional<Execution> findById(String id);

    ArrayListTotal<Execution> findByFlowId(String namespace, String id, Pageable pageable);

    ArrayListTotal<Execution> find(String query, Pageable pageable, State.Type state);

    List<DailyExecutionStatistics> dailyStatistics(String query);

    Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(String query);

    Execution save(Execution flow);
}
