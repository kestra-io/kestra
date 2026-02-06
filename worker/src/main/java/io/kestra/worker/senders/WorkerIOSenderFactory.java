package io.kestra.worker.senders;

import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceStub;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.worker.models.WorkerTriggerResult;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.senders.GrpcWorkerIOSender.SendStrategy;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/**
 * Micronaut factory that creates all {@link WorkerIOSender} instances.
 */
@Factory
public class WorkerIOSenderFactory {

    /**
     * Creates a sender for {@link WorkerTaskResult} events (sent per-item).
     */
    @Singleton
    public GrpcWorkerIOSender<WorkerTaskResult> taskResultSender(
        final WorkerControllerServiceStub controllerServiceStub,
        final WorkerQueueRegistry workerQueueRegistry) {
        return new GrpcWorkerIOSender<>(
            workerQueueRegistry,
            "TaskResultWorkerIOSender",
            WorkerTaskResult.class,
            SendStrategy.PER_ITEM,
            controllerServiceStub::sendWorkerTaskResults
        );
    }

    /**
     * Creates a sender for {@link WorkerTriggerResult} events (sent per-item).
     */
    @Singleton
    public GrpcWorkerIOSender<WorkerTriggerResult> triggerResultSender(
        final WorkerControllerServiceStub controllerServiceStub,
        final WorkerQueueRegistry workerQueueRegistry) {
        return new GrpcWorkerIOSender<>(
            workerQueueRegistry,
            "TriggerResultWorkerIOSender",
            WorkerTriggerResult.class,
            SendStrategy.PER_ITEM,
            controllerServiceStub::sendWorkerTriggerResults
        );
    }

    /**
     * Creates a sender for {@link LogEntry} events (sent as a batch).
     */
    @Singleton
    public GrpcWorkerIOSender<LogEntry> logEntrySender(
        final WorkerControllerServiceStub controllerServiceStub,
        final WorkerQueueRegistry workerQueueRegistry) {
        return new GrpcWorkerIOSender<>(
            workerQueueRegistry,
            "LogEntryWorkerIOSender",
            LogEntry.class,
            SendStrategy.BATCH,
            controllerServiceStub::sendWorkerLogEntries
        );
    }

    /**
     * Creates a sender for {@link MetricEntry} events (sent as a batch).
     */
    @Singleton
    public GrpcWorkerIOSender<MetricEntry> metricsSender(
        final WorkerControllerServiceStub controllerServiceStub,
        final WorkerQueueRegistry workerQueueRegistry) {
        return new GrpcWorkerIOSender<>(
            workerQueueRegistry,
            "MetricsWorkerIOSender",
            MetricEntry.class,
            SendStrategy.BATCH,
            controllerServiceStub::sendWorkerMetricEntries
        );
    }
}
