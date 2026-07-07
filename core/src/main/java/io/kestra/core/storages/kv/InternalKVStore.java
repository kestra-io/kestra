package io.kestra.core.storages.kv;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.runners.KVMetadataStateStore;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.StorageObject;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * The default {@link KVStore} implementation.
 *
 */
@Slf4j
public class InternalKVStore implements KVStore {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^P(?=[^T]|T.)(?:\\d*D)?(?:T(?=.)(?:\\d*H)?(?:\\d*M)?(?:\\d*S)?)?$");

    private final String namespace;
    private final String tenant;
    private final StorageInterface storage;
    private final KVMetadataStateStore kvMetadataStateStore;

    /**
     * Creates a new {@link InternalKVStore} instance.
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
        this.namespace = namespace;
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
        this.tenant = tenant;
        this.kvMetadataStateStore = kvMetadataStateStore;
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

        if (!overwrite && exists(key)) {
            throw new KVStoreException(
                String.format(
                    "Cannot set value for key '%s'. Key already exists and `overwrite` is set to `false`.", key
                )
            );
        }

        Object actualValue = value.value();
        byte[] serialized = actualValue instanceof Duration ? actualValue.toString().getBytes(StandardCharsets.UTF_8) : JacksonMapper.ofIon().writeValueAsBytes(actualValue);

        PersistedKvMetadata saved = this.kvMetadataStateStore.save(
            PersistedKvMetadata.builder()
                .tenantId(this.tenant)
                .namespace(this.namespace)
                .name(key)
                .description(Optional.ofNullable(value.metadata()).map(KVMetadata::getDescription).orElse(null))
                .expirationDate(Optional.ofNullable(value.metadata()).map(KVMetadata::getExpirationDate).orElse(null))
                .deleted(false)
                .build()
        );
        this.storage.put(
            this.tenant, this.namespace, this.storageUri(key, saved.getRevision()), new StorageObject(
                value.metadataAsMap(),
                new ByteArrayInputStream(serialized)
            )
        );
    }

    /**
     * Puts a KV entry using an already-serialized (raw) value, bypassing ION serialization.
     * This is intended for backup/restore where the value is already in its stored ION format.
     *
     * @param key      The key.
     * @param metadata The metadata (nullable).
     * @param rawValue The raw ION-serialized value bytes.
     */
    public void putRaw(String key, @Nullable KVMetadata metadata, byte[] rawValue) throws IOException {
        KVStore.validateKey(key);

        PersistedKvMetadata saved = this.kvMetadataStateStore.save(
            PersistedKvMetadata.builder()
                .tenantId(this.tenant)
                .namespace(this.namespace)
                .name(key)
                .description(Optional.ofNullable(metadata).map(KVMetadata::getDescription).orElse(null))
                .expirationDate(Optional.ofNullable(metadata).map(KVMetadata::getExpirationDate).orElse(null))
                .deleted(false)
                .build()
        );
        KVValueAndMetadata wrapper = new KVValueAndMetadata(metadata, null);
        this.storage.put(
            this.tenant, this.namespace, this.storageUri(key, saved.getRevision()), new StorageObject(
                wrapper.metadataAsMap(),
                new ByteArrayInputStream(rawValue)
            )
        );
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

        Optional<PersistedKvMetadata> maybeMetadata = this.kvMetadataStateStore.findByName(this.tenant, this.namespace, key);

        int revision = maybeMetadata.map(PersistedKvMetadata::getRevision).orElse(1);
        if (maybeMetadata.isPresent()) {
            PersistedKvMetadata metadata = maybeMetadata.get();
            if (metadata.isDeleted()) {
                return Optional.empty();
            }

            if (Optional.ofNullable(metadata.getExpirationDate()).map(Instant.now()::isAfter).orElse(false)) {
                this.delete(key);
                throw new ResourceExpiredException("The requested value has expired");
            }
        }

        StorageObject withMetadata;
        try {
            withMetadata = this.storage.getWithMetadata(this.tenant, this.namespace, this.storageUri(key, revision));
        } catch (FileNotFoundException e) {
            return Optional.empty();
        }
        KVValueAndMetadata kvStoreValueWrapper = KVValueAndMetadata.from(withMetadata);

        return Optional.of((String) (kvStoreValueWrapper.value()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(String key) throws IOException {
        KVStore.validateKey(key);
        Optional<PersistedKvMetadata> maybeMetadata = this.kvMetadataStateStore.findByName(this.tenant, this.namespace, key);
        if (maybeMetadata.map(PersistedKvMetadata::isDeleted).orElse(true)) {
            return false;
        }

        this.kvMetadataStateStore.delete(maybeMetadata.get());
        return true;

    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation uses the {@link KVMetadataStateStore} and is safe to call from workers.
     */
    @Override
    public List<KVEntry> list() throws IOException {
        return this.kvMetadataStateStore.find(this.tenant, this.namespace)
            .stream()
            .map(throwFunction(KVEntry::from))
            .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<KVEntry> get(final String key) throws IOException {
        KVStore.validateKey(key);

        Optional<PersistedKvMetadata> maybeMetadata = this.kvMetadataStateStore.findByName(this.tenant, this.namespace, key);
        if (maybeMetadata.isEmpty() || maybeMetadata.get().isDeleted()) {
            return Optional.empty();
        }

        return Optional.of(KVEntry.from(maybeMetadata.get()));
    }
}
