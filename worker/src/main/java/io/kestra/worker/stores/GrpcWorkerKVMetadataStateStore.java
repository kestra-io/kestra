package io.kestra.worker.stores;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.controller.grpc.BooleanResponse;
import io.kestra.controller.grpc.KVMetadataRequest;
import io.kestra.controller.grpc.KVMetadataServiceGrpc;
import io.kestra.controller.grpc.NamespaceRequest;
import io.kestra.controller.grpc.OpaqueData;
import io.kestra.controller.messages.MessageFormat;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.runners.DefaultKVMetadataStateStore;
import io.kestra.core.runners.KVMetadataStateStore;
import io.kestra.core.worker.models.WorkerInfo;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Worker-side implementation of {@link KVMetadataStateStore} that communicates
 * with the controller via gRPC.
 * <p>
 * This implementation is used only by workers and replaces the default implementation
 * that uses repositories (which are not available to workers).
 * Only exposes worker-safe operations.
 */
@Singleton
@Slf4j
@Requires(property = "kestra.server-type", value = "WORKER")
@Replaces(DefaultKVMetadataStateStore.class)
public class GrpcWorkerKVMetadataStateStore implements KVMetadataStateStore {

    private static final MessageFormat MESSAGE_FORMAT = MessageFormats.JSON;
    private static final TypeReference<List<PersistedKvMetadata>> LIST_TYPE = new TypeReference<>() {
    };

    private final KVMetadataServiceGrpc.KVMetadataServiceBlockingStub kvMetadataStub;
    private final WorkerInfo workerInfo;

    @Inject
    public GrpcWorkerKVMetadataStateStore(final KVMetadataServiceGrpc.KVMetadataServiceBlockingStub kvMetadataStub,
        final WorkerInfo workerInfo) {
        this.kvMetadataStub = kvMetadataStub;
        this.workerInfo = workerInfo;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PersistedKvMetadata> findByName(String tenantId, String namespace, String name) {
        log.trace("Fetching KV metadata by name via gRPC: tenantId={}, namespace={}, name={}", tenantId, namespace, name);

        KVMetadataRequest request = KVMetadataRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(tenantId)
            .setNamespace(namespace)
            .setName(name)
            .build();

        OpaqueData response = kvMetadataStub.findByName(request);

        return Optional.ofNullable(MESSAGE_FORMAT.fromByteString(response.getMessage(), PersistedKvMetadata.class));
    }

    /** {@inheritDoc} */
    @Override
    public List<PersistedKvMetadata> find(String tenantId, @Nullable String namespace) {
        log.trace("Fetching KV metadata via gRPC find: tenantId={}, namespace={}", tenantId, namespace);

        NamespaceRequest request = NamespaceRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(tenantId)
            .setNamespace(namespace)
            .build();

        OpaqueData response = kvMetadataStub.find(request);

        return MESSAGE_FORMAT.fromByteString(response.getMessage(), LIST_TYPE);
    }

    /** {@inheritDoc} */
    @Override
    public boolean existsByNamespace(String tenantId, String namespace) {
        log.trace("Checking KV existence by namespace via gRPC: tenantId={}, namespace={}", tenantId, namespace);

        NamespaceRequest request = NamespaceRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(tenantId)
            .setNamespace(namespace)
            .build();

        BooleanResponse response = kvMetadataStub.existsByNamespace(request);

        return response.getValue();
    }

    /** {@inheritDoc} */
    @Override
    public PersistedKvMetadata save(PersistedKvMetadata item) {
        log.trace("Saving KV metadata via gRPC: namespace={}, name={}", item.getNamespace(), item.getName());

        OpaqueData request = OpaqueData.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setMessage(MESSAGE_FORMAT.toByteString(item))
            .build();

        OpaqueData response = kvMetadataStub.save(request);

        return MESSAGE_FORMAT.fromByteString(response.getMessage(), PersistedKvMetadata.class);
    }

    /** {@inheritDoc} */
    @Override
    public PersistedKvMetadata delete(PersistedKvMetadata item) throws IOException {
        log.trace("Deleting KV metadata via gRPC: namespace={}, name={}", item.getNamespace(), item.getName());

        KVMetadataRequest request = KVMetadataRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(item.getTenantId())
            .setNamespace(item.getNamespace())
            .setName(item.getName())
            .build();
        OpaqueData response = kvMetadataStub.deleteByName(request);

        return MESSAGE_FORMAT.fromByteString(response.getMessage(), PersistedKvMetadata.class);
    }
}
