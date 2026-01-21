package io.kestra.core.runners;

import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.services.MaintenanceService;
import io.kestra.worker.WorkerAgent;
import io.kestra.worker.WorkerJobExecutor;
import io.kestra.worker.fetchers.WorkerJobFetcher;
import io.kestra.worker.senders.WorkerIOSender;
import io.kestra.worker.services.WorkerConnectionService;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;

import java.util.List;

/**
 * This worker is a special worker which won't close every queue allowing it to be ran and closed within a test without
 * preventing the Micronaut context to be used for further tests with queues still being up
 */
// TODO check if this class is still required
public class TestMethodScopedWorker extends WorkerAgent {
    @Inject
    public TestMethodScopedWorker(ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher,
                                  WorkerConnectionService workerConnectionService,
                                  WorkerJobExecutor workerJobExecutor,
                                  WorkerJobFetcher workerJobFetcher,
                                  List<WorkerIOSender> workerIOSenders,
                                  MaintenanceService maintenanceService
    ) {
        super(eventPublisher, workerConnectionService, workerJobExecutor, workerJobFetcher, workerIOSenders, maintenanceService);
    }

    /**
     * Override is done to prevent closing the queue. However, please note that this is not failsafe because we ideally need
     * to stop worker's subscriptions to every queue before cutting of the executors pool.
     */
    @Override
    public void close() {
        super.close();
    }
}