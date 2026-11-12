package io.kestra.core.models.executions;

import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.With;

/**
 * An output of a previous task set on replay: {@code path} as the replayed task reads it, for example
 * {@code outputs.fetch.values.code}, and {@code keys} inside the outputs of task run {@code taskRunId}.
 */
public record OutputOverride(
    @NotNull String path,
    @NotNull String taskRunId,
    @NotNull List<String> keys,
    @Nullable Object value,
    @With @Nullable Object originalValue) {
}
