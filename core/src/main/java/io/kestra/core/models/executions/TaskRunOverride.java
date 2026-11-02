package io.kestra.core.models.executions;

import jakarta.validation.constraints.NotNull;

/**
 * A value set on replay for one expression of a task run, kept next to the value it rendered.
 *
 * @param key the expression as printed back from its Pebble tree, for example {@code outputs.fetch.row.code}
 */
public record TaskRunOverride(@NotNull String key, Object value, Object originalValue) {
}
