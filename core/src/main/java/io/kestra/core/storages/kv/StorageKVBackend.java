package io.kestra.core.storages.kv;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.runners.KVMetadataStateStore;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.StorageObject;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * The default {@link KVBackend}: the metadata goes to the {@link KVMetadataStateStore}, and the value to the
 * internal storage, under one file per revision.
 */
@Singleton
public class StorageKVBackend implements KVBackend {

    private final StorageInterface storage;
    private final KVMetadataStateStore kvMetadataStateStore;

    @Inject
    public StorageKVBackend(StorageInterface storage, KVMetadataStateStore kvMetadataStateStore) {
        this.storage = storage;
        this.kvMetadataStateStore = kvMetadataStateStore;
    }

    @Override
    public void put(String tenant, String namespace, String key, @Nullable KVMetadata metadata, InputStream value, boolean overwrite) throws IOException {
        try (value) {
            if (!overwrite && get(tenant, namespace, key).isPresent()) {
                throw new KVStoreException(
                    String.format(
                        "Cannot set value for key '%s'. Key already exists and `overwrite` is set to `false`.", key
                    )
                );
            }

            PersistedKvMetadata saved = this.kvMetadataStateStore.save(
                PersistedKvMetadata.builder()
                    .tenantId(tenant)
                    .namespace(namespace)
                    .name(key)
                    .description(Optional.ofNullable(metadata).map(KVMetadata::getDescription).orElse(null))
                    .expirationDate(Optional.ofNullable(metadata).map(KVMetadata::getExpirationDate).orElse(null))
                    .deleted(false)
                    .build()
            );
            this.storage.put(
                tenant, namespace, KVStore.storageUri(key, namespace, saved.getRevision()), new StorageObject(
                    new KVValueAndMetadata(metadata, null).metadataAsMap(),
                    value
                )
            );
        }
    }

    @Override
    public Optional<InputStream> getRawValue(String tenant, String namespace, String key) throws IOException, ResourceExpiredException {
        Optional<PersistedKvMetadata> maybeMetadata = this.kvMetadataStateStore.findByName(tenant, namespace, key);

        int revision = maybeMetadata.map(PersistedKvMetadata::getRevision).orElse(1);
        if (maybeMetadata.isPresent()) {
            PersistedKvMetadata metadata = maybeMetadata.get();
            if (metadata.isDeleted()) {
                return Optional.empty();
            }

            if (Optional.ofNullable(metadata.getExpirationDate()).map(Instant.now()::isAfter).orElse(false)) {
                this.delete(tenant, namespace, key);
                throw new ResourceExpiredException("The requested value has expired");
            }
        }

        try {
            return Optional.of(this.storage.get(tenant, namespace, KVStore.storageUri(key, namespace, revision)));
        } catch (FileNotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<KVEntry> get(String tenant, String namespace, String key) throws IOException {
        Optional<PersistedKvMetadata> maybeMetadata = this.kvMetadataStateStore.findByName(tenant, namespace, key);
        if (maybeMetadata.isEmpty() || maybeMetadata.get().isDeleted()) {
            return Optional.empty();
        }

        return Optional.of(KVEntry.from(maybeMetadata.get()));
    }

    @Override
    public List<KVEntry> list(String tenant, String namespace) throws IOException {
        return this.kvMetadataStateStore.find(tenant, namespace)
            .stream()
            .map(throwFunction(KVEntry::from))
            .toList();
    }

    @Override
    public boolean delete(String tenant, String namespace, String key) throws IOException {
        Optional<PersistedKvMetadata> maybeMetadata = this.kvMetadataStateStore.findByName(tenant, namespace, key);
        if (maybeMetadata.map(PersistedKvMetadata::isDeleted).orElse(true)) {
            return false;
        }

        this.kvMetadataStateStore.delete(maybeMetadata.get());
        return true;
    }
}
