package io.kestra.core.services;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Applies the dashboard filters describing an execution (state, scope, labels) to a logs chart.
 * <p>
 * A log line stores none of them and logs can live in another store than the executions, so there is no join to run.
 * The ids of the matching executions are looked up first, and the logs are then narrowed to those ids.
 */
@Singleton
public class LogsExecutionFilterService {
    public static final Set<QueryFilter.Field> EXECUTION_FILTER_FIELDS = Set.of(QueryFilter.Field.STATE, QueryFilter.Field.SCOPE, QueryFilter.Field.LABELS);

    private static final Set<QueryFilter.Field> SHARED_FILTER_FIELDS = Set.of(QueryFilter.Field.NAMESPACE, QueryFilter.Field.FLOW_ID);

    private final ExecutionRepositoryInterface executionRepository;
    private final int limit;

    @Inject
    public LogsExecutionFilterService(
        ExecutionRepositoryInterface executionRepository,
        @Value("${kestra.dashboards.logs-execution-filter-limit:10000}") int limit
    ) {
        this.executionRepository = Objects.requireNonNull(executionRepository);
        this.limit = limit;
    }

    /**
     * Replaces the execution-level filters with an {@code executionId IN (...)} filter on the executions they match, among
     * those that started within the time window.
     * <p>
     * The filters are returned untouched when none applies, or when more than the configured limit of executions match: the
     * logs chart then keeps reporting them as ignored rather than showing a truncated result.
     */
    public List<QueryFilter> resolve(String tenantId, List<QueryFilter> filters, ZonedDateTime startDate, ZonedDateTime endDate) {
        if (filters == null || filters.stream().noneMatch(f -> EXECUTION_FILTER_FIELDS.contains(f.field()))) {
            return filters;
        }

        List<QueryFilter> executionFilters = new ArrayList<>();
        List<QueryFilter> remaining = new ArrayList<>();
        for (QueryFilter filter : filters) {
            if (EXECUTION_FILTER_FIELDS.contains(filter.field())) {
                executionFilters.add(filter);
            } else {
                remaining.add(filter);
                if (SHARED_FILTER_FIELDS.contains(filter.field())) {
                    executionFilters.add(filter);
                }
            }
        }

        if (startDate != null) {
            executionFilters.add(QueryFilter.builder().field(QueryFilter.Field.START_DATE).operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO).value(startDate.toInstant().toString()).build());
        }
        if (endDate != null) {
            executionFilters.add(QueryFilter.builder().field(QueryFilter.Field.START_DATE).operation(QueryFilter.Op.LESS_THAN_OR_EQUAL_TO).value(endDate.toInstant().toString()).build());
        }

        List<String> executionIds = executionRepository.findAsync(tenantId, executionFilters)
            .map(Execution::getId)
            .take(limit + 1L)
            .collectList()
            .block();

        if (executionIds == null || executionIds.size() > limit) {
            return filters;
        }

        remaining.add(QueryFilter.builder().field(QueryFilter.Field.EXECUTION_ID).operation(QueryFilter.Op.IN).value(executionIds).build());

        return remaining;
    }
}
