package io.kestra.controller.grpc.services;

import java.util.List;
import java.util.Objects;

import io.kestra.core.executor.WorkerJobRunningStateStore;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.MessageTooBigException;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.UnsupportedMessageException;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.runners.RunContextLoggerFactory;
import io.kestra.core.runners.WorkerTaskResult;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes the task results a worker sends back and releases their running entries once terminal.
 */
@Singleton
@Slf4j
public class WorkerTaskResultPublisher {
    private final DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue;
    private final WorkerJobRunningStateStore workerJobRunningStateStore;
    private final RunContextLoggerFactory runContextLoggerFactory;

    @Inject
    public WorkerTaskResultPublisher(
        DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue,
        WorkerJobRunningStateStore workerJobRunningStateStore,
        RunContextLoggerFactory runContextLoggerFactory
    ) {
        this.workerTaskResultQueue = Objects.requireNonNull(workerTaskResultQueue);
        this.workerJobRunningStateStore = Objects.requireNonNull(workerJobRunningStateStore);
        this.runContextLoggerFactory = Objects.requireNonNull(runContextLoggerFactory);
    }

    /**
     * Emits the results as one batch, then releases the running entries of the terminated task runs in one call.
     * A batch that fails to publish is retried one result at a time, so a single oversized or unsupported payload
     * only fails its own task run.
     */
    public void publish(List<WorkerTaskResult> results) {
        if (results.isEmpty()) {
            return;
        }

        try {
            workerTaskResultQueue.emit(results);
        } catch (QueueException e) {
            log.warn("Unable to emit {} worker task results as a batch, retrying them one by one: {}", results.size(), e.getMessage());
            results.forEach(this::publishOne);
            return;
        }

        List<String> terminatedTaskRunIds = results.stream()
            .filter(result -> result.getTaskRun().getState().isTerminated())
            .map(result -> result.getTaskRun().getId())
            .toList();
        workerJobRunningStateStore.deleteByKeys(terminatedTaskRunIds);
    }

    private void publishOne(WorkerTaskResult workerTaskResult) {
        try {
            emitAndRelease(workerTaskResult);
        } catch (QueueException e) {
            WorkerTaskResult failed = new WorkerTaskResult(workerTaskResult.getTaskRun().fail(), workerTaskResult.getOutputs());
            // an unsupported payload is most likely a bad output value, so it is retried without outputs like an oversized one
            if (e instanceof MessageTooBigException || e instanceof UnsupportedMessageException) {
                failed = failed.withOutputs(null);
            }
            RunContextLogger contextLogger = runContextLoggerFactory.create(workerTaskResult);
            contextLogger.logger().error("Unable to emit the worker task result to the queue: {}", e.getMessage(), e);
            try {
                emitAndRelease(failed);
            } catch (QueueException ex) {
                log.error("Unable to emit the worker task result for task {} taskrun {}", failed.getTaskRun().getTaskId(), failed.getTaskRun().getId(), e);
            }
        }
    }

    private void emitAndRelease(WorkerTaskResult workerTaskResult) throws QueueException {
        workerTaskResultQueue.emit(workerTaskResult);

        if (workerTaskResult.getTaskRun().getState().isTerminated()) {
            workerJobRunningStateStore.deleteByKey(workerTaskResult.getTaskRun().getId());
        }
    }
}
