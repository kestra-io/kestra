package io.kestra.core.models.executions;

import jakarta.validation.constraints.NotNull;

/**
 * A value set by hand on replay for a variable read by one task run, kept next to the value it replaced.
 *
 * @param key the variable path as written in the task, for example {@code outputs.fetch.row.code}
 */
public record TaskRunOverride(@NotNull String key, Object value, Object originalValue) {
}
