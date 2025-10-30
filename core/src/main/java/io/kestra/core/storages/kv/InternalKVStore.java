package io.kestra.core.storages.kv;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.StorageObject;
import io.kestra.core.utils.ListUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final KvMetadataRepositoryInterface kvMetadataRepository;

    /**
     * Creates a new {@link InternalKVStore} instance.
     *
     * @param namespace The namespace
     * @param tenant    The tenant.
     * @param storage   The storage.
     */
    public InternalKVStore(@Nullable final String tenant, @Nullable final String namespace, final StorageInterface storage, final KvMetadataRepositoryInterface kvMetadataRepository) {
        this.namespace = namespace;
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
        this.tenant = tenant;
        this.kvMetadataRepository = kvMetadataRepository;
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

        Object actualValue = value.value();
        byte[] serialized = actualValue instanceof Duration ? actualValue.toString().getBytes(StandardCharsets.UTF_8) : JacksonMapper.ofIon().writeValueAsBytes(actualValue);

        PersistedKvMetadata saved = this.kvMetadataRepository.save(PersistedKvMetadata.builder()
            .tenantId(this.tenant)
            .namespace(this.namespace)
            .name(key)
            .description(Optional.ofNullable(value.metadata()).map(KVMetadata::getDescription).orElse(null))
            .expirationDate(Optional.ofNullable(value.metadata()).map(KVMetadata::getExpirationDate).orElse(null))
            .deleted(false)
            .build());
        this.storage.put(this.tenant, this.namespace, this.storageUri(key, saved.getVersion()), new StorageObject(
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

        Optional<PersistedKvMetadata> maybeMetadata = this.kvMetadataRepository.findByName(this.tenant, this.namespace, key);

        int version = maybeMetadata.map(PersistedKvMetadata::getVersion).orElse(1);
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
            withMetadata = this.storage.getWithMetadata(this.tenant, this.namespace, this.storageUri(key, version));
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
        Optional<PersistedKvMetadata> maybeMetadata = this.kvMetadataRepository.findByName(this.tenant, this.namespace, key);
        if (maybeMetadata.map(PersistedKvMetadata::isDeleted).orElse(true)) {
            return false;
        }

        this.kvMetadataRepository.delete(maybeMetadata.get());
        return true;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<KVEntry> listAll() throws IOException {
        return this.list(Pageable.UNPAGED, Collections.emptyList(), true, true, FetchVersion.ALL);
    }

    @Override
    public ArrayListTotal<KVEntry> list(Pageable pageable, List<QueryFilter> filters, boolean allowDeleted, boolean allowExpired, FetchVersion fetchBehavior) throws IOException {
        if (this.namespace != null) {
            filters = Stream.concat(
                filters.stream(),
                Stream.of(QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(this.namespace).build())
            ).toList();
        }

        return this.kvMetadataRepository.find(
            pageable,
            this.tenant,
            filters,
            allowDeleted,
            allowExpired,
            fetchBehavior
        ).map(throwFunction(KVEntry::from));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<KVEntry> get(final String key) throws IOException {
        KVStore.validateKey(key);

        Optional<PersistedKvMetadata> maybeMetadata = this.kvMetadataRepository.findByName(this.tenant, this.namespace, key);
        if (maybeMetadata.isEmpty() || maybeMetadata.get().isDeleted()) {
            return Optional.empty();
        }

        return Optional.of(KVEntry.from(maybeMetadata.get()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer purge(List<KVEntry> kvEntries) throws IOException {
        Integer purgedMetadataCount = this.kvMetadataRepository.purge(kvEntries.stream().map(kv -> PersistedKvMetadata.from(tenant, kv)).toList());

        long actualDeletedEntries = kvEntries.stream()
            .map(KVEntry::key)
            .map(this::storageUri)
            .map(throwFunction(uri -> {
                boolean deleted = this.storage.delete(tenant, namespace, uri);
                URI metadataURI = URI.create(uri.getPath() + ".metadata");
                if (this.storage.exists(this.tenant, this.namespace, metadataURI)) {
                    this.storage.delete(this.tenant, this.namespace, metadataURI);
                }

                return deleted;
            })).filter(Boolean::booleanValue)
            .count();

        if (actualDeletedEntries != purgedMetadataCount) {
            log.warn("KV Metadata purge reported {} deleted entries, but {} values were actually deleted from storage", purgedMetadataCount, actualDeletedEntries);
        }

        return purgedMetadataCount;
    }
}
