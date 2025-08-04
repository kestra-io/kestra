package io.kestra.worker;

import io.kestra.controller.GrpcChannelManager;
import io.kestra.controller.grpc.ConnectControllerServiceGrpc;
import io.kestra.controller.grpc.ConnectControllerServiceGrpc.ConnectControllerServiceBlockingStub;
import io.kestra.controller.grpc.LivenessControllerServiceGrpc;
import io.kestra.controller.grpc.LivenessControllerServiceGrpc.LivenessControllerServiceBlockingStub;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceBlockingStub;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceStub;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Factory for creating gRPC stubs for worker services.
 */
@Factory
public class GrpcStubFactory {

    @Inject
    GrpcChannelManager grpcChannelManager;

    @Bean
    @Singleton
    public WorkerControllerServiceBlockingStub blockingWorkerServiceStub() {
        return WorkerControllerServiceGrpc.newBlockingStub(grpcChannelManager.createOrGetDefault());
    }

    @Bean
    @Singleton
    public WorkerControllerServiceStub asyncWorkerServiceStub() {
        return WorkerControllerServiceGrpc.newStub(grpcChannelManager.createOrGetDefault());
    }

    @Bean
    @Singleton
    public LivenessControllerServiceBlockingStub workerServiceStub() {
        return LivenessControllerServiceGrpc.newBlockingStub(grpcChannelManager.createOrGetDefault());
    }

    @Bean
    @Singleton
    public ConnectControllerServiceBlockingStub connectControllerServiceBlockingStub() {
        return ConnectControllerServiceGrpc.newBlockingStub(grpcChannelManager.createOrGetDefault());
    }
}
