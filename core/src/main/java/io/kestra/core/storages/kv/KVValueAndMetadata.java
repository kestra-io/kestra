package io.kestra.core.storages.kv;

import io.kestra.core.storages.StorageObject;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

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

    static KVValueAndMetadata from(StorageObject storageObject) throws IOException {
        try (InputStream is = storageObject.inputStream()) {
            String ionString = new String(is.readAllBytes());
            return new KVValueAndMetadata(new KVMetadata(storageObject.metadata()), ionString);
        }
    }
}
