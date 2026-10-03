package io.kestra.worker.senders;

import java.time.Instant;

import org.slf4j.event.Level;

import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceStub;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.LogEntryEmitter;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.Logs;
import io.kestra.core.worker.models.WorkerTriggerResult;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.senders.GrpcWorkerIOSender.SendStrategy;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/**
 * Micronaut factory that creates the gRPC {@link WorkerIOSender} instances
 * used by the regular {@link io.kestra.worker.WorkerAgent}. Consumers
 * disambiguate from the direct-queue senders used by the SystemWorker by
 * depending on the concrete {@link GrpcWorkerIOSender} type rather than the
 * {@link WorkerIOSender} interface.
 */
@Factory
public class GrpcWorkerIOSenderFactory {

    private static final String RESULT_TOO_LARGE_MESSAGE =
        "Failed to send the task result to the worker controller: its serialized size exceeds the maximum inbound gRPC message size configured on the controller. "
            + "The task run is failed and its outputs are dropped. Reduce the size of the task outputs, or increase 'kestra.grpc.max-inbound-message-size' on the worker controller.";

    /**
     * Creates a sender for {@link WorkerTaskResult} events (sent per-item).
     * <p>
     * If the server rejects the message with {@code RESOURCE_EXHAUSTED} (e.g. outputs too large),
     * the result is retried with a failed state and no outputs so the execution can still terminate.
     */
    @Singleton
    public GrpcWorkerIOSender<WorkerTaskResult> taskResultSender(
        final WorkerControllerServiceStub controllerServiceStub,
        final WorkerQueueRegistry workerQueueRegistry,
        final LogEntryEmitter logEntryEmitter) {
        return new GrpcWorkerIOSender<>(
            workerQueueRegistry,
            "TaskResultWorkerIOSender",
            WorkerTaskResult.class,
            SendStrategy.PER_ITEM,
            controllerServiceStub::sendWorkerTaskResults,
            result ->
            {
                Logs.logTaskRun(result.getTaskRun(), Level.ERROR, RESULT_TOO_LARGE_MESSAGE);
                logEntryEmitter.emits(taskRunLogEntry(result.getTaskRun(), RESULT_TOO_LARGE_MESSAGE));
                return result.withTaskRun(result.getTaskRun().withStateAndAttempt(State.Type.FAILED)).withoutOutputs();
            },
            // Task results must survive a transient network partition: re-queue and redrive rather than
            // drop, otherwise a completed task is left stuck RUNNING because its result is lost.
            true
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
            controllerServiceStub::sendWorkerTriggerResults,
            null,
            // Trigger results are terminal work output — re-queue on a transient network partition, like task results.
            true
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
            controllerServiceStub::sendWorkerLogEntries,
            null,
            // Logs are best-effort and high-volume: re-queuing could back-pressure the worker, so drop on failure.
            false
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
            controllerServiceStub::sendWorkerMetricEntries,
            null,
            // Metrics are best-effort and high-volume: drop on failure rather than back-pressure the worker.
            false
        );
    }

    private static LogEntry taskRunLogEntry(final TaskRun taskRun, final String message) {
        int lastAttemptIndex = Math.max(0, taskRun.attemptNumber() - 1);
        return LogEntry.of(taskRun, null).toBuilder()
            .level(Level.ERROR)
            .attemptNumber(lastAttemptIndex)
            .message(message)
            .timestamp(Instant.now())
            .thread(Thread.currentThread().getName())
            .build();
    }
}
