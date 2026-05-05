package io.kestra.core.runners;

import java.util.List;

import io.kestra.core.models.tasks.WorkerQueueFallback;
import io.kestra.core.worker.WorkerQueues;

/**
 * Resolved Worker Queue routing decision for a Worker Job: where to dispatch the job
 * (the Worker Queue id), the source tags that drove the resolution (when tag-based),
 * and the {@link WorkerQueueFallback} policy when no worker is available.
 *
 * <p>Returned by {@link io.kestra.core.services.WorkerQueueService#resolveWorkerQueueForJob}.
 * Two encodings convey "default queue":
 * <ul>
 *   <li>{@link Optional#empty()} from the resolver — no routing was configured at any
 *       level; dispatch to the default queue.</li>
 *   <li>{@link #toDefault()} returned by the resolver when configuration explicitly
 *       resolved to default (e.g. {@link WorkerQueueFallback#IGNORE} consumed at a level)
 *       — distinct from {@code Optional.empty()} so the resolver does not fall through
 *       to lower levels. Callers should use {@link #isDefault()} to detect this case.</li>
 * </ul>
 *
 * <p>{@link #forSystem()} is the parallel sentinel for the reserved {@link WorkerQueues#SYSTEM_ID}
 * queue served by the in-process SystemWorker. It is returned by the resolver for any
 * {@link io.kestra.core.models.tasks.SystemTask}, bypassing tag-based resolution.
 *
 * <p>Note: {@link WorkerQueueFallback#IGNORE} is a resolution-time directive only — the
 * resolver consumes it. {@code fallback} on a resolved routing will only ever be
 * {@code null}, {@link WorkerQueueFallback#FAIL}, {@link WorkerQueueFallback#WAIT}, or
 * {@link WorkerQueueFallback#CANCEL}.
 *
 * @param workerQueueId The resolved Worker Queue id; {@link WorkerQueues#DEFAULT_ID} when
 *                     produced by {@link #toDefault()}, {@link WorkerQueues#SYSTEM_ID} when
 *                     produced by {@link #forSystem()}.
 * @param tags         The source tags that drove resolution — the {@code workerSelector.tags}
 *                     on task/flow/trigger or the {@code tagsConfiguration.tags} on
 *                     namespace/tenant. {@code null} or empty for default and system
 *                     routing. Useful for log/diagnostic messages.
 * @param fallback     The fallback policy when no worker is available; {@code null}
 *                     when the call site does not need a fallback (e.g. routing
 *                     resolved successfully against an existing queue).
 */
public record WorkerQueueRouting(String workerQueueId, List<String> tags, WorkerQueueFallback fallback) {

    /**
     * Sentinel routing that explicitly resolves to the global default queue. Use this
     * (instead of {@link Optional#empty()}) when a level has consumed an
     * {@link WorkerQueueFallback#IGNORE} directive — the resolver must stop walking and
     * the executor must dispatch to the default queue without an availability check.
     */
    public static WorkerQueueRouting toDefault() {
        return new WorkerQueueRouting(WorkerQueues.DEFAULT_ID, null, null);
    }

    /**
     * Sentinel routing for the reserved {@link WorkerQueues#SYSTEM_ID} queue served by the
     * in-process SystemWorker. Returned by the resolver for any
     * {@link io.kestra.core.models.tasks.SystemTask} — tag-based resolution is bypassed.
     */
    public static WorkerQueueRouting forSystem() {
        return new WorkerQueueRouting(WorkerQueues.SYSTEM_ID, null, null);
    }

    /**
     * Returns {@code true} when this routing refers to the global default queue.
     */
    public boolean isDefault() {
        return WorkerQueues.isDefault(workerQueueId);
    }

    /**
     * Returns {@code true} when this routing refers to the reserved SYSTEM queue.
     */
    public boolean isSystem() {
        return WorkerQueues.SYSTEM_ID.equals(workerQueueId);
    }
}
