package io.kestra.core.runners;

import java.time.Instant;

import io.kestra.core.models.HasUID;
import io.kestra.core.queues.event.KeyedDispatchEvent;

import jakarta.annotation.Nullable;

/**
 * Event wrapper for {@link WorkerJob} that implements {@link KeyedDispatchEvent}, routed
 * by the Worker Queue id used internally by the dispatch queue.
 *
 * <p>
 * The internal routing convention is unchanged: {@code null} or empty {@code workerQueueId}
 * means the default queue. The user-facing
 * {@link io.kestra.core.worker.WorkerQueues#DEFAULT_ID "default"} sentinel that
 * appears on {@link io.kestra.core.worker.QueueSubscription} and
 * {@link WorkerQueueRouting} must be normalized to {@code null} by callers before
 * emitting.
 *
 * @param workerQueueId The Worker Queue id for routing. Null/empty for the default queue.
 * @param job The actual worker job payload.
 * @param stickyRouting The routing to the one worker that must run the job, or {@code null} for any worker of the Worker Queue.
 */
public record WorkerJobEvent(
    String workerQueueId,
    WorkerJob job,
    @Nullable StickyRouting stickyRouting) implements KeyedDispatchEvent, HasUID {

    public WorkerJobEvent {
        workerQueueId = normalizeWorkerQueue(workerQueueId);
    }

    public WorkerJobEvent(String workerQueueId, WorkerJob job) {
        this(workerQueueId, job, null);
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
     * Returns the routing key for the keyed dispatch queue: the vNode routing key of a sticky job,
     * otherwise the Worker Queue id, or empty string for the default queue.
     */
    @Override
    public String key() {
        if (stickyRouting != null) {
            return stickyRouting.vNodeRoutingKey();
        }
        return workerQueueId != null ? workerQueueId : "";
    }

    public WorkerJobEvent withStickyRouting(StickyRouting stickyRouting) {
        return new WorkerJobEvent(workerQueueId, job, stickyRouting);
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

    /**
     * Routes a job to one worker through a vNode of the controller holding it.
     *
     * @param workerId The worker that must run the job.
     * @param vNodeRoutingKey The routing key of a vNode owned by the controller holding the worker.
     * @param workerUnknownSince Since when no controller has held the worker, or {@code null} once one was found.
     */
    public record StickyRouting(String workerId, String vNodeRoutingKey, @Nullable Instant workerUnknownSince) {

        public StickyRouting(String workerId, String vNodeRoutingKey) {
            this(workerId, vNodeRoutingKey, null);
        }

        public StickyRouting workerFound(String vNodeRoutingKey) {
            return new StickyRouting(workerId, vNodeRoutingKey, null);
        }

        public StickyRouting workerUnknown(Instant now) {
            return new StickyRouting(workerId, vNodeRoutingKey, workerUnknownSince == null ? now : workerUnknownSince);
        }
    }
}
