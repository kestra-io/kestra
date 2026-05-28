package io.kestra.core.repositories;

import java.util.List;

import io.kestra.core.models.QueryFilter;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;

/**
 * Capability interface for repositories that can return distinct values of one of their fields,
 * optionally narrowed by any combination of additional {@link QueryFilter} criteria.
 * <p>
 * Resource-specific repository interfaces (e.g. {@link ExecutionRepositoryInterface}) should
 * extend this interface to declare that they support the operation. Concrete implementations
 * typically inherit the behavior from the matching abstract base
 * ({@code AbstractJdbcCrudRepository} on the JDBC side, {@code AbstractElasticSearchRepository}
 * on the ElasticSearch side), so opting in is a one-line {@code extends} on the resource interface.
 */
public interface DistinctFieldValuesQueryInterface {
    /**
     * Returns distinct values of {@code field}, optionally narrowed by {@code filters}.
     * <p>
     * {@code filters} can reference any combination of fields (including {@code field} itself for
     * a same-field substring narrowing); they are combined with AND. When {@code filters} is null
     * or empty, the full distinct set under the tenant default filter is returned.
     * {@code pageable.getSize()} caps the number of distinct values returned.
     */
    List<String> findDistinctFieldValues(
        String tenantId,
        QueryFilter.Field field,
        @Nullable List<QueryFilter> filters,
        Pageable pageable
    );
}
