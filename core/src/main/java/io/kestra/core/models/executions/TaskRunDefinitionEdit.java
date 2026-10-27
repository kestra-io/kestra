package io.kestra.core.models.executions;

import java.time.Instant;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

/**
 * A replay ran this task run on a definition edited for that replay only. The edited YAML is kept with the task
 * outputs, under {@code id}, so that reading it is a separate, permission-checked call.
 *
 * @param editedBy the user who requested the replay, when known
 */
public record TaskRunDefinitionEdit(@NotNull String id, @Nullable String editedBy, @NotNull Instant editedAt) {
}
