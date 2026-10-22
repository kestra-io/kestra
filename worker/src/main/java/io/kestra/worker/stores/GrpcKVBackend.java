package io.kestra.worker.stores;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.controller.grpc.KVKeyRequest;
import io.kestra.controller.grpc.KVPutEntry;
import io.kestra.controller.grpc.KVPutRequest;
import io.kestra.controller.grpc.KVPutResponse;
import io.kestra.controller.grpc.KVStoreServiceGrpc;
import io.kestra.controller.grpc.NamespaceRequest;
import io.kestra.controller.grpc.RequestOrResponseHeader;
import io.kestra.controller.grpc.StreamChunk;
import io.kestra.controller.grpc.streaming.ChunkedStreamReader;
import io.kestra.controller.grpc.streaming.ChunkedStreamUpload;
import io.kestra.controller.messages.MessageFormat;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.storages.kv.KVBackend;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStoreException;
import io.kestra.core.storages.kv.StorageKVBackend;
import io.kestra.core.worker.models.WorkerInfo;

import io.grpc.StatusRuntimeException;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * A {@link KVBackend} reached through the controller, for a worker that does not reach the internal storage
 * holding the entries. Every operation of an entry goes through the controller, and values are streamed both
 * ways, so the worker never touches its own storage for the KV store.
 */
@Singleton
@Replaces(StorageKVBackend.class)
@Requires(condition = KVStoreFromControllerCondition.class)
public class GrpcKVBackend implements KVBackend {

    private static final MessageFormat MESSAGE_FORMAT = MessageFormats.JSON;
    private static final TypeReference<List<KVEntry>> ENTRY_LIST = new TypeReference<>() {
    };

    private final KVStoreServiceGrpc.KVStoreServiceStub kvStoreStub;
    private final KVStoreServiceGrpc.KVStoreServiceBlockingStub kvStoreBlockingStub;
    private final WorkerInfo workerInfo;

    @Inject
    public GrpcKVBackend(
        KVStoreServiceGrpc.KVStoreServiceStub kvStoreStub,
        KVStoreServiceGrpc.KVStoreServiceBlockingStub kvStoreBlockingStub,
        WorkerInfo workerInfo) {
        this.kvStoreStub = kvStoreStub;
        this.kvStoreBlockingStub = kvStoreBlockingStub;
        this.workerInfo = workerInfo;
    }

    @Override
    public void put(String tenant, String namespace, String key, @Nullable KVMetadata metadata, InputStream value, boolean overwrite) throws IOException {
        try (value) {
            ChunkedStreamUpload.<KVPutRequest, KVPutResponse>upload(
                kvStoreStub::put,
                putRequest(tenant).setEntry(entry(namespace, key, metadata, overwrite)).build(),
                value,
                chunk -> putRequest(tenant).setChunk(StreamChunk.newBuilder().setHeader(header()).setContent(chunk)).build()
            );
        } catch (StatusRuntimeException e) {
            throw translate(e, namespace, key);
        }
    }

    @Override
    public Optional<InputStream> getRawValue(String tenant, String namespace, String key) throws ResourceExpiredException {
        KVKeyRequest request = keyRequest(tenant, namespace, key);
        try {
            return Optional.of(ChunkedStreamReader.open(() -> kvStoreBlockingStub.getRawValue(request)));
        } catch (StatusRuntimeException e) {
            switch (e.getStatus().getCode()) {
                case NOT_FOUND -> {
                    return Optional.empty();
                }
                case FAILED_PRECONDITION -> throw new ResourceExpiredException(e.getStatus().getDescription(), e);
                default -> throw translate(e, namespace, key);
            }
        }
    }

    @Override
    public Optional<KVEntry> get(String tenant, String namespace, String key) {
        KVKeyRequest request = keyRequest(tenant, namespace, key);
        return Optional.ofNullable(call(() -> MESSAGE_FORMAT.fromByteString(kvStoreBlockingStub.get(request).getMessage(), KVEntry.class), namespace, key));
    }

    @Override
    public List<KVEntry> list(String tenant, String namespace) {
        NamespaceRequest request = NamespaceRequest.newBuilder()
            .setHeader(header())
            .setTenantId(tenant)
            .setNamespace(namespace)
            .build();
        return call(() -> MESSAGE_FORMAT.fromByteString(kvStoreBlockingStub.list(request).getMessage(), ENTRY_LIST), namespace, null);
    }

    @Override
    public boolean delete(String tenant, String namespace, String key) {
        KVKeyRequest request = keyRequest(tenant, namespace, key);
        return call(() -> kvStoreBlockingStub.delete(request).getValue(), namespace, key);
    }

    private static <T> T call(Supplier<T> call, String namespace, @Nullable String key) {
        try {
            return call.get();
        } catch (StatusRuntimeException e) {
            throw translate(e, namespace, key);
        }
    }

    private static RuntimeException translate(StatusRuntimeException e, String namespace, @Nullable String key) {
        String description = e.getStatus().getDescription();
        return switch (e.getStatus().getCode()) {
            case ALREADY_EXISTS, PERMISSION_DENIED -> new KVStoreException(description, e);
            case INVALID_ARGUMENT -> new IllegalArgumentException(description, e);
            case UNIMPLEMENTED -> new KestraRuntimeException(
                ("Cannot access the KV store of namespace '%s' through the controller, which serves no KV store entries. "
                    + "Upgrade the controller, or set '%s' to STORAGE so this worker reads the KV store from internal storage.")
                    .formatted(namespace, KVStoreFromControllerCondition.CONFIG_KEY),
                e
            );
            default -> e;
        };
    }

    private KVPutRequest.Builder putRequest(String tenant) {
        return KVPutRequest.newBuilder()
            .setHeader(header())
            .setTenantId(tenant);
    }

    private static KVPutEntry entry(String namespace, String key, @Nullable KVMetadata metadata, boolean overwrite) {
        KVPutEntry.Builder entry = KVPutEntry.newBuilder()
            .setNamespace(namespace)
            .setKey(key)
            .setOverwrite(overwrite);
        if (metadata != null && metadata.getDescription() != null) {
            entry.setDescription(metadata.getDescription());
        }
        if (metadata != null && metadata.getExpirationDate() != null) {
            entry.setExpirationDate(metadata.getExpirationDate().toString());
        }
        return entry.build();
    }

    private KVKeyRequest keyRequest(String tenant, String namespace, String key) {
        return KVKeyRequest.newBuilder()
            .setHeader(header())
            .setTenantId(tenant)
            .setNamespace(namespace)
            .setKey(key)
            .build();
    }

    private RequestOrResponseHeader header() {
        return RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId());
    }
}
