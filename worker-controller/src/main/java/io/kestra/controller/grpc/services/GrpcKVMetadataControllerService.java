package io.kestra.controller.grpc.services;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.controller.grpc.BooleanResponse;
import io.kestra.controller.grpc.KVMetadataRequest;
import io.kestra.controller.grpc.KVMetadataServiceGrpc;
import io.kestra.controller.grpc.NamespaceRequest;
import io.kestra.controller.grpc.OpaqueData;
import io.kestra.controller.grpc.WorkerControllerService;
import io.kestra.controller.messages.MessageFormat;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.runners.KVMetadataStateStore;
import io.kestra.core.worker.models.WorkerInfo;

import io.grpc.stub.StreamObserver;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * gRPC service implementation for KV metadata operations.
 * Provides worker-safe KV metadata read/write to workers via gRPC.
 */
@Singleton
@Requires(property = "kestra.server-type", pattern = "(CONTROLLER|STANDALONE)")
public class GrpcKVMetadataControllerService extends KVMetadataServiceGrpc.KVMetadataServiceImplBase implements WorkerControllerService {

    private static final Logger log = LoggerFactory.getLogger(GrpcKVMetadataControllerService.class);
    private static final MessageFormat MESSAGE_FORMAT = MessageFormats.JSON;

    private final KVMetadataStateStore kvMetadataStateStore;
    private final WorkerInfo workerInfo;

    @Inject
    public GrpcKVMetadataControllerService(final KVMetadataStateStore kvMetadataStateStore,
        final WorkerInfo workerInfo) {
        this.kvMetadataStateStore = kvMetadataStateStore;
        this.workerInfo = workerInfo;
    }

    @Override
    public void findByName(KVMetadataRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            log.trace(
                "Received findByName request: tenantId={}, namespace={}, name={}",
                request.getTenantId(), request.getNamespace(), request.getName()
            );

            Optional<PersistedKvMetadata> result = kvMetadataStateStore.findByName(
                request.getTenantId(), request.getNamespace(), request.getName()
            );

            OpaqueData response = OpaqueData.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
                .setMessage(MESSAGE_FORMAT.toByteString(result.orElse(null)))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error during findByName", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void find(NamespaceRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            String tenantId = request.getTenantId();
            String namespace = request.getNamespace();

            log.trace("Received find request: tenantId={}, namespace={}", tenantId, namespace);

            List<PersistedKvMetadata> result = kvMetadataStateStore.find(tenantId, namespace);

            OpaqueData response = OpaqueData.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
                .setMessage(MESSAGE_FORMAT.toByteString(result))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error during find", e);
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

            boolean exists = kvMetadataStateStore.existsByNamespace(request.getTenantId(), request.getNamespace());

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

            PersistedKvMetadata item = MESSAGE_FORMAT.fromByteString(request.getMessage(), PersistedKvMetadata.class);

            PersistedKvMetadata result = kvMetadataStateStore.save(item);

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

    @Override
    public void deleteByName(KVMetadataRequest request, StreamObserver<OpaqueData> responseObserver) {
        try {
            log.trace("Received delete request");

            Optional<PersistedKvMetadata> result = kvMetadataStateStore.deleteByName(request.getTenantId(), request.getNamespace(), request.getName());

            OpaqueData response = OpaqueData.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
                .setMessage(MESSAGE_FORMAT.toByteString(result.orElse(null)))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error during delete", e);
            responseObserver.onError(e);
        }
    }
}
