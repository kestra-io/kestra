package io.kestra.webserver.services;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.searchable.SearchableOutputRepositoryInterface;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Resolves the {@link QueryFilter.Field#OUTPUTS} filters of an execution search into filters on the execution id.
 * <p>
 * Searchable outputs live in their own table, so the executions that match are first resolved from it, then the executions
 * themselves are searched by id.
 */
@Singleton
public class ExecutionOutputFilterService {
    static final int MAX_EXECUTIONS = 10_000;

    private final SearchableOutputRepositoryInterface searchableOutputRepository;

    @Inject
    public ExecutionOutputFilterService(SearchableOutputRepositoryInterface searchableOutputRepository) {
        this.searchableOutputRepository = Objects.requireNonNull(searchableOutputRepository);
    }

    /**
     * Replaces each {@link QueryFilter.Field#OUTPUTS} filter, including nested ones, by an {@link QueryFilter.Field#ID} filter.
     * A filter matching no execution is replaced by an id filter matching nothing.
     * The negative operations only match the executions having the output with another value.
     *
     * @throws InvalidQueryFiltersException if an outputs filter has no output id or matches more than {@value #MAX_EXECUTIONS} executions
     */
    public List<QueryFilter> resolve(String tenantId, List<QueryFilter> filters) {
        if (!QueryFilter.hasField(filters, QueryFilter.Field.OUTPUTS)) {
            return filters;
        }
        return filters.stream().map(filter -> resolve(tenantId, filter)).toList();
    }

    private QueryFilter resolve(String tenantId, QueryFilter filter) {
        if (filter.isNode()) {
            return QueryFilter.builder()
                .logical(filter.logical())
                .children(filter.children().stream().map(child -> resolve(tenantId, child)).toList())
                .build();
        }
        if (filter.field() != QueryFilter.Field.OUTPUTS) {
            return filter;
        }

        if (!QueryFilter.Field.OUTPUTS.supportedOp().contains(filter.operation())) {
            throw new InvalidQueryFiltersException(
                "The operation %s is not supported on the outputs filter, the supported operations are %s.".formatted(filter.operation(), QueryFilter.Field.OUTPUTS.supportedOp())
            );
        }
        if (!(filter.value() instanceof Map<?, ?> map) || map.size() != 1 || map.values().iterator().next() == null) {
            throw new InvalidQueryFiltersException(
                "The outputs filter requires an output id and a value, for example filters[outputs][%s][<outputId>]=<value>.".formatted(filter.operation())
            );
        }
        var entry = map.entrySet().iterator().next();
        String outputId = String.valueOf(entry.getKey());
        boolean multiple = filter.operation() == QueryFilter.Op.IN || filter.operation() == QueryFilter.Op.NOT_IN;
        Object value = multiple ? toValues(entry.getValue()) : String.valueOf(entry.getValue());

        List<String> executionIds = searchableOutputRepository.findExecutionIds(tenantId, outputId, filter.operation(), value, MAX_EXECUTIONS + 1);
        if (executionIds.size() > MAX_EXECUTIONS) {
            throw new InvalidQueryFiltersException(
                "The outputs filter on '%s' matches more than %d executions, please use a more specific value.".formatted(outputId, MAX_EXECUTIONS)
            );
        }

        return QueryFilter.builder()
            .field(QueryFilter.Field.ID)
            .operation(QueryFilter.Op.IN)
            .value(executionIds)
            .build();
    }

    private static List<String> toValues(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return Arrays.asList(String.valueOf(value).split(","));
    }
}
