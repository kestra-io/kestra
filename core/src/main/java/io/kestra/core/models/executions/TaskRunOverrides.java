package io.kestra.core.models.executions;

import java.time.Instant;
import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

/**
 * The outputs a replay overrode for this task run. Their values are kept in the task output store under {@code id},
 * so reading them takes the same permission as reading outputs.
 */
public record TaskRunOverrides(@NotNull String id, @NotNull List<String> paths, @Nullable String overriddenBy, @NotNull Instant overriddenAt) {
}
