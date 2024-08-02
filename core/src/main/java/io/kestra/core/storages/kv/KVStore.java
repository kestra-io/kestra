package io.kestra.core.storages.kv;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageContext;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * Service interface for accessing the files attached to a namespace Key-Value store.
 */
public interface KVStore {
    Pattern durationPattern = Pattern.compile("^P(?=[^T]|T.)(?:\\d*D)?(?:T(?=.)(?:\\d*H)?(?:\\d*M)?(?:\\d*S)?)?$");

    default void validateKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        if (!key.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
            throw new IllegalArgumentException("Key must start with an alphanumeric character (uppercase or lowercase) and can contain alphanumeric characters (uppercase or lowercase), dots (.), underscores (_), and hyphens (-) only.");
        }
    }

    String namespace();

    default URI storageUri(String key) {
        return this.storageUri(key, namespace());
    }

    default URI storageUri(String key, String namespace) {
        String filePath = key == null ? "" : ("/" + key + ".ion");
        return URI.create(StorageContext.KESTRA_PROTOCOL + StorageContext.kvPrefix(namespace) + filePath);
    }

    default void put(String key, KVStoreValueWrapper<Object> kvStoreValueWrapper) throws IOException {
        put(key, kvStoreValueWrapper, true);
    }
    default void put(String key, KVStoreValueWrapper<Object> kvStoreValueWrapper, boolean overwrite) throws IOException {
        Objects.requireNonNull(key, "key cannot be null");

        if (!overwrite && exists(key)) {
            throw new KVStoreException(String.format(
                "Cannot set value for key '%s'. Key already exists and `overwrite` is set to `false`.", key));
        }

        Object value = kvStoreValueWrapper.value();
        String ionValue;
        if (value instanceof Duration duration) {
            ionValue = duration.toString();
        } else {
            ionValue = JacksonMapper.ofIon().writeValueAsString(value);
        }

        this.putRaw(key, new KVStoreValueWrapper<>(kvStoreValueWrapper.kvMetadata(), ionValue));
    }

    void putRaw(String key, KVStoreValueWrapper<String> kvStoreValueWrapper) throws IOException;

    default Optional<KVValue> getValue(String key) throws IOException, ResourceExpiredException {
        return this.getRawValue(key).map(throwFunction(raw -> {
            Object value = JacksonMapper.ofIon().readValue(raw, Object.class);
            if (value instanceof String valueStr && durationPattern.matcher(valueStr).matches()) {
                return new KVValue(Duration.parse(valueStr));
            }

            return new KVValue(value);
        }));
    }

    Optional<String> getRawValue(String key) throws IOException, ResourceExpiredException;

    /**
     * Deletes the K/V store entry for the given key.
     *
     * @param key The entry key.
     * @throws IOException if an error occurred while executing the operation on the K/V store.
     */
    boolean delete(String key) throws IOException;

    /**
     * Lists all the K/V store entries.
     *
     * @return  The list of {@link KVEntry}.
     * @throws IOException if an error occurred while executing the operation on the K/V store.
     */
    List<KVEntry> list() throws IOException;

    /**
     * Finds the K/V store entry for the given key.
     *
     * @return  The {@link KVEntry} or {@link Optional#empty()} if entry exists or the entry expired.
     * @throws IOException if an error occurred while executing the operation on the K/V store.
     */
    Optional<KVEntry> get(String key) throws IOException;

    /**
     * Checks whether a K/V entry exists for teh given key.
     *
     * @param key The entry key.
     * @return {@code true} of an entry exists.
     * @throws IOException if an error occurred while executing the operation on the K/V store.
     */
    default boolean exists(String key) throws IOException {
        return list().stream().anyMatch(kvEntry -> kvEntry.key().equals(key));
    }
}
