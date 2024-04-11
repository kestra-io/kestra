package io.kestra.core.runners;

import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.services.WorkerGroupService;
import io.kestra.core.utils.ExecutorsUtils;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;

/**
 * This worker is a special worker which won't close every queue allowing it to be ran and closed within a test without
 * preventing the Micronaut context to be used for further tests with queues still being up
 */
@Prototype
public class TestMethodScopedWorker extends Worker {
    @Inject
    public TestMethodScopedWorker(@Parameter String workerId,
                                  @Parameter Integer numThreads,
                                  @Nullable @Parameter String workerGroupKey,
                                  ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher,
                                  WorkerGroupService workerGroupService,
                                  ExecutorsUtils executorsUtils
    ) {
        super(workerId, numThreads, workerGroupKey, eventPublisher, workerGroupService, executorsUtils);
    }

    /**
     * Override is done to prevent closing the queue. However, please note that this is not failsafe because we ideally need
     * to stop worker's subscriptions to every queue before cutting of the executors pool.
     */
    @Override
    public void close() {
        shutdown();
    }
}