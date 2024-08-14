package io.kestra.core.storages.kv;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.StorageContext;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;


/**
 * Service interface for accessing the files attached to a namespace Key-Value store.
 */
public interface KVStore {

    /**
     * Gets the namespace attached to this K/V store.
     *
     * @return The namespace id.
     */
    String namespace();

    default URI storageUri(String key) {
        return this.storageUri(key, namespace());
    }

    default URI storageUri(String key, String namespace) {
        String filePath = key == null ? "" : ("/" + key + ".ion");
        return URI.create(StorageContext.KESTRA_PROTOCOL + StorageContext.kvPrefix(namespace) + filePath);
    }

    /**
     * Puts the given K/V entry.
     *
     * @param key       The entry key - cannot be {@code null}.
     * @param value     The entry value - cannot be {@code null}.
     * @throws IOException if an error occurred while executing the operation on the K/V store.
     */
    default void put(String key, KVValueAndMetadata value) throws IOException {
        put(key, value, true);
    }

    /**
     * Puts the given K/V entry.
     *
     * @param key       The entry key - cannot be {@code null}.
     * @param value     The entry value - cannot be {@code null}.
     * @param overwrite Specifies whether to overwrite the existing value.
     * @throws IOException if an error occurred while executing the operation on the K/V store.
     */
    void put(String key, KVValueAndMetadata value, boolean overwrite) throws IOException;

    /**
     * Finds the entry value for the given key.
     *
     * @param key The entry key - cannot be {@code null}.
     * @return The {@link KVValue}, otherwise {@link Optional#empty()} if no entry exist for the given key.
     * @throws IOException              if an error occurred while executing the operation on the K/V store.
     * @throws ResourceExpiredException if the entry expired.
     */
    Optional<KVValue> getValue(String key) throws IOException, ResourceExpiredException;

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


    Pattern KEY_VALIDATOR_PATTERN = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]*");

    /**
     * Static helper method for validating a K/V entry key.
     *
     * @param key the key to validate.
     */
    static void validateKey(final String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        if (!KEY_VALIDATOR_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Key must start with an alphanumeric character (uppercase or lowercase) and can contain alphanumeric characters (uppercase or lowercase), dots (.), underscores (_), and hyphens (-) only.");
        }
    }
}
