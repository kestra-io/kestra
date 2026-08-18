package io.kestra.core.models.executions;

import io.kestra.core.models.HasUID;

/**
 * A single indexed field value for an execution.
 * <p>
 * Stored in a dedicated table ({@code execution_indexed_fields}) as a plain string key/value pair so executions can
 * be searched efficiently. One row is stored per (execution, key).
 */
public record ExecutionIndexedField(
    String tenantId,
    String executionId,
    String key,
    String value,
    String namespace,
    String flowId) implements HasUID {
    /**
     * Synthetic stable identifier used as the table primary key. Built from the execution id and the field key so
     * re-computing an execution's indexed fields is idempotent.
     */
    @Override
    public String uid() {
        return executionId + "-" + key;
    }
}
