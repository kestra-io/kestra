package io.kestra.core.repositories;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.flows.FlowScope;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.utils.DateUtils;
import io.kestra.plugin.core.dashboard.data.Executions;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Flux;

public interface ExecutionRepositoryInterface extends QueryBuilderInterface<Executions.Fields> {
    default Optional<Execution> findById(String tenantId, String id) {
        return findById(tenantId, id, false);
    }

    Optional<Execution> findById(String tenantId, String id, boolean allowDeleted);

    Optional<Execution> findByIdWithoutAcl(String tenantId, String id);

    ArrayListTotal<Execution> findByFlowId(String tenantId, String namespace, String id, Pageable pageable);

    /**
     * Finds all the executions that were triggered by the given execution id.
     *
     * @param tenantId the tenant id.
     * @param triggerExecutionId the id of the execution trigger.
     * @return a {@link Flux} of one or more executions.
     */
    Flux<Execution> findAllByTriggerExecutionId(String tenantId, String triggerExecutionId);

    /**
     * Finds all the executions that was triggered by the given trigger.
     *
     * @param triggerId the trigger id.
     * @return a {@link Flux} of one or more executions.
     */
    Flux<Execution> findAllByTrigger(TriggerId triggerId);

    /**
     * Finds the latest execution for the given flow and s.
     *
     * @param tenantId The tenant ID.
     * @param namespace The namespace of execution.
     * @param flowId The flow ID of execution.
     * @param states The execution's states.
     * @return an optional {@link Execution}.
     */
    Optional<Execution> findLatestForStates(String tenantId, String namespace, String flowId, List<State.Type> states);

    ArrayListTotal<Execution> find(
        Pageable pageable,
        @Nullable String tenantId,
        @Nullable List<QueryFilter> filters);

    default ArrayListTotal<Execution> find(
        Pageable pageable,
        @Nullable String tenantId,
        @Nullable List<QueryFilter> filters,
        @Nullable DateFilter dateFilter) {
        return find(pageable, tenantId, filters);
    }

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
        @Nullable ChildFilter childFilter) {
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
        boolean allowDeleted);

    Flux<Execution> findAllAsync(@Nullable String tenantId);

    Flux<Execution> findAsync(String tenantId, List<QueryFilter> filters);

    /**
     * WARNING: this method is only intended to be used in tests.
     */
    @VisibleForTesting
    Execution delete(Execution execution);

    boolean purge(Execution execution);

    Integer purge(List<Execution> executions);

    List<DailyExecutionStatistics> dailyStatisticsForAllTenants(
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable DateUtils.GroupType groupBy);

    List<DailyExecutionStatistics> dailyStatistics(
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable List<FlowScope> scope,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable DateUtils.GroupType groupBy,
        List<State.Type> state);

    @Getter
    @SuperBuilder
    @NoArgsConstructor
    class FlowFilter {
        @NotNull
        private String namespace;
        @NotNull
        private String id;
    }

    /**
     * WARNING: this method is only intended to be used in tests or inside the BackupService.
     */
    Execution save(Execution execution);

    default Function<String, String> sortMapping() throws IllegalArgumentException {
        return s -> s;
    }

    enum ChildFilter {
        CHILD,
        MAIN
    }

    /** Controls which execution date column(s) the time-based filters are applied against. */
    enum DateFilter {
        START_DATE,
        END_DATE,
        START_OR_END_DATE
    }

    List<Execution> lastExecutions(
        String tenantId,
        @Nullable List<FlowFilter> flows);

    /** Returns the loop sub-executions for the given parent execution, sorted by iteration index. */
    default List<Execution> findLoopSubExecutions(String tenantId, String parentExecutionId) {
        return find(
            Pageable.from(Sort.of(Sort.Order.asc("loopRunIndex"))),
            tenantId,
            List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.PARENT_ID)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(parentExecutionId)
                    .build(),
                QueryFilter.builder()
                    .field(QueryFilter.Field.KIND)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(ExecutionKind.LOOP)
                    .build()
            )
        );
    }
}
