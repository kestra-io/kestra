package io.kestra.core.storages.kv;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.Storage;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.StorageObject;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * The default {@link KVStore} implementation.
 * This class acts as a facade to the {@link StorageInterface} for manipulating Key-Value store.
 *
 * @see Storage#namespaceKv()
 * @see Storage#namespaceKv(String)
 */
public class InternalKVStore implements KVStore {

    private static final Logger log = LoggerFactory.getLogger(InternalKVStore.class);

    private final String namespace;
    private final String tenant;
    private final StorageInterface storage;
    private final Logger logger;

    /**
     * Creates a new {@link InternalKVStore} instance.
     *
     * @param namespace The namespace
     * @param storage   The storage.
     */
    public InternalKVStore(final String tenant, final String namespace, final StorageInterface storage) {
        this(log, tenant, namespace, storage);
    }

    /**
     * Creates a new {@link InternalKVStore} instance.
     *
     * @param logger    The logger to be used by this class.
     * @param namespace The namespace
     * @param tenant    The tenant.
     * @param storage   The storage.
     */
    public InternalKVStore(final Logger logger, @Nullable final String tenant, final String namespace, final StorageInterface storage) {
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
        this.tenant = tenant;
    }

    @Override
    public String namespace() {
        return this.namespace;
    }

    @Override
    public void putRaw(String key, KVStoreValueWrapper<String> kvStoreValueWrapper) throws IOException {
        this.validateKey(key);

        this.storage.put(this.tenant, this.storageUri(key), new StorageObject(
            kvStoreValueWrapper.metadataAsMap(),
            new ByteArrayInputStream(kvStoreValueWrapper.value().getBytes())
        ));
    }

    @Override
    public Optional<String> getRaw(String key) throws IOException, ResourceExpiredException {
        this.validateKey(key);

        StorageObject withMetadata;
        try {
            withMetadata = this.storage.getWithMetadata(this.tenant, this.storageUri(key));
        } catch (FileNotFoundException e) {
            return Optional.empty();
        }
        KVStoreValueWrapper<String> kvStoreValueWrapper = KVStoreValueWrapper.from(withMetadata);

        Instant expirationDate = kvStoreValueWrapper.kvMetadata().getExpirationDate();
        if (expirationDate != null && Instant.now().isAfter(expirationDate)) {
            this.delete(key);

            throw new ResourceExpiredException("The requested value has expired");
        }
        return Optional.of(kvStoreValueWrapper.value());
    }

    @Override
    public boolean delete(String key) throws IOException {
        this.validateKey(key);

        return this.storage.delete(this.tenant, this.storageUri(key));
    }

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
}
