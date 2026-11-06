package io.kestra.core.models.executions;

import java.time.Instant;
import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

/**
 * What a replay overrode for a task run. The values are kept with the task outputs, under {@code id},
 * so that reading them takes the same permission as reading outputs.
 *
 * @param keys the variable paths that were overridden
 * @param editedBy the user who requested the replay, when known
 */
public record TaskRunOverrides(@NotNull String id, @NotNull List<String> keys, @Nullable String editedBy, @NotNull Instant editedAt) {
}
