package io.kestra.worker.systemworker;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.worker.queues.WorkerQueueRegistry;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Direct-queue {@link io.kestra.worker.senders.WorkerIOSender}s consumed by
 * the {@link SystemWorker}. Consumers disambiguate from the gRPC senders
 * used by the regular {@link io.kestra.worker.WorkerAgent} by depending on
 * the concrete {@link DirectQueueWorkerIOSender} type rather than the
 * {@link io.kestra.worker.senders.WorkerIOSender} interface.
 * <p>
 * Trigger results are intentionally not handled: SystemTasks are tasks,
 * not triggers.
 */
@Factory
@Requires(property = "kestra.server-type", pattern = "(EXECUTOR|STANDALONE)")
public class DirectQueueWorkerIOSenderFactory {

    @Singleton
    public DirectQueueWorkerIOSender<WorkerTaskResult> taskResultSender(
        final WorkerQueueRegistry workerQueueRegistry,
        final DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue
    ) {
        return new DirectQueueWorkerIOSender<>(
            workerQueueRegistry,
            workerTaskResultQueue,
            "TaskResultSystemWorkerIOSender",
            WorkerTaskResult.class
        );
    }

    @Singleton
    public DirectQueueWorkerIOSender<LogEntry> logEntrySender(
        final WorkerQueueRegistry workerQueueRegistry,
        final DispatchQueueInterface<LogEntry> logQueue
    ) {
        return new DirectQueueWorkerIOSender<>(
            workerQueueRegistry,
            logQueue,
            "LogEntrySystemWorkerIOSender",
            LogEntry.class
        );
    }

    @Singleton
    public DirectQueueWorkerIOSender<MetricEntry> metricsSender(
        final WorkerQueueRegistry workerQueueRegistry,
        final DispatchQueueInterface<MetricEntry> metricQueue
    ) {
        return new DirectQueueWorkerIOSender<>(
            workerQueueRegistry,
            metricQueue,
            "MetricsSystemWorkerIOSender",
            MetricEntry.class
        );
    }
}
