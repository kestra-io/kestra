package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.DateUtils;
import io.micronaut.data.model.Pageable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;

public interface ExecutionRepositoryInterface extends SaveRepositoryInterface<Execution> {
    Boolean isTaskRunEnabled();

    Optional<Execution> findById(String tenantId, String id, boolean allowDeleted);

    ArrayListTotal<Execution> findByFlowId(String tenantId, String namespace, String id, Pageable pageable);

    /**
     * Finds all the executions that was triggered by the given execution id.
     *
     * @param tenantId           the tenant id.
     * @param triggerExecutionId the id of the execution trigger.
     * @return a {@link Flux} of one or more executions.
     */
    Flux<Execution> findAllByTriggerExecutionId(String tenantId, String triggerExecutionId);

    ArrayListTotal<Execution> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state,
        @Nullable Map<String, String> labels,
        @Nullable String triggerExecutionId,
        @Nullable ChildFilter childFilter
    );

    Flux<Execution> find(
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state,
        @Nullable Map<String, String> labels,
        @Nullable String triggerExecutionId,
        @Nullable ChildFilter childFilter
    );

    ArrayListTotal<TaskRun> findTaskRun(
        Pageable pageable,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> states,
        @Nullable Map<String, String> labels,
        @Nullable String triggerExecutionId,
        @Nullable ChildFilter childFilter
    );

    Execution delete(Execution execution);

    Integer purge(Execution execution);

    Integer maxTaskRunSetting();

    List<DailyExecutionStatistics> dailyStatisticsForAllTenants(
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable DateUtils.GroupType groupBy,
        boolean isTaskRun
    );

    List<DailyExecutionStatistics> dailyStatistics(
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable DateUtils.GroupType groupBy,
        boolean isTaskRun
    );

    List<Execution> lastExecutions(
        @Nullable String tenantId,
        @Nullable List<FlowFilter> flows
    );

    Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable List<FlowFilter> flows,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        boolean groupByNamespaceOnly
    );

    @Getter
    @SuperBuilder
    @NoArgsConstructor
    class FlowFilter {
        @NotNull
        private String namespace;
        @NotNull
        private String id;
    }

    List<ExecutionCount> executionCounts(
        @Nullable String tenantId,
        List<Flow> flows,
        @Nullable List<State.Type> states,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate
    );

    Execution save(Execution execution);

    Execution update(Execution execution);

    default Function<String, String> sortMapping() throws IllegalArgumentException {
        return s -> s;
    }

    enum ChildFilter {
        CHILD,
        MAIN
    }
}
