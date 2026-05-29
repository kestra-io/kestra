package io.kestra.controller.grpc.services;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.kestra.core.worker.QueueSubscription;
import io.kestra.controller.grpc.services.WorkerStreamContext.PendingJob;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Trivial capacity policy: a single shared pool of {@code maxConcurrency}
 * interchangeable slots, no per-queue accounting and no borrowing.
 *
 * <p>This is the default policy. Deployments that need per-queue reservations
 * or capacity borrowing replace the {@link WorkerCapacityPolicyFactory} bean
 * with one that returns a richer implementation.
 */
@ThreadSafe
final class SinglePoolCapacityPolicy implements WorkerCapacityPolicy {

    private final int maxConcurrency;
    private final AtomicInteger used = new AtomicInteger(0);

    SinglePoolCapacityPolicy(int maxConcurrency) {
        this.maxConcurrency = Math.max(0, maxConcurrency);
    }

    @Override
    public String tryReserve(String workerQueueId) {
        return tryIncrement(used, maxConcurrency) ? PendingJob.SHARED : null;
    }

    @Override
    public void release(String bucket) {
        if (bucket == null) {
            return;
        }
        used.updateAndGet(v -> Math.max(0, v - 1));
    }

    @Override
    public boolean hasCapacity(String workerQueueId) {
        return used.get() < maxConcurrency;
    }

    @Override
    public void replaceSubscriptions(List<QueueSubscription> subscriptions) {
        // No per-subscription state to refresh.
    }

    @Override
    public int allocated(String workerQueueId) {
        return 0;
    }

    @Override
    public int used(String workerQueueId) {
        return 0;
    }

    @Override
    public int sharedAllocated() {
        return maxConcurrency;
    }

    @Override
    public int sharedUsed() {
        return used.get();
    }

    private static boolean tryIncrement(AtomicInteger counter, int limit) {
        int current;
        do {
            current = counter.get();
            if (current >= limit) {
                return false;
            }
        } while (!counter.compareAndSet(current, current + 1));
        return true;
    }
}
