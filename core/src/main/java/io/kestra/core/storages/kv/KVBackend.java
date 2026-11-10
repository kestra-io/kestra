package io.kestra.core.storages.kv;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import io.kestra.core.exceptions.ResourceExpiredException;

import jakarta.annotation.Nullable;

/**
 * Persists the entries of the namespace K/V stores, keeping the metadata of an entry and its serialized value
 * together. An implementation decides where the entries live; {@link InternalKVStore} validates the keys and
 * serializes the values on top of it. Values are passed as streams, so an implementation relaying them is not
 * bound to hold a whole value in memory.
 */
public interface KVBackend {

    /**
     * Puts an entry whose value is already serialized.
     *
     * @param value the serialized value, read to its end and closed by the implementation.
     * @throws KVStoreException if the key exists and {@code overwrite} is {@code false}.
     */
    void put(String tenant, String namespace, String key, @Nullable KVMetadata metadata, InputStream value, boolean overwrite) throws IOException;

    /**
     * Gets the serialized value of an entry. An expired entry is deleted.
     *
     * @return the value, to be closed by the caller, or {@link Optional#empty()} if the entry does not exist or is deleted.
     * @throws ResourceExpiredException if the entry expired.
     */
    Optional<InputStream> getRawValue(String tenant, String namespace, String key) throws IOException, ResourceExpiredException;

    /**
     * @return the entry, or {@link Optional#empty()} if it does not exist or is deleted.
     */
    Optional<KVEntry> get(String tenant, String namespace, String key) throws IOException;

    /**
     * @return the non-expired, non-deleted entries of the namespace.
     */
    List<KVEntry> list(String tenant, String namespace) throws IOException;

    /**
     * @return {@code true} if an entry was deleted, {@code false} if there was none.
     */
    boolean delete(String tenant, String namespace, String key) throws IOException;
}
