package io.kestra.worker.systemworker;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.server.ServiceType;
import io.kestra.core.services.MaintenanceService;
import io.kestra.core.worker.WorkerQueues;
import io.kestra.worker.AbstractWorker;
import io.kestra.worker.WorkerJobExecutor;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Worker variant hosted inside the executor / standalone process. Reuses
 * the full {@link AbstractWorker} pipeline (executor, IO senders, maintenance
 * integration) but with the {@link DirectQueueJobFetcher} on the inbound side
 * and {@link DirectQueueWorkerIOSender} instances on the outbound side: no
 * gRPC is involved, no liveness is registered through the gRPC path.
 * <p>
 * Does not implement {@link io.kestra.core.runners.Worker} on purpose so it
 * does not collide with the regular {@link io.kestra.worker.WorkerAgent}
 * singleton in STANDALONE mode where both run in the same JVM.
 */
@Singleton
@Requires(property = "kestra.server-type", pattern = "(EXECUTOR|STANDALONE)")
@Slf4j
public class SystemWorker extends AbstractWorker {

    @Inject
    public SystemWorker(
        ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher,
        WorkerJobExecutor workerJobExecutor,
        DirectQueueJobFetcher directQueueJobFetcher,
        List<DirectQueueWorkerIOSender<?>> workerIOSenders,
        MaintenanceService maintenanceService,
        MetricRegistry metricRegistry,
        ServerConfig serverConfig
    ) {
        super(
            ServiceType.WORKER,
            eventPublisher,
            workerJobExecutor,
            directQueueJobFetcher,
            workerIOSenders,
            maintenanceService,
            metricRegistry,
            serverConfig,
            "system-worker-io-"
        );
    }

    /**
     * Start with the default thread-pool size, pinned to the reserved
     * {@link WorkerQueues#SYSTEM_ID} Worker Queue. The pool size mirrors the
     * executor's sizing pattern but multiplied by 4 because SystemTasks are
     * I/O-bound (DB-backed purge / ship-out work).
     */
    public void start() {
        int threads = Math.max(4, KestraContext.getContext().getAllocatedCpuCores());
        start(threads);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The SystemWorker exclusively serves the reserved {@link WorkerQueues#SYSTEM_ID}
     * Worker Queue.
     */
    @Override
    protected String resolveWorkerGroupId() {
        return WorkerQueues.SYSTEM_ID;
    }
}
