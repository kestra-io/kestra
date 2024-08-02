package io.kestra.core.storages.kv;

import jakarta.annotation.Nullable;

/**
 * A K/V store entry value.
 *
 * @param value The value - can be null
 */
public record KVValue(@Nullable Object value) {
}
