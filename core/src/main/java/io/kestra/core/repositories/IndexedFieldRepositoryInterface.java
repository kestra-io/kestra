package io.kestra.core.repositories;

import java.util.List;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionIndexedField;

/**
 * Repository for execution indexed fields, used to store and retrieve the indexed fields of executions.
 * WARNING: don't use it directly, use the {@link io.kestra.core.services.IndexedFieldService}.
 */
public interface IndexedFieldRepositoryInterface {
    /**
     * Save an indexed field.
     */
    ExecutionIndexedField save(ExecutionIndexedField indexedField);

    /**
     * Find all indexed fields for a given execution.
     */
    List<ExecutionIndexedField> findByExecution(Execution execution);

    /**
     * Find execution ids matching an indexed field key and value.
     *
     * @param tenantId the tenant id
     * @param key the indexed field key
     * @param value the value to match
     * @param exactMatch when {@code true} matches the value exactly, otherwise uses a substring (contains) match
     * @return the matching execution ids
     */
    List<String> findExecutionIds(String tenantId, String key, String value, boolean exactMatch);

    /**
     * Purge (hard delete) all indexed fields for a given list of execution ids.
     *
     * @return the number of deleted indexed fields
     */
    int purgeByExecutionIds(List<String> executionIds);
}
