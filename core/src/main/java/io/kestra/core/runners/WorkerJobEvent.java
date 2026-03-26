package io.kestra.core.runners;

import io.kestra.core.models.HasUID;
import io.kestra.core.queues.event.KeyedDispatchEvent;

import jakarta.annotation.Nullable;

/**
 * Event wrapper for WorkerJob that implements KeyedDispatchEvent.
 * <p>
 * This allows WorkerJob instances to be dispatched via the KeyedDispatchQueueInterface,
 * with the worker group key used for routing to specific worker groups.
 * <p>
 * The key is the worker group key (null or empty string for the default group).
 *
 * @param workerGroupKey The worker group key for routing. Null or empty string means the default worker group.
 * @param job The actual worker job payload.
 *
 */
public record WorkerJobEvent(
    String workerGroupKey,
    WorkerJob job) implements KeyedDispatchEvent, HasUID {

    public WorkerJobEvent {
        workerGroupKey = normalizeWorkerGroup(workerGroupKey);
    }

    /**
     * Creates a WorkerJobEvent for a WorkerTask.
     *
     * @param workerTask the worker task
     * @param workerGroupKey the worker group key (can be null for default group)
     * @return a new WorkerJobEvent
     */
    public static WorkerJobEvent of(WorkerTask workerTask, @Nullable String workerGroupKey) {
        return new WorkerJobEvent(workerGroupKey, workerTask);
    }

    /**
     * Creates a WorkerJobEvent for a WorkerTrigger.
     *
     * @param workerTrigger the worker trigger
     * @param workerGroupKey the worker group key (can be null for default group)
     * @return a new WorkerJobEvent
     */
    public static WorkerJobEvent of(WorkerTrigger workerTrigger, @Nullable String workerGroupKey) {
        return new WorkerJobEvent(workerGroupKey, workerTrigger);
    }

    /**
     * Creates a WorkerJobEvent from an existing WorkerJob.
     *
     * @param job the worker job
     * @param workerGroupKey the worker group key (can be null for default group)
     * @return a new WorkerJobEvent
     */
    public static WorkerJobEvent of(WorkerJob job, @Nullable String workerGroupKey) {
        return new WorkerJobEvent(workerGroupKey, job);
    }

    /**
     * Returns the routing key for the keyed dispatch queue.
     * This is the worker group key, normalized to empty string for the default group.
     *
     * @return the routing key (never null, empty string for default group)
     */
    @Override
    public String key() {
        return workerGroupKey != null ? workerGroupKey : "";
    }

    /**
     * Returns the unique identifier for this event (delegates to the wrapped job).
     *
     * @return the job's unique identifier
     */
    @Override
    public String uid() {
        return job.uid();
    }

    /**
     * Normalizes worker group key: null and empty string both represent the default group.
     */
    private static String normalizeWorkerGroup(@Nullable String workerGroup) {
        if (workerGroup == null || workerGroup.isEmpty()) {
            return null;
        }
        return workerGroup;
    }
}
