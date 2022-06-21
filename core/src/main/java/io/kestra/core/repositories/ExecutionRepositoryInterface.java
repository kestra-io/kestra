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
import java.util.function.Function;
import javax.annotation.Nullable;

public interface ExecutionRepositoryInterface extends SaveRepositoryInterface<Execution> {
    Boolean isTaskRunEnabled();

    Optional<Execution> findById(String id);

    ArrayListTotal<Execution> findByFlowId(String namespace, String id, Pageable pageable);

    ArrayListTotal<Execution> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state
    );

    ArrayListTotal<TaskRun> findTaskRun(
        Pageable pageable,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> states
    );

    Integer maxTaskRunSetting();

    List<DailyExecutionStatistics> dailyStatistics(
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        boolean isTaskRun
    );

    Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        boolean groupByNamespaceOnly
    );

    List<ExecutionCount> executionCounts(
        List<Flow> flows,
        @Nullable List<State.Type> states,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate
    );

    Execution save(Execution flow);

    default Function<String, String> sortMapping() throws IllegalArgumentException {
        return s -> s;
    }
}
