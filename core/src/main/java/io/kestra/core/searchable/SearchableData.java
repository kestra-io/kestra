package io.kestra.core.searchable;

import jakarta.validation.constraints.NotNull;

/**
 * A searchable value of execution data.
 * Array data is stored as one searchable data per item, {@code index} being the position of the item
 * among the stored items (null items are skipped) and always 0 for any other data.
 */
public record SearchableData(@NotNull String tenantId, @NotNull String executionId, @NotNull String dataId, int index, String value) {
    public SearchableData(String tenantId, String executionId, String dataId, String value) {
        this(tenantId, executionId, dataId, 0, value);
    }
}
