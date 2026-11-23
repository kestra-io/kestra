package io.kestra.core.searchable;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ArrayListTotal;
import com.google.common.annotations.VisibleForTesting;

import io.micronaut.data.model.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Searchable data repository interface.
 */
public interface SearchableDataRepositoryInterface {

    /**
     * Save searchable data: if it already exists, only its value is updated.
     */
    SearchableData save(SearchableData searchableData);

    /**
     * Save the searchable data, one per array item for array data: a searchable data that already exists is
     * updated with the new value.
     *
     * @return the number of searchable data given, whether they were inserted or updated
     */
    int saveBatch(List<SearchableData> searchableData);

    /**
     * Find searchable data from its identifier, the first item for an array data.
     */
    @VisibleForTesting
    Optional<SearchableData> findById(String tenantId, String executionId, String dataId);

    /**
     * List the searchable data of an execution, ordered by data id then by array index.
     */
    ArrayListTotal<SearchableData> findByExecutionId(String tenantId, String executionId, Pageable pageable);

    /**
     * Purge searchable data (hard delete).
     *
     * @param executions the list of executions to purge searchable data for
     * @return the number of purged searchable data
     */
    int purge(List<Execution> executions);

    /**
     * Find the ids of the executions having searchable data with the given data id and a value matching the given operation.
     * <p>
     * The negative operations only match executions that have the data with another value, not the executions without the data.
     *
     * @param operation one of {@link QueryFilter.Op#EQUALS}, {@link QueryFilter.Op#NOT_EQUALS}, {@link QueryFilter.Op#IN} or {@link QueryFilter.Op#NOT_IN}, matching is case-sensitive
     * @param value    the value to match: a {@link List} for {@link QueryFilter.Op#IN} and {@link QueryFilter.Op#NOT_IN}, a single value for the other operations
     * @param limit     the maximum number of execution ids to return
     * @throws IllegalArgumentException if the operation is not supported, or if the value doesn't fit the operation
     */
    List<String> findExecutionIds(String tenantId, String dataId, QueryFilter.Op operation, Object value, int limit);
}
