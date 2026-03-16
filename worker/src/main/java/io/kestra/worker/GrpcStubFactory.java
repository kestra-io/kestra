package io.kestra.worker;

import io.grpc.Deadline;
import io.grpc.stub.AbstractStub;
import io.kestra.controller.GrpcChannelManager;
import io.kestra.controller.config.WorkerControllersConfiguration;
import io.kestra.controller.grpc.ConnectControllerServiceGrpc;
import io.kestra.controller.grpc.ConnectControllerServiceGrpc.ConnectControllerServiceBlockingStub;
import io.kestra.controller.grpc.KVMetadataServiceGrpc;
import io.kestra.controller.grpc.KVMetadataServiceGrpc.KVMetadataServiceBlockingStub;
import io.kestra.controller.grpc.LivenessControllerServiceGrpc;
import io.kestra.controller.grpc.LivenessControllerServiceGrpc.LivenessControllerServiceBlockingStub;
import io.kestra.controller.grpc.NamespaceFileMetadataServiceGrpc;
import io.kestra.controller.grpc.NamespaceFileMetadataServiceGrpc.NamespaceFileMetadataServiceBlockingStub;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceBlockingStub;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceStub;
import io.kestra.controller.grpc.WorkerFlowMetaStoreServiceGrpc;
import io.kestra.controller.grpc.WorkerFlowMetaStoreServiceGrpc.WorkerFlowMetaStoreServiceBlockingStub;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;

/**
 * Factory for creating gRPC stubs for worker services.
 */
@Factory
public class GrpcStubFactory {
    
    @Inject
    WorkerControllersConfiguration workerControllersConfiguration;
    
    @Bean
    @Singleton
    public WorkerControllerServiceBlockingStub blockingWorkerServiceStub(GrpcChannelManager manager) {
        return WorkerControllerServiceGrpc.newBlockingStub(manager.getDefaultChannel());
    }

    @Bean
    @Singleton
    public WorkerControllerServiceStub asyncWorkerServiceStub(GrpcChannelManager manager) {
        return withWaitForReady(WorkerControllerServiceGrpc.newStub(manager.getDefaultChannel()), false);
    }

    @Bean
    @Singleton
    public LivenessControllerServiceBlockingStub workerServiceStub(GrpcChannelManager manager) {
        return LivenessControllerServiceGrpc.newBlockingStub(manager.getDefaultChannel());
    }

    @Bean
    @Singleton
    public ConnectControllerServiceBlockingStub connectControllerServiceBlockingStub(GrpcChannelManager manager) {
        ConnectControllerServiceBlockingStub stub = ConnectControllerServiceGrpc.newBlockingStub(manager.getDefaultChannel());
        // Only set wait-for-ready here; deadline is applied per-call to avoid a stale absolute timestamp on a singleton.
        return withWaitForReady(stub, false);
    }
    
    @Bean
    @Singleton
    public WorkerFlowMetaStoreServiceBlockingStub workerFlowMetaStoreServiceBlockingStub(GrpcChannelManager manager) {
        return WorkerFlowMetaStoreServiceGrpc.newBlockingStub(manager.getDefaultChannel());
    }

    @Bean
    @Singleton
    public KVMetadataServiceBlockingStub kvMetadataServiceBlockingStub(GrpcChannelManager manager) {
        return KVMetadataServiceGrpc.newBlockingStub(manager.getDefaultChannel());
    }

    @Bean
    @Singleton
    public NamespaceFileMetadataServiceBlockingStub namespaceFileMetadataServiceBlockingStub(GrpcChannelManager manager) {
        return NamespaceFileMetadataServiceGrpc.newBlockingStub(manager.getDefaultChannel());
    }

    public <S extends AbstractStub<S>> S withWaitForReady(S stub, boolean deadline) {
        if (workerControllersConfiguration.waitForReady().enabled()) {
            stub = stub.withWaitForReady();
            if (deadline) {
                Duration duration = workerControllersConfiguration.waitForReady().deadline();
                stub = stub.withDeadline(Deadline.after(duration.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS));
            }
        }
        return stub;
    }
}
