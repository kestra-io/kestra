package io.kestra.core.runners;

import io.kestra.core.models.HasUID;
import io.kestra.core.queues.event.KeyedDispatchEvent;

import jakarta.annotation.Nullable;

/**
 * Event wrapper for {@link WorkerJob} that implements {@link KeyedDispatchEvent}, routed
 * by the Worker Queue id used internally by the dispatch queue.
 *
 * <p>The internal routing convention is unchanged: {@code null} or empty {@code workerQueueId}
 * means the default queue. The user-facing
 * {@link io.kestra.core.worker.WorkerQueues#DEFAULT_ID "default"} sentinel that
 * appears on {@link io.kestra.core.worker.QueueSubscription} and
 * {@link WorkerQueueRouting} must be normalized to {@code null} by callers before
 * emitting.
 *
 * @param workerQueueId The Worker Queue id for routing. Null/empty for the default queue.
 * @param job           The actual worker job payload.
 */
public record WorkerJobEvent(
    String workerQueueId,
    WorkerJob job) implements KeyedDispatchEvent, HasUID {

    public WorkerJobEvent {
        workerQueueId = normalizeWorkerQueue(workerQueueId);
    }

    public static WorkerJobEvent of(WorkerTask workerTask, @Nullable String workerQueueId) {
        return new WorkerJobEvent(workerQueueId, workerTask);
    }

    public static WorkerJobEvent of(WorkerTrigger workerTrigger, @Nullable String workerQueueId) {
        return new WorkerJobEvent(workerQueueId, workerTrigger);
    }

    public static WorkerJobEvent of(WorkerJob job, @Nullable String workerQueueId) {
        return new WorkerJobEvent(workerQueueId, job);
    }

    /**
     * Returns the routing key for the keyed dispatch queue: the Worker Queue id, or
     * empty string for the default queue.
     */
    @Override
    public String key() {
        return workerQueueId != null ? workerQueueId : "";
    }

    /**
     * Returns the unique identifier for this event (delegates to the wrapped job).
     */
    @Override
    public String uid() {
        return job.uid();
    }

    /**
     * Normalizes a Worker Queue id: null and empty string both represent the default queue.
     */
    private static String normalizeWorkerQueue(@Nullable String workerQueueId) {
        if (workerQueueId == null || workerQueueId.isEmpty()) {
            return null;
        }
        return workerQueueId;
    }
}
