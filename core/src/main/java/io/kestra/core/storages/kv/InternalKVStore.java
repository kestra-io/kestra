package io.kestra.core.storages.kv;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.StorageObject;
import jakarta.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * The default {@link KVStore} implementation.
 *
 */
public class InternalKVStore implements KVStore {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^P(?=[^T]|T.)(?:\\d*D)?(?:T(?=.)(?:\\d*H)?(?:\\d*M)?(?:\\d*S)?)?$");

    private final String namespace;
    private final String tenant;
    private final StorageInterface storage;

    /**
     * Creates a new {@link InternalKVStore} instance.
     *
     * @param namespace The namespace
     * @param tenant    The tenant.
     * @param storage   The storage.
     */
    public InternalKVStore(@Nullable final String tenant, final String namespace, final StorageInterface storage) {
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
        this.tenant = tenant;
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
            throw new KVStoreException(String.format(
                "Cannot set value for key '%s'. Key already exists and `overwrite` is set to `false`.", key));
        }

        byte[] serialized = JacksonMapper.ofIon().writeValueAsBytes(value.value());

        this.storage.put(this.tenant, this.storageUri(key), new StorageObject(
            value.metadataAsMap(),
            new ByteArrayInputStream(serialized)
        ));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<KVValue> getValue(String key) throws IOException, ResourceExpiredException {
        return this.getRawValue(key).map(throwFunction(raw -> {
            Object value = JacksonMapper.ofIon().readValue(raw, Object.class);
            if (value instanceof String valueStr && DURATION_PATTERN.matcher(valueStr).matches()) {
                return new KVValue(Duration.parse(valueStr));
            }
            return new KVValue(value);
        }));
    }

    public Optional<String> getRawValue(String key) throws IOException, ResourceExpiredException {
        KVStore.validateKey(key);

        StorageObject withMetadata;
        try {
            withMetadata = this.storage.getWithMetadata(this.tenant, this.storageUri(key));
        } catch (FileNotFoundException e) {
            return Optional.empty();
        }
        KVValueAndMetadata kvStoreValueWrapper = KVValueAndMetadata.from(withMetadata);

        Instant expirationDate = kvStoreValueWrapper.metadata().getExpirationDate();
        if (expirationDate != null && Instant.now().isAfter(expirationDate)) {
            this.delete(key);
            throw new ResourceExpiredException("The requested value has expired");
        }
        return Optional.of((String)(kvStoreValueWrapper.value()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(String key) throws IOException {
        KVStore.validateKey(key);
        return this.storage.delete(this.tenant, this.storageUri(key));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<KVEntry> list() throws IOException {
        List<FileAttributes> list;
        try {
            list = this.storage.list(this.tenant, this.storageUri(null));
        } catch (FileNotFoundException e) {
            return Collections.emptyList();
        }
        return list.stream()
            .map(throwFunction(KVEntry::from))
            .filter(kvEntry -> Optional.ofNullable(kvEntry.expirationDate()).map(expirationDate -> Instant.now().isBefore(expirationDate)).orElse(true))
            .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<KVEntry> get(final String key) throws IOException {
        KVStore.validateKey(key);

        try {
            KVEntry entry = KVEntry.from(this.storage.getAttributes(this.tenant, this.storageUri(key)));
            if (entry.expirationDate() != null && Instant.now().isAfter(entry.expirationDate())) {
                this.delete(key);
                return Optional.empty();
            }
            return Optional.of(entry);
        } catch (FileNotFoundException e) {
            return Optional.empty();
        }
    }
}
