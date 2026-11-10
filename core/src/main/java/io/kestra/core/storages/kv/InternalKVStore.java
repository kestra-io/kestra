package io.kestra.core.storages.kv;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.runners.KVMetadataStateStore;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;

import jakarta.annotation.Nullable;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * The default {@link KVStore} implementation: it validates the keys and serializes the values, and leaves
 * where the entries live to a {@link KVBackend}.
 */
public class InternalKVStore implements KVStore {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^P(?=[^T]|T.)(?:\\d*D)?(?:T(?=.)(?:\\d*H)?(?:\\d*M)?(?:\\d*S)?)?$");

    private final String namespace;
    private final String tenant;
    private final KVBackend backend;

    /**
     * Creates a new {@link InternalKVStore} instance.
     *
     * @param tenant The tenant.
     * @param namespace The namespace.
     * @param backend Where the entries live.
     */
    public InternalKVStore(@Nullable final String tenant, @Nullable final String namespace, final KVBackend backend) {
        this.namespace = namespace;
        this.tenant = tenant;
        this.backend = Objects.requireNonNull(backend, "backend cannot be null");
    }

    /**
     * Creates a new {@link InternalKVStore} instance backed by the given internal storage.
     *
     * @param tenant The tenant.
     * @param namespace The namespace.
     * @param storage The storage.
     * @param kvMetadataStateStore The KV metadata state store (used for worker-safe operations).
     */
    public InternalKVStore(@Nullable final String tenant,
        @Nullable final String namespace,
        final StorageInterface storage,
        final KVMetadataStateStore kvMetadataStateStore) {
        this(tenant, namespace, new StorageKVBackend(Objects.requireNonNull(storage, "storage cannot be null"), kvMetadataStateStore));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String namespace() {
        return this.namespace;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(String key, KVValueAndMetadata value, boolean overwrite) throws IOException {
        KVStore.validateKey(key);

        Object actualValue = value.value();
        byte[] serialized = actualValue instanceof Duration ? actualValue.toString().getBytes(StandardCharsets.UTF_8) : JacksonMapper.ofIon().writeValueAsBytes(actualValue);

        this.backend.put(this.tenant, this.namespace, key, value.metadata(), new ByteArrayInputStream(serialized), overwrite);
    }

    /**
     * Puts a KV entry using an already-serialized (raw) value, bypassing ION serialization.
     * This is intended for backup/restore where the value is already in its stored ION format.
     *
     * @param key The key.
     * @param metadata The metadata (nullable).
     * @param rawValue The raw ION-serialized value bytes.
     */
    public void putRaw(String key, @Nullable KVMetadata metadata, byte[] rawValue) throws IOException {
        KVStore.validateKey(key);

        this.backend.put(this.tenant, this.namespace, key, metadata, new ByteArrayInputStream(rawValue), true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<KVValue> getValue(String key) throws IOException, ResourceExpiredException {
        return this.getRawValue(key).map(throwFunction(raw ->
        {
            Object value = JacksonMapper.ofIon().readValue(raw, Object.class);
            if (value instanceof String valueStr && DURATION_PATTERN.matcher(valueStr).matches()) {
                return new KVValue(Duration.parse(valueStr));
            }
            return new KVValue(value);
        }));
    }

    public Optional<String> getRawValue(String key) throws IOException, ResourceExpiredException {
        KVStore.validateKey(key);

        Optional<InputStream> raw = this.backend.getRawValue(this.tenant, this.namespace, key);
        if (raw.isEmpty()) {
            return Optional.empty();
        }

        try (InputStream value = raw.get()) {
            return Optional.of(new String(value.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(String key) throws IOException {
        KVStore.validateKey(key);

        return this.backend.delete(this.tenant, this.namespace, key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<KVEntry> list() throws IOException {
        return this.backend.list(this.tenant, this.namespace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<KVEntry> get(final String key) throws IOException {
        KVStore.validateKey(key);

        return this.backend.get(this.tenant, this.namespace, key);
    }
}
