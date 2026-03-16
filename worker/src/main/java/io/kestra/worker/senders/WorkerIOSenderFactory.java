package io.kestra.worker.senders;

import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceStub;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.Logs;
import io.kestra.core.worker.models.WorkerTriggerResult;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.senders.GrpcWorkerIOSender.SendStrategy;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.event.Level;

import java.util.Map;

/**
 * Micronaut factory that creates all {@link WorkerIOSender} instances.
 */
@Factory
public class WorkerIOSenderFactory {

    /**
     * Creates a sender for {@link WorkerTaskResult} events (sent per-item).
     * <p>
     * If the server rejects the message with {@code RESOURCE_EXHAUSTED} (e.g. outputs too large),
     * the result is retried with a failed state and no outputs so the execution can still terminate.
     */
    @Singleton
    @Named
    public GrpcWorkerIOSender<WorkerTaskResult> taskResultSender(
        final WorkerControllerServiceStub controllerServiceStub,
        final WorkerQueueRegistry workerQueueRegistry) {
        return new GrpcWorkerIOSender<>(
            workerQueueRegistry,
            "TaskResultWorkerIOSender",
            WorkerTaskResult.class,
            SendStrategy.PER_ITEM,
            controllerServiceStub::sendWorkerTaskResults,
            result -> {
                Logs.logTaskRun(result.getTaskRun(), Level.ERROR, "Failed to send result. Cause: outputs exceeds maximum size.");
                return result.withTaskRun(result.getTaskRun().fail()).withOutputs(null);
            }
        );
    }

    /**
     * Creates a sender for {@link WorkerTriggerResult} events (sent per-item).
     */
    @Singleton
    @Named
    public GrpcWorkerIOSender<WorkerTriggerResult> triggerResultSender(
        final WorkerControllerServiceStub controllerServiceStub,
        final WorkerQueueRegistry workerQueueRegistry) {
        return new GrpcWorkerIOSender<>(
            workerQueueRegistry,
            "TriggerResultWorkerIOSender",
            WorkerTriggerResult.class,
            SendStrategy.PER_ITEM,
            controllerServiceStub::sendWorkerTriggerResults,
            null
        );
    }

    /**
     * Creates a sender for {@link LogEntry} events (sent as a batch).
     */
    @Singleton
    @Named
    public GrpcWorkerIOSender<LogEntry> logEntrySender(
        final WorkerControllerServiceStub controllerServiceStub,
        final WorkerQueueRegistry workerQueueRegistry) {
        return new GrpcWorkerIOSender<>(
            workerQueueRegistry,
            "LogEntryWorkerIOSender",
            LogEntry.class,
            SendStrategy.BATCH,
            controllerServiceStub::sendWorkerLogEntries,
            null
        );
    }

    /**
     * Creates a sender for {@link MetricEntry} events (sent as a batch).
     */
    @Singleton
    @Named
    public GrpcWorkerIOSender<MetricEntry> metricsSender(
        final WorkerControllerServiceStub controllerServiceStub,
        final WorkerQueueRegistry workerQueueRegistry) {
        return new GrpcWorkerIOSender<>(
            workerQueueRegistry,
            "MetricsWorkerIOSender",
            MetricEntry.class,
            SendStrategy.BATCH,
            controllerServiceStub::sendWorkerMetricEntries,
            null
        );
    }
}
