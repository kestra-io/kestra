package io.kestra.worker;

import java.util.List;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.runners.Worker;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.server.ServiceType;
import io.kestra.core.services.MaintenanceService;
import io.kestra.worker.fetchers.WorkerJobFetcher;
import io.kestra.worker.senders.GrpcWorkerIOSender;
import io.kestra.worker.services.WorkerConnectionService;

import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Default worker process. Reuses the lifecycle and IO-sender plumbing from
 * {@link AbstractWorker} and adds the gRPC handshake against the worker
 * controller via {@link WorkerConnectionService}.
 */
@Slf4j
@Singleton
public class WorkerAgent extends AbstractWorker implements Worker {

    private final WorkerConnectionService workerConnectionService;

    @Inject
    public WorkerAgent(
        ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher,
        WorkerConnectionService workerConnectionService,
        WorkerJobExecutor workerJobExecutor,
        WorkerJobFetcher workerJobFetcher,
        List<GrpcWorkerIOSender<?>> workerIOSenders,
        MaintenanceService maintenanceService,
        MetricRegistry metricRegistry,
        ServerConfig serverConfig
    ) {
        super(
            ServiceType.WORKER,
            eventPublisher,
            workerJobExecutor,
            workerJobFetcher,
            workerIOSenders,
            maintenanceService,
            metricRegistry,
            serverConfig,
            "worker-io-"
        );
        this.workerConnectionService = workerConnectionService;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Resolves the worker group by performing the gRPC handshake against the
     * worker controller. The controller may override the user-supplied key
     * with a server-assigned group.
     */
    @Override
    protected String resolveWorkerGroup(String workerGroupKey) {
        WorkerConnectionService.ConnectionResult connectionResult =
            workerConnectionService.connect(getId(), workerGroupKey);
        return connectionResult.workerGroup();
    }
}
