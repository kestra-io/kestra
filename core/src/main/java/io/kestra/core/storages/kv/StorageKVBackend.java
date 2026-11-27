package io.kestra.core.storages.kv;

import java.io.ByteArrayInputStream;
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
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * The default {@link KVBackend}: the metadata goes to the {@link KVMetadataStateStore}, and the value to the
 * internal storage, under one file per revision.
 */
@Slf4j
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

            byte[] content = value.readAllBytes();
            int expectedRevision = this.kvMetadataStateStore.findByName(tenant, namespace, key)
                .map(PersistedKvMetadata::getRevision)
                .orElse(0) + 1;
            putValue(tenant, namespace, key, expectedRevision, metadata, content);

            // The metadata is what makes a revision visible, so it must be saved once its value is stored.
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
            if (saved.getRevision() != expectedRevision) {
                putValue(tenant, namespace, key, saved.getRevision(), metadata, content);
            }
        }
    }

    private void putValue(String tenant, String namespace, String key, int revision, @Nullable KVMetadata metadata, byte[] content) throws IOException {
        this.storage.put(
            tenant, namespace, KVStore.storageUri(key, namespace, revision), new StorageObject(
                new KVValueAndMetadata(metadata, null).metadataAsMap(),
                new ByteArrayInputStream(content)
            )
        );
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
            if (isExpired(metadata)) {
                this.delete(tenant, namespace, key);
                throw new ResourceExpiredException("The requested value has expired");
            }
        }

        return readExistingRevision(key, namespace, revision, candidate -> this.storage.get(tenant, namespace, KVStore.storageUri(key, namespace, candidate)));
    }

    @Override
    public Optional<KVMetadata> getMetadata(String tenant, String namespace, String key) throws IOException {
        Optional<PersistedKvMetadata> maybeMetadata = this.kvMetadataStateStore.findByName(tenant, namespace, key);
        if (maybeMetadata.map(PersistedKvMetadata::isDeleted).orElse(false)) {
            return Optional.empty();
        }

        int revision = maybeMetadata.map(PersistedKvMetadata::getRevision).orElse(1);
        return readExistingRevision(
            key, namespace, revision,
            candidate -> new KVMetadata(this.storage.getAttributes(tenant, namespace, KVStore.storageUri(key, namespace, candidate)).getMetadata())
        );
    }

    @Override
    public Optional<KVEntry> get(String tenant, String namespace, String key) throws IOException {
        Optional<PersistedKvMetadata> maybeMetadata = this.kvMetadataStateStore.findByName(tenant, namespace, key);
        if (maybeMetadata.isEmpty() || maybeMetadata.get().isDeleted() || isExpired(maybeMetadata.get())) {
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

    /**
     * Reads the given revision, falling back to the most recent lower revision that exists in storage
     * when the metadata and the storage have drifted.
     */
    private <T> Optional<T> readExistingRevision(String key, String namespace, int revision, RevisionReader<T> reader) throws IOException {
        for (int candidate = revision; candidate >= 1; candidate--) {
            try {
                T value = reader.read(candidate);
                if (candidate != revision) {
                    log.warn(
                        "KV '{}' revision {} is missing from storage in namespace '{}' (metadata/storage drift); serving the latest available revision {} instead.",
                        key, revision, namespace, candidate
                    );
                }
                return Optional.of(value);
            } catch (FileNotFoundException e) {
                // try the previous revision
            }
        }
        return Optional.empty();
    }

    @FunctionalInterface
    private interface RevisionReader<T> {
        T read(int revision) throws IOException;
    }

    private static boolean isExpired(PersistedKvMetadata metadata) {
        return Optional.ofNullable(metadata.getExpirationDate()).map(Instant.now()::isAfter).orElse(false);
    }
}
