package io.kestra.worker.stores;

import io.kestra.controller.grpc.TaskOutputProviderServiceGrpc;
import io.kestra.core.runners.DefaultWorkerLazyOutputProviderFactory;
import io.kestra.core.runners.LazyOutputProvider;
import io.kestra.core.runners.WorkerLazyOutputProviderFactory;
import io.kestra.core.worker.models.WorkerInfo;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Worker-side {@link WorkerLazyOutputProviderFactory} that communicates with the controller via gRPC.
 * Replaces {@link DefaultWorkerLazyOutputProviderFactory} on distributed workers.
 */
@Singleton
@Slf4j
@Requires(property = "kestra.server-type", value = "WORKER")
@Replaces(DefaultWorkerLazyOutputProviderFactory.class)
public class GrpcWorkerLazyOutputProviderFactory implements WorkerLazyOutputProviderFactory {

    private final TaskOutputProviderServiceGrpc.TaskOutputProviderServiceBlockingStub stub;
    private final WorkerInfo workerInfo;

    @Inject
    public GrpcWorkerLazyOutputProviderFactory(TaskOutputProviderServiceGrpc.TaskOutputProviderServiceBlockingStub stub,
                                                WorkerInfo workerInfo) {
        this.stub = stub;
        this.workerInfo = workerInfo;
    }

    /** {@inheritDoc} */
    @Override
    public LazyOutputProvider create(String tenantId, String executionId) {
        return new GrpcLazyOutputProvider(stub, workerInfo, tenantId, executionId);
    }
}
