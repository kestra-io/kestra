package io.kestra.controller.grpc.services;

import java.util.List;

import io.kestra.core.worker.QueueSubscription;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Per-worker capacity policy: decides whether a slot is available for a job
 * heading to a given Worker Queue, and reserves/releases that slot.
 *
 * <p>Each {@link WorkerStreamContext} owns exactly one policy instance for its
 * lifetime. The policy is selected by a {@link WorkerCapacityPolicyFactory} at
 * stream connection time and never swapped.
 *
 * <p>The default implementation is {@link SinglePoolCapacityPolicy}:
 * {@code maxConcurrency} interchangeable slots, no per-queue accounting.
 * Deployments that need per-queue reservations or capacity borrowing replace
 * the {@link WorkerCapacityPolicyFactory} bean with one that returns a richer
 * policy.
 *
 * <p>All methods must be safe for concurrent invocation from multiple dispatch
 * threads on the same worker.
 */
@ThreadSafe
public interface WorkerCapacityPolicy {

    /**
     * Attempts to atomically reserve a slot for a job dispatched to
     * {@code workerQueueId}. Returns an opaque bucket token on success that
     * must later be handed back to {@link #release(String)}, or {@code null}
     * if no capacity is available.
     */
    String tryReserve(String workerQueueId);

    /**
     * Releases a slot previously acquired via {@link #tryReserve(String)}.
     * No-op if {@code bucket} is {@code null} or unknown to this policy.
     */
    void release(String bucket);

    /**
     * Returns {@code true} if a slot is currently available for a job heading
     * to {@code workerQueueId} — directly or by any policy-internal mechanism
     * (e.g. borrowing). The dispatcher uses this to decide whether to pause
     * a queue subscription.
     */
    boolean hasCapacity(String workerQueueId);

    /**
     * Reacts to a dynamic subscription change. In-flight slot reservations
     * must be preserved.
     */
    void replaceSubscriptions(List<QueueSubscription> subscriptions);

    /**
     * Number of slots guaranteed to {@code workerQueueId}. Used by the
     * capacity metrics publisher. Single-pool policies return 0.
     */
    int allocated(String workerQueueId);

    /**
     * Number of guaranteed slots currently used for {@code workerQueueId}.
     * Used by the capacity metrics publisher. Single-pool policies return 0.
     */
    int used(String workerQueueId);

    /**
     * Size of the shared (unreserved) pool. For single-pool policies this is
     * the entire {@code maxConcurrency}.
     */
    int sharedAllocated();

    /** Number of shared-pool slots currently used. */
    int sharedUsed();
}
