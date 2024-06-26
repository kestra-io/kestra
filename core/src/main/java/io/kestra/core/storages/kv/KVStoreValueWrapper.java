package io.kestra.core.storages.kv;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

public class KVStoreValueWrapper<T> {
    private final KVMetadata kvMetadata;
    private final T value;

    public KVStoreValueWrapper(KVMetadata kvMetadata, T value) {
        this.kvMetadata = kvMetadata;
        this.value = value;
    }

    public KVMetadata kvMetadata() {
        return kvMetadata;
    }

    public Map<String, String> metadataAsMap() {
        return Optional.ofNullable(kvMetadata).map(KVMetadata::toMap).orElse(null);
    }

    public T value() {
        return value;
    }

    static KVStoreValueWrapper<String> from(StorageObject storageObject) throws IOException {
        try (InputStream is = storageObject.inputStream()) {
            String ionString = new String(is.readAllBytes());
            return new KVStoreValueWrapper<>(new KVMetadata(storageObject.metadata()), ionString);
        }
    }

    static KVStoreValueWrapper<String> ionStringify(KVStoreValueWrapper<Object> kvStoreValueWrapper) throws JsonProcessingException {
        return new KVStoreValueWrapper<>(kvStoreValueWrapper.kvMetadata(), JacksonMapper.ofIon().writeValueAsString(kvStoreValueWrapper.value()));
    }
}
