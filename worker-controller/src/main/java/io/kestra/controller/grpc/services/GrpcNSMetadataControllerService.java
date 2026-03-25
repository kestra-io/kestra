package io.kestra.controller.grpc.services;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.controller.grpc.*;
import io.kestra.controller.messages.MessageFormat;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.namespace.NamespaceFileMetadataStateStore;
import io.kestra.core.worker.models.WorkerInfo;

import io.grpc.stub.StreamObserver;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * gRPC service implementation for namespace file metadata operations.
 * Provides worker-safe namespace file metadata read/write to workers via gRPC.
 */
@Singleton
@Requires(property = "kestra.server-type", pattern = "(CONTROLLER|STANDALONE)")
public class GrpcNSMetadataControllerService extends NamespaceFileMetadataServiceGrpc.NamespaceFileMetadataServiceImplBase implements WorkerControllerService {

    private static final Logger log = LoggerFactory.getLogger(GrpcNSMetadataControllerService.class);
    private static final MessageFormat MESSAGE_FORMAT = MessageFormats.JSON;

    private final NamespaceFileMetadataStateStore stateStore;
    private final WorkerInfo workerInfo;

    @Inject
    public GrpcNSMetadataControllerService(final NamespaceFileMetadataStateStore stateStore,
        final WorkerInfo workerInfo) {
        this.stateStore = stateStore;
        this.workerInfo = workerInfo;
    }

    @Override
    public void findByPath(NamespaceFileMetadataPathRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            Integer version = request.hasVersion() ? request.getVersion() : null;

            log.trace(
                "Received findByPath request: tenantId={}, namespace={}, path={}, version={}, allowDeleted={}",
                request.getTenantId(), request.getNamespace(), request.getPath(), version, request.getAllowDeleted()
            );

            Optional<NamespaceFileMetadata> result = stateStore.findByPath(
                request.getTenantId(), request.getNamespace(), request.getPath(),
                version, request.getAllowDeleted()
            );

            OpaqueData response = OpaqueData.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
                .setMessage(MESSAGE_FORMAT.toByteString(result.orElse(null)))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error during findByPath", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void findChildren(NamespaceFileMetadataChildrenRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            log.trace(
                "Received findChildren request: tenantId={}, namespace={}, parentPath={}, recursive={}",
                request.getTenantId(), request.getNamespace(), request.getParentPath(), request.getRecursive()
            );

            List<NamespaceFileMetadata> result = stateStore.findChildren(
                request.getTenantId(), request.getNamespace(),
                request.getParentPath(), request.getRecursive()
            );

            OpaqueData response = OpaqueData.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
                .setMessage(MESSAGE_FORMAT.toByteString(result))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error during findChildren", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void findAll(NamespaceFileMetadataFindAllRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            String containing = request.hasContaining() ? request.getContaining() : null;

            log.trace(
                "Received findAll request: tenantId={}, namespace={}, containing={}",
                request.getTenantId(), request.getNamespace(), containing
            );

            List<NamespaceFileMetadata> result = stateStore.findAll(
                request.getTenantId(), request.getNamespace(), containing
            );

            OpaqueData response = OpaqueData.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
                .setMessage(MESSAGE_FORMAT.toByteString(result))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error during findAll", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void findByPaths(NamespaceFileMetadataPathsRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            log.trace(
                "Received findByPaths request: tenantId={}, namespace={}, paths={}, allowDeleted={}",
                request.getTenantId(), request.getNamespace(), request.getPathsList(), request.getAllowDeleted()
            );

            List<NamespaceFileMetadata> result = stateStore.findByPaths(
                request.getTenantId(), request.getNamespace(),
                request.getPathsList(), request.getAllowDeleted()
            );

            OpaqueData response = OpaqueData.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
                .setMessage(MESSAGE_FORMAT.toByteString(result))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error during findByPaths", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void findAllVersionsByPaths(NamespaceFileMetadataPathsRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            log.trace(
                "Received findAllVersionsByPaths request: tenantId={}, namespace={}, paths={}",
                request.getTenantId(), request.getNamespace(), request.getPathsList()
            );

            List<NamespaceFileMetadata> result = stateStore.findAllVersionsByPaths(
                request.getTenantId(), request.getNamespace(), request.getPathsList()
            );

            OpaqueData response = OpaqueData.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
                .setMessage(MESSAGE_FORMAT.toByteString(result))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error during findAllVersionsByPaths", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void existsByNamespace(NamespaceRequest request, StreamObserver<BooleanResponse> responseObserver) {
        try {
            log.trace(
                "Received existsByNamespace request: tenantId={}, namespace={}",
                request.getTenantId(), request.getNamespace()
            );

            boolean exists = stateStore.existsByNamespace(request.getTenantId(), request.getNamespace());

            BooleanResponse response = BooleanResponse.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
                .setValue(exists)
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error during existsByNamespace", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void save(OpaqueData request, StreamObserver<OpaqueData> responseObserver) {
        try {
            log.trace("Received save request");

            NamespaceFileMetadata item = MESSAGE_FORMAT.fromByteString(request.getMessage(), NamespaceFileMetadata.class);

            NamespaceFileMetadata result = stateStore.save(item);

            OpaqueData response = OpaqueData.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
                .setMessage(MESSAGE_FORMAT.toByteString(result))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error during save", e);
            responseObserver.onError(e);
        }
    }
}
