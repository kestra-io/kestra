package io.kestra.core.storages.kv;

import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Nullable;

/**
 * Class wrapping a value and metadata for K/V entry.
 *
 * @param metadata
 * @param value
 */
public record KVValueAndMetadata(@Nullable KVMetadata metadata, @Nullable Object value) {

    public Map<String, String> metadataAsMap() {
        return Optional.ofNullable(metadata).map(KVMetadata::toMap).orElse(null);
    }
}
