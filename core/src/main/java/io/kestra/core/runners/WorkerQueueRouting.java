package io.kestra.core.runners;

import java.util.List;

import io.kestra.core.models.tasks.WorkerQueueFallback;
import io.kestra.core.worker.WorkerQueues;

/**
 * Resolved Worker Queue routing decision for a Worker Job: where to dispatch the job
 * (the Worker Queue id), the source tags that drove the resolution (when tag-based),
 * the {@link WorkerQueueFallback} policy when no worker is available, and the
 * {@link Disposition} the caller must enact.
 *
 * <p>Returned by {@link io.kestra.core.services.WorkerQueueService#resolveWorkerQueueForJob}.
 * The resolver is responsible for the worker-availability check; callers switch on
 * {@link #disposition()} rather than re-checking availability.
 *
 * @param workerQueueId the resolved Worker Queue id; {@link WorkerQueues#DEFAULT_ID} for
 *                      {@link #toDefault()}, {@link WorkerQueues#SYSTEM_ID} for {@link #forSystem()}.
 * @param tags          the source tags that drove resolution; {@code null} or empty for
 *                      default and system routing. Useful for log/diagnostic messages.
 * @param fallback      the user-configured fallback policy; {@code null} when the routing
 *                      resolved successfully against an available worker. {@code null},
 *                      {@link WorkerQueueFallback#FAIL}, {@link WorkerQueueFallback#WAIT},
 *                      or {@link WorkerQueueFallback#CANCEL} only — {@link WorkerQueueFallback#IGNORE}
 *                      collapses to a default-queue {@link #toDefault()} routing (with
 *                      {@code fallback=null}) at resolution time and never appears here.
 * @param disposition   what the caller must do — never {@code null}.
 */
public record WorkerQueueRouting(String workerQueueId, List<String> tags, WorkerQueueFallback fallback, Disposition disposition) {

    /**
     * What the caller must do given the resolved routing. The resolver determines
     * this — callers do not call {@code hasActiveWorkerForQueue} themselves.
     */
    public enum Disposition {
        /** A worker is subscribed (or it is the SYSTEM/DEFAULT sentinel). Dispatch as-is. */
        DISPATCH,
        /** Queue exists but no worker is currently subscribed. Emit/hold; a worker will pick up later. */
        WAIT_AND_DISPATCH,
        /** No worker available. Caller must fail the job. */
        FAIL,
        /** No worker available. Caller must cancel the job. */
        CANCEL
    }

    /**
     * Sentinel routing that explicitly resolves to the global default queue.
     */
    public static WorkerQueueRouting toDefault() {
        return new WorkerQueueRouting(WorkerQueues.DEFAULT_ID, null, null, Disposition.DISPATCH);
    }

    /**
     * Sentinel routing for the reserved {@link WorkerQueues#SYSTEM_ID} queue.
     */
    public static WorkerQueueRouting forSystem() {
        return new WorkerQueueRouting(WorkerQueues.SYSTEM_ID, null, null, Disposition.DISPATCH);
    }

    /** Returns {@code true} when this routing refers to the global default queue. */
    public boolean isDefault() {
        return WorkerQueues.isDefault(workerQueueId);
    }

    /** Returns {@code true} when this routing refers to the reserved SYSTEM queue. */
    public boolean isSystem() {
        return WorkerQueues.SYSTEM_ID.equals(workerQueueId);
    }
}
