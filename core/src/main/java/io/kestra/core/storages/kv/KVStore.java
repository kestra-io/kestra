package io.kestra.core.storages.kv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.storages.StorageContext;

/**
 * Service interface for accessing the files attached to a namespace Key-Value store.
 * <p>
 * This interface exposes only worker-safe operations. For server-only operations
 * (paginated listing, listing all entries, purging), see {@link io.kestra.core.kv.services.KVService}.
 */
public interface KVStore {

    /**
     * Gets the namespace attached to this K/V store.
     *
     * @return The namespace id.
     */
    String namespace();

    default URI storageUri(String key) {
        return this.storageUri(key, 1);
    }

    default URI storageUri(String key, int revision) {
        return KVStore.storageUri(key, namespace(), revision);
    }

    static URI storageUri(String key, String namespace, int revision) {
        String fileName = kvFileName(key, revision);
        return URI.create(StorageContext.KESTRA_PROTOCOL + StorageContext.kvPrefix(namespace) + (fileName.isEmpty() ? fileName : ("/" + fileName)));
    }

    static String kvFileName(String key, int revision) {
        return key == null ? "" : (key + ".ion") + (revision > 1 ? (".v" + revision) : "");
    }

    /**
     * Puts the given K/V entry.
     *
     * @param key The entry key - cannot be {@code null}.
     * @param value The entry value - cannot be {@code null}.
     * @throws IOException if an error occurred while executing the operation on the K/V store.
     */
    default void put(String key, KVValueAndMetadata value) throws IOException {
        put(key, value, true);
    }

    /**
     * Puts the given K/V entry.
     *
     * @param key The entry key - cannot be {@code null}.
     * @param value The entry value - cannot be {@code null}.
     * @param overwrite Specifies whether to overwrite the existing value.
     * @throws IOException if an error occurred while executing the operation on the K/V store.
     */
    void put(String key, KVValueAndMetadata value, boolean overwrite) throws IOException;

    /**
     * Finds the entry value for the given key.
     *
     * @param key The entry key - cannot be {@code null}.
     * @return The {@link KVValue}, otherwise {@link Optional#empty()} if no entry exist for the given key.
     * @throws IOException if an error occurred while executing the operation on the K/V store.
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
     * Lists all non-expired, non-deleted K/V store entries.
     *
     * @return The list of {@link KVEntry}.
     * @throws IOException if an error occurred while executing the operation on the K/V store.
     */
    List<KVEntry> list() throws IOException;

    /**
     * Finds the K/V store entry for the given key.
     *
     * @return The {@link KVEntry} or {@link Optional#empty()} if entry exists or the entry expired.
     * @throws IOException if an error occurred while executing the operation on the K/V store.
     */
    Optional<KVEntry> get(String key) throws IOException;

    /**
     * Checks whether a K/V entry exists for the given key.
     *
     * @param key The entry key.
     * @return {@code true} of an entry exists.
     * @throws IOException if an error occurred while executing the operation on the K/V store.
     */
    default boolean exists(String key) throws IOException {
        return get(key).isPresent();
    }

    /**
     * Finds a KV entry with associated metadata for a given key.
     *
     * @param key the KV entry key.
     * @return an optional of {@link KVValueAndMetadata}.
     *
     * @throws UncheckedIOException if an error occurred while executing the operation on the K/V store.
     */
    default Optional<KVValueAndMetadata> findMetadataAndValue(final String key) throws UncheckedIOException {
        try {
            return get(key).flatMap(entry ->
            {
                try {
                    return getValue(entry.key()).map(current -> new KVValueAndMetadata(new KVMetadata(entry.description(), entry.expirationDate()), current.value()));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (ResourceExpiredException e) {
                    return Optional.empty();
                }
            }
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
            throw new IllegalArgumentException(
                "Key must start with an alphanumeric character (uppercase or lowercase) and can contain alphanumeric characters (uppercase or lowercase), dots (.), underscores (_), and hyphens (-) only."
            );
        }
    }
}
