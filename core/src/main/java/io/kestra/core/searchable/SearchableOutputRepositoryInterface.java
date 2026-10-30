package io.kestra.core.searchable;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ArrayListTotal;
import com.google.common.annotations.VisibleForTesting;

import io.micronaut.data.model.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Searchable output repository interface.
 */
public interface SearchableOutputRepositoryInterface {

    /**
     * Save a searchable output: if it already exists, only its value is updated.
     */
    SearchableOutput save(SearchableOutput searchableOutput);

    /**
     * Save the searchable outputs, one per array item for an array output: a searchable output that already exists is
     * updated with the new value.
     *
     * @return the number of searchable outputs given, whether they were inserted or updated
     */
    int saveBatch(List<SearchableOutput> searchableOutputs);

    /**
     * Find a searchable output from its identifier, the first item for an array output.
     */
    @VisibleForTesting
    Optional<SearchableOutput> findById(String tenantId, String executionId, String outputId);

    /**
     * List the searchable outputs of an execution, ordered by output id then by array index.
     */
    ArrayListTotal<SearchableOutput> findByExecutionId(String tenantId, String executionId, Pageable pageable);

    /**
     * Purge searchable outputs (hard delete).
     *
     * @param executions the list of executions to purge searchable outputs for
     * @return the number of purged searchable outputs
     */
    int purge(List<Execution> executions);

    /**
     * Find the ids of the executions having a searchable output with the given output id and a value matching the given operation.
     * <p>
     * The negative operations only match executions that have the output with another value, not the executions without the output.
     *
     * @param operation one of {@link QueryFilter.Op#EQUALS}, {@link QueryFilter.Op#NOT_EQUALS}, {@link QueryFilter.Op#IN} or {@link QueryFilter.Op#NOT_IN}, matching is case-sensitive
     * @param value    the value to match: a {@link List} for {@link QueryFilter.Op#IN} and {@link QueryFilter.Op#NOT_IN}, a single value for the other operations
     * @param limit     the maximum number of execution ids to return
     * @throws IllegalArgumentException if the operation is not supported or if the value doesn't fit the operation
     */
    List<String> findExecutionIds(String tenantId, String outputId, QueryFilter.Op operation, Object value, int limit);
}
