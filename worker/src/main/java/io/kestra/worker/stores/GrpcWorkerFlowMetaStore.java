package io.kestra.worker.stores;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.kestra.controller.grpc.BooleanResponse;
import io.kestra.controller.grpc.NamespaceRequest;
import io.kestra.controller.grpc.WorkerFlowMetaStoreServiceGrpc.WorkerFlowMetaStoreServiceBlockingStub;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.models.TenantAndNamespace;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.runners.DefaultFlowMetaStore;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.worker.MetaStoreCacheConfig;
import io.kestra.core.worker.MetadataChangePayload;
import io.kestra.core.worker.WorkerMetadataChangeHandler;
import io.kestra.core.worker.models.WorkerInfo;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Worker-side implementation of {@link FlowMetaStoreInterface} that retrieves
 * flow metadata from the controller via gRPC.
 * <p>
 * This implementation is used only by workers and replaces the default implementation
 * that uses queue cache and repositories (which are not available to workers).
 * <p>
 * Only methods required by {@link io.kestra.core.services.DefaultNamespaceService} are implemented.
 * All other methods throw {@link UnsupportedOperationException}.
 * <p>
 * {@link #isNamespaceExists} results are cached locally to spare the controller a gRPC
 * round-trip on every task. Entries are normally evicted by push invalidation events
 * (see {@link io.kestra.core.worker.WorkerMetadataChangeHandler}); the cache TTL is a
 * safety bound only.
 */
@Singleton
@Slf4j
@Requires(property = "kestra.server-type", value = "WORKER")
@Replaces(DefaultFlowMetaStore.class)
public class GrpcWorkerFlowMetaStore implements FlowMetaStoreInterface, WorkerMetadataChangeHandler {

    private final WorkerInfo workerInfo;
    private final WorkerFlowMetaStoreServiceBlockingStub workerFlowMetaStoreStub;
    private final Cache<TenantAndNamespace, Boolean> namespaceExistsCache;

    @Inject
    public GrpcWorkerFlowMetaStore(WorkerFlowMetaStoreServiceBlockingStub workerFlowMetaStoreStub,
        WorkerInfo workerInfo,
        MetaStoreCacheConfig cacheConfig) {
        this.workerFlowMetaStoreStub = workerFlowMetaStoreStub;
        this.workerInfo = workerInfo;
        this.namespaceExistsCache = Caffeine.newBuilder()
            .maximumSize(Objects.requireNonNull(cacheConfig).maximumSize())
            .expireAfterAccess(cacheConfig.expireAfterAccess())
            .build();
    }

    @Override
    public boolean isNamespaceExists(String tenant, String namespace) {
        return namespaceExistsCache.get(new TenantAndNamespace(tenant, namespace), this::fetchNamespaceExists);
    }

    private boolean fetchNamespaceExists(TenantAndNamespace key) {
        log.debug("Checking namespace exists via gRPC: tenant={}, namespace={}", key.tenantId(), key.namespace());

        NamespaceRequest request = NamespaceRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerInfo.getWorkerId()))
            .setTenantId(key.tenantId())
            .setNamespace(key.namespace())
            .build();

        BooleanResponse namespaceExists = workerFlowMetaStoreStub.isNamespaceExists(request);
        return namespaceExists.getValue();
    }

    public void invalidateNamespace(String tenant, String namespace) {
        namespaceExistsCache.invalidate(new TenantAndNamespace(tenant, namespace));
    }

    public void invalidateAll() {
        namespaceExistsCache.invalidateAll();
    }

    @Override
    public void onMetadataChange(MetadataChangePayload payload) {
        if (payload.type() == MetadataChangePayload.Type.FLOW) {
            invalidateNamespace(payload.tenantId(), payload.namespace());
        }
    }

    @Override
    public void onReconnect() {
        log.debug("gRPC stream (re)connected — invalidating flow metastore cache");
        invalidateAll();
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
