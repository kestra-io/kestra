package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.executions.statistics.ExecutionCountStatistics;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.FlowScope;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.DateUtils;
import io.kestra.plugin.core.dashboard.data.Executions;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Flux;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public interface ExecutionRepositoryInterface extends SaveRepositoryInterface<Execution>, QueryBuilderInterface<Executions.Fields> {
    Boolean isTaskRunEnabled();

    default Optional<Execution> findById(String tenantId, String id) {
        return findById(tenantId, id, false);
    }

    Optional<Execution> findById(String tenantId, String id, boolean allowDeleted);

    Optional<Execution> findByIdWithoutAcl(String tenantId, String id);

    ArrayListTotal<Execution> findByFlowId(String tenantId, String namespace, String id, Pageable pageable);

    /**
     * Finds all the executions that was triggered by the given execution id.
     *
     * @param tenantId           the tenant id.
     * @param triggerExecutionId the id of the execution trigger.
     * @return a {@link Flux} of one or more executions.
     */
    Flux<Execution> findAllByTriggerExecutionId(String tenantId, String triggerExecutionId);

    /**
     * Finds the latest execution for the given flow and s.
     *
     * @param tenantId  The tenant ID.
     * @param namespace The namespace of execution.
     * @param flowId    The flow ID  of execution.
     * @param states     The execution's states.
     * @return an optional {@link Execution}.
     */
    Optional<Execution> findLatestForStates(String tenantId, String namespace, String flowId, List<State.Type> states);

    ArrayListTotal<Execution> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable List<FlowScope> scope,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state,
        @Nullable Map<String, String> labels,
        @Nullable String triggerExecutionId,
        @Nullable ChildFilter childFilter
    );

    default Flux<Execution> find(
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable List<FlowScope> scope,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state,
        @Nullable Map<String, String> labels,
        @Nullable String triggerExecutionId,
        @Nullable ChildFilter childFilter
    ) {
        return find(query, tenantId, scope, namespace, flowId, startDate, endDate, state, labels, triggerExecutionId, childFilter, false);
    }

    Flux<Execution> find(
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable List<FlowScope> scope,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state,
        @Nullable Map<String, String> labels,
        @Nullable String triggerExecutionId,
        @Nullable ChildFilter childFilter,
        boolean allowDeleted
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
        @Nullable List<FlowScope> scope,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable DateUtils.GroupType groupBy,
        List<State.Type> state,
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

    Map<String, ExecutionCountStatistics> executionCountsGroupedByNamespace(
        @Nullable String tenantId,
        @Nullable String namespace,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate
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
        @Nullable List<Flow> flows,
        @Nullable List<State.Type> states,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<String> namespaces);

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
