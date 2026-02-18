package io.kestra.worker;

import io.kestra.controller.GrpcChannelManager;
import io.kestra.controller.grpc.ConnectControllerServiceGrpc;
import io.kestra.controller.grpc.ConnectControllerServiceGrpc.ConnectControllerServiceBlockingStub;
import io.kestra.controller.grpc.KVMetadataServiceGrpc;
import io.kestra.controller.grpc.KVMetadataServiceGrpc.KVMetadataServiceBlockingStub;
import io.kestra.controller.grpc.LivenessControllerServiceGrpc;
import io.kestra.controller.grpc.LivenessControllerServiceGrpc.LivenessControllerServiceBlockingStub;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceBlockingStub;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceStub;
import io.kestra.controller.grpc.WorkerFlowMetaStoreServiceGrpc;
import io.kestra.controller.grpc.WorkerFlowMetaStoreServiceGrpc.WorkerFlowMetaStoreServiceBlockingStub;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Factory for creating gRPC stubs for worker services.
 */
@Factory
public class GrpcStubFactory {
    
    @Bean
    @Singleton
    public WorkerControllerServiceBlockingStub blockingWorkerServiceStub(GrpcChannelManager manager) {
        return WorkerControllerServiceGrpc.newBlockingStub(manager.getDefaultChannel());
    }

    @Bean
    @Singleton
    public WorkerControllerServiceStub asyncWorkerServiceStub(GrpcChannelManager manager) {
        return WorkerControllerServiceGrpc.newStub(manager.getDefaultChannel());
    }

    @Bean
    @Singleton
    public LivenessControllerServiceBlockingStub workerServiceStub(GrpcChannelManager manager) {
        return LivenessControllerServiceGrpc.newBlockingStub(manager.getDefaultChannel());
    }

    @Bean
    @Singleton
    public ConnectControllerServiceBlockingStub connectControllerServiceBlockingStub(GrpcChannelManager manager) {
        return ConnectControllerServiceGrpc.newBlockingStub(manager.getDefaultChannel());
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
}
