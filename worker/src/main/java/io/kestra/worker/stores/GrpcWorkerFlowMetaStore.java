package io.kestra.worker.stores;

import io.kestra.controller.grpc.BooleanResponse;
import io.kestra.controller.grpc.NamespaceRequest;
import io.kestra.controller.grpc.WorkerFlowMetaStoreServiceGrpc.WorkerFlowMetaStoreServiceBlockingStub;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.runners.DefaultFlowMetaStore;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.worker.models.WorkerInfo;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Optional;

/**
 * Worker-side implementation of {@link FlowMetaStoreInterface} that retrieves
 * flow metadata from the controller via gRPC.
 * <p>
 * This implementation is used only by workers and replaces the default implementation
 * that uses queue cache and repositories (which are not available to workers).
 * <p>
 * Only methods required by {@link io.kestra.core.services.DefaultNamespaceService} are implemented.
 * All other methods throw {@link UnsupportedOperationException}.
 */
@Singleton
@Slf4j
@Requires(property = "kestra.server-type", value = "WORKER")
@Replaces(DefaultFlowMetaStore.class)
public class GrpcWorkerFlowMetaStore implements FlowMetaStoreInterface {

    private final WorkerInfo workerInfo;

    private final WorkerFlowMetaStoreServiceBlockingStub workerFlowMetaStoreStub;

    @Inject
    public GrpcWorkerFlowMetaStore(WorkerFlowMetaStoreServiceBlockingStub workerFlowMetaStoreStub,
                                   WorkerInfo workerInfo) {
        this.workerFlowMetaStoreStub = workerFlowMetaStoreStub;
        this.workerInfo = workerInfo;
    }

    @Override
    public boolean isNamespaceExists(String tenant, String namespace) {
        log.debug("Checking namespace exists via gRPC: tenant={}, namespace={}", tenant, namespace);

        NamespaceRequest request = NamespaceRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(tenant)
            .setNamespace(namespace)
            .build();
        
        BooleanResponse namespaceExists = workerFlowMetaStoreStub.isNamespaceExists(request);
        return namespaceExists.getValue();
    }

    @Override
    public Collection<FlowWithSource> allLastVersion() {
        throw new UnsupportedOperationException("allLastVersion is not supported on workers");
    }

    @Override
    public Optional<FlowInterface> findById(String tenantId, String namespace, String id, Optional<Integer> revision) {
        throw new UnsupportedOperationException("findById is not supported on workers");
    }

    @Override
    public Optional<FlowInterface> findByIdFromTask(String tenantId, String namespace, String id, Optional<Integer> revision, String fromTenant, String fromNamespace, String fromId) {
        throw new UnsupportedOperationException("findByIdFromTask is not supported on workers");
    }

    @Override
    public Optional<FlowInterface> findByExecution(Execution execution) {
        throw new UnsupportedOperationException("findByExecution is not supported on workers");
    }

    @Override
    public Optional<FlowWithSource> findByExecutionThenInjectDefaults(Execution execution) {
        throw new UnsupportedOperationException("findByExecutionThenInjectDefaults is not supported on workers");
    }
}
