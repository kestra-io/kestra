package io.kestra.webserver.services;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.searchable.SearchableDataRepositoryInterface;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Resolves the {@link QueryFilter.Field#SEARCHABLE_DATA} filters of an execution search into filters on the execution id.
 * <p>
 * Searchable outputs live in their own table, so the executions that match are first resolved from it, then the executions
 * themselves are searched by id.
 */
@Singleton
public class SearchableDataFilterService {
    static final int MAX_EXECUTIONS = 10_000;

    private final SearchableDataRepositoryInterface searchableDataRepository;

    @Inject
    public SearchableDataFilterService(SearchableDataRepositoryInterface searchableDataRepository) {
        this.searchableDataRepository = Objects.requireNonNull(searchableDataRepository);
    }

    /**
     * Replaces each {@link QueryFilter.Field#SEARCHABLE_DATA} filter, including nested ones, by an {@link QueryFilter.Field#ID} filter.
     * A filter matching no execution is replaced by an id filter matching nothing.
     * The negative operations only match the executions having the data with another value.
     *
     * @throws InvalidQueryFiltersException if a searchable data filter has no data id or matches more than {@value #MAX_EXECUTIONS} executions
     */
    public List<QueryFilter> resolve(String tenantId, List<QueryFilter> filters) {
        if (!QueryFilter.hasField(filters, QueryFilter.Field.SEARCHABLE_DATA)) {
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
        if (filter.field() != QueryFilter.Field.SEARCHABLE_DATA) {
            return filter;
        }

        if (!QueryFilter.Field.SEARCHABLE_DATA.supportedOp().contains(filter.operation())) {
            throw new InvalidQueryFiltersException(
                "The operation %s is not supported on the searchable data filter, the supported operations are %s.".formatted(filter.operation(), QueryFilter.Field.SEARCHABLE_DATA.supportedOp())
            );
        }
        if (!(filter.value() instanceof Map<?, ?> map) || map.size() != 1 || map.values().iterator().next() == null) {
            throw new InvalidQueryFiltersException(
                "The searchable data filter requires a data id and a value, for example filters[searchableData][%s][<dataId>]=<value>.".formatted(filter.operation())
            );
        }
        var entry = map.entrySet().iterator().next();
        String dataId = String.valueOf(entry.getKey());
        boolean multiple = filter.operation() == QueryFilter.Op.IN || filter.operation() == QueryFilter.Op.NOT_IN;
        Object value = multiple ? toValues(entry.getValue()) : String.valueOf(entry.getValue());

        List<String> executionIds = searchableDataRepository.findExecutionIds(tenantId, dataId, filter.operation(), value, MAX_EXECUTIONS + 1);
        if (executionIds.size() > MAX_EXECUTIONS) {
            throw new InvalidQueryFiltersException(
                "The searchable data filter on '%s' matches more than %d executions, please use a more specific value.".formatted(dataId, MAX_EXECUTIONS)
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
