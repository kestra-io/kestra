package io.kestra.core.searchable;

import jakarta.validation.constraints.NotNull;

/**
 * A searchable value of an execution output.
 * An array output is stored as one searchable output per item, {@code index} being the position of the item
 * among the stored items (null items are skipped) and always 0 for any other output.
 */
public record SearchableOutput(@NotNull String tenantId, @NotNull String executionId, @NotNull String outputId, int index, String value) {
    public SearchableOutput(String tenantId, String executionId, String outputId, String value) {
        this(tenantId, executionId, outputId, 0, value);
    }
}
