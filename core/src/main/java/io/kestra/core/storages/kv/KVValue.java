package io.kestra.core.storages.kv;

import jakarta.annotation.Nullable;

import java.util.Optional;

/**
 * A K/V store entry value.
 *
 * @param value The value - can be null
 */
public record KVValue(@Nullable Object value) {

    @Override
    public String toString() {
        return "{value="+ value + ", type=" + Optional.ofNullable(value).map(val ->val.getClass().getSimpleName()).orElse("null") + "}";
    }
}
