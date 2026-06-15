package io.kestra.controller.grpc.services;

import java.util.Set;

/**
 * SPI fired by {@link WorkerJobDispatcher} on worker stream lifecycle transitions.
 * <p>
 * Listeners are wired via constructor injection — they are discovered as
 * {@code List<WorkerLifecycleListener>} when the dispatcher is built, and
 * {@link #init(WorkerJobDispatcher)} is invoked once on each listener at the
 * end of the dispatcher's constructor. Listeners that need to read live
 * dispatcher state (e.g. {@link WorkerJobDispatcher#activeStreams()}) should
 * capture the reference there.
 * <p>
 * Subsequent callbacks are invoked synchronously on the thread that drives the
 * lifecycle change (typically the gRPC handler thread for register / unregister,
 * or the worker-group-sync thread for subscription changes). Implementations MUST
 * be cheap and non-blocking, and MUST NOT call back into the dispatcher in a way
 * that could re-enter the lifecycle path.
 */
public interface WorkerLifecycleListener {

    /**
     * Called exactly once at startup, at the end of the {@link WorkerJobDispatcher}
     * constructor, after all dispatcher state is wired but before any worker has
     * connected. Implementations that need to read live dispatcher state from
     * callback handlers should capture the reference here.
     */
    default void init(WorkerJobDispatcher dispatcher) {
    }

    /**
     * Fired after a worker stream has been fully registered in the dispatcher's
     * indices (active streams, worker group index, and per-queue indices).
     * <p>
     * The context is live at this point: {@code dispatcher.activeStreams()} will
     * return it.
     */
    default void onWorkerRegistered(WorkerStreamContext<?> context) {
    }

    /**
     * Fired after a worker stream has been fully unregistered from the dispatcher's
     * indices. The context is no longer reachable from
     * {@code dispatcher.activeStreams()}, but the {@code context} instance is still
     * readable so listeners can derive its group id, subscribed queues, etc.
     */
    default void onWorkerUnregistered(WorkerStreamContext<?> context) {
    }

    /**
     * Fired after a worker's queue subscriptions have been atomically swapped via
     * {@link WorkerJobDispatcher#reRegisterWorker} and the per-queue indices have
     * been updated. The {@code context} reflects the new subscription set.
     *
     * @param context the worker stream context, with its new subscriptions applied
     * @param added   normalized queue ids the worker now serves but didn't before
     * @param removed normalized queue ids the worker has stopped serving
     */
    default void onWorkerSubscriptionsChanged(WorkerStreamContext<?> context, Set<String> added, Set<String> removed) {
    }
}
