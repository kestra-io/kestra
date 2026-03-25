package io.kestra.worker.stores;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.controller.grpc.*;
import io.kestra.controller.messages.MessageFormat;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.namespace.DefaultNamespaceFileMetadataStateStore;
import io.kestra.core.namespace.NamespaceFileMetadataStateStore;
import io.kestra.core.worker.models.WorkerInfo;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Worker-side implementation of {@link NamespaceFileMetadataStateStore} that communicates
 * with the controller via gRPC.
 * <p>
 * This implementation is used only by workers and replaces the default implementation
 * that uses repositories (which are not available to workers).
 * Only exposes worker-safe operations.
 */
@Singleton
@Slf4j
@Requires(property = "kestra.server-type", value = "WORKER")
@Replaces(DefaultNamespaceFileMetadataStateStore.class)
public class GrpcWorkerNamespaceFileMetadataStateStore implements NamespaceFileMetadataStateStore {

    private static final MessageFormat MESSAGE_FORMAT = MessageFormats.JSON;
    private static final TypeReference<List<NamespaceFileMetadata>> LIST_TYPE = new TypeReference<>() {
    };

    private final NamespaceFileMetadataServiceGrpc.NamespaceFileMetadataServiceBlockingStub stub;
    private final WorkerInfo workerInfo;

    @Inject
    public GrpcWorkerNamespaceFileMetadataStateStore(
        final NamespaceFileMetadataServiceGrpc.NamespaceFileMetadataServiceBlockingStub stub,
        final WorkerInfo workerInfo) {
        this.stub = stub;
        this.workerInfo = workerInfo;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<NamespaceFileMetadata> findByPath(String tenantId, String namespace, String path,
        @Nullable Integer version, boolean allowDeleted) throws IOException {
        log.trace(
            "Fetching namespace file metadata by path via gRPC: tenantId={}, namespace={}, path={}, version={}, allowDeleted={}",
            tenantId, namespace, path, version, allowDeleted
        );

        NamespaceFileMetadataPathRequest.Builder requestBuilder = NamespaceFileMetadataPathRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(tenantId)
            .setNamespace(namespace)
            .setPath(path)
            .setAllowDeleted(allowDeleted);

        if (version != null) {
            requestBuilder.setVersion(version);
        }

        OpaqueData response = stub.findByPath(requestBuilder.build());
        return Optional.ofNullable(MESSAGE_FORMAT.fromByteString(response.getMessage(), NamespaceFileMetadata.class));
    }

    /** {@inheritDoc} */
    @Override
    public List<NamespaceFileMetadata> findChildren(String tenantId, String namespace, @Nullable String parentPath, boolean recursive) {
        log.trace(
            "Fetching namespace file metadata children via gRPC: tenantId={}, namespace={}, parentPath={}, recursive={}",
            tenantId, namespace, parentPath, recursive
        );

        NamespaceFileMetadataChildrenRequest.Builder requestBuilder = NamespaceFileMetadataChildrenRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(tenantId)
            .setNamespace(namespace)
            .setRecursive(recursive);

        if (parentPath != null) {
            requestBuilder.setParentPath(parentPath);
        }

        OpaqueData response = stub.findChildren(requestBuilder.build());
        return MESSAGE_FORMAT.fromByteString(response.getMessage(), LIST_TYPE);
    }

    /** {@inheritDoc} */
    @Override
    public List<NamespaceFileMetadata> findAll(String tenantId, String namespace, @Nullable String containing) {
        log.trace("Fetching all namespace file metadata via gRPC: tenantId={}, namespace={}, containing={}", tenantId, namespace, containing);

        NamespaceFileMetadataFindAllRequest.Builder requestBuilder = NamespaceFileMetadataFindAllRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(tenantId)
            .setNamespace(namespace);

        if (containing != null) {
            requestBuilder.setContaining(containing);
        }

        OpaqueData response = stub.findAll(requestBuilder.build());
        return MESSAGE_FORMAT.fromByteString(response.getMessage(), LIST_TYPE);
    }

    /** {@inheritDoc} */
    @Override
    public List<NamespaceFileMetadata> findByPaths(String tenantId, String namespace, List<String> paths, boolean allowDeleted) {
        log.trace(
            "Fetching namespace file metadata by paths via gRPC: tenantId={}, namespace={}, paths={}, allowDeleted={}",
            tenantId, namespace, paths, allowDeleted
        );

        NamespaceFileMetadataPathsRequest request = NamespaceFileMetadataPathsRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(tenantId)
            .setNamespace(namespace)
            .addAllPaths(paths)
            .setAllowDeleted(allowDeleted)
            .build();

        OpaqueData response = stub.findByPaths(request);
        return MESSAGE_FORMAT.fromByteString(response.getMessage(), LIST_TYPE);
    }

    /** {@inheritDoc} */
    @Override
    public List<NamespaceFileMetadata> findAllVersionsByPaths(String tenantId, String namespace, List<String> paths) {
        log.trace(
            "Fetching all versions of namespace file metadata by paths via gRPC: tenantId={}, namespace={}, paths={}",
            tenantId, namespace, paths
        );

        NamespaceFileMetadataPathsRequest request = NamespaceFileMetadataPathsRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(tenantId)
            .setNamespace(namespace)
            .addAllPaths(paths)
            .build();

        OpaqueData response = stub.findAllVersionsByPaths(request);
        return MESSAGE_FORMAT.fromByteString(response.getMessage(), LIST_TYPE);
    }

    /** {@inheritDoc} */
    @Override
    public boolean existsByNamespace(String tenantId, String namespace) {
        log.trace("Checking namespace file existence by namespace via gRPC: tenantId={}, namespace={}", tenantId, namespace);

        NamespaceRequest request = NamespaceRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(tenantId)
            .setNamespace(namespace)
            .build();

        BooleanResponse response = stub.existsByNamespace(request);
        return response.getValue();
    }

    /** {@inheritDoc} */
    @Override
    public NamespaceFileMetadata save(NamespaceFileMetadata item) {
        log.trace("Saving namespace file metadata via gRPC: namespace={}, path={}", item.getNamespace(), item.getPath());

        OpaqueData request = OpaqueData.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setMessage(MESSAGE_FORMAT.toByteString(item))
            .build();

        OpaqueData response = stub.save(request);
        return MESSAGE_FORMAT.fromByteString(response.getMessage(), NamespaceFileMetadata.class);
    }
}
