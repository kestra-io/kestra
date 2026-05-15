package io.kestra.controller.grpc.services;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.ThreadSafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.protobuf.ByteString;

import io.kestra.controller.grpc.WorkerJobPayload;
import io.kestra.controller.grpc.WorkerJobResponse;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.executor.WorkerJobRunningStateStore;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.worker.QueueSubscription;
import io.kestra.core.worker.WorkerQueues;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.runners.*;
import io.kestra.core.scheduler.events.TriggerEvaluated;
import io.kestra.core.scheduler.events.TriggerReceived;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.server.ClusterEvent;
import io.kestra.core.utils.Either;
import io.kestra.core.worker.MetadataChangePayload;
import io.kestra.core.worker.WorkerBroadcastEvent;

import io.micronaut.core.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Central coordinator for dispatching {@link WorkerJob} to workers using the pull/ack pattern.
 * <p>
 * This component:
 * <ul>
 * <li>Manages all active worker stream connections</li>
 * <li>Subscribes to the {@link KeyedDispatchQueueInterface} per Worker Queue</li>
 * <li>Dispatches jobs to workers based on their available permits</li>
 * <li>Uses pause/resume for backpressure when no workers have capacity</li>
 * </ul>
 * <p>
 * When no workers have capacity, the subscription is paused and the job is re-queued.
 * This ensures jobs are never lost even if the controller crashes, as they are
 * always either in the durable queue or in the WorkerJobRunningStateStore.
 * <p>
 * When all workers for a Worker Queue disconnect, the subscription is disposed immediately
 * to free resources. A new subscription will be created when workers reconnect.
 * <p>
 * Task Queues are optional. Workers without a Worker Queue (null or empty string) are assigned
 * to the default Worker Queue (represented as empty string internally).
 * <p>
 * Thread-safe: Operations are synchronized per Worker Queue to prevent race conditions
 * between job dispatching and worker registration/permit updates.
 */
@ThreadSafe
@Singleton
@Slf4j
public class WorkerJobDispatcher {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    // TTL for killed execution IDs in the local cache to prevent unbounded growth
    // This cache is used for pre-dispatch filtering of tasks belonging to killed executions avoiding unnecessary dispatch and allowing for faster kill response times.
    private static final Duration KILLED_CACHE_TTL = Duration.ofHours(24);

    private final KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue;
    private final WorkerJobRunningStateStore workerJobRunningStateStore;
    private final DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue;
    private final TriggerEventQueue triggerEventQueue;
    private final MetricRegistry metricRegistry;

    /**
     * Global closed flag to prevent operations after shutdown.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * All active worker stream connections, keyed by workerId.
     */
    private final ConcurrentHashMap<String, WorkerStreamContext<WorkerJobResponse>> activeStreams = new ConcurrentHashMap<>();

    /**
     * Secondary index: worker IDs by Worker Queue for O(1) Worker Queue lookups.
     * Key: workerQueueId (empty string for default Worker Queue), Value: set of worker IDs
     */
    private final ConcurrentHashMap<String, Set<String>> workerIdsByWorkerQueue = new ConcurrentHashMap<>();

    /**
     * Queue subscriptions and state per Worker Queue.
     * Key: workerQueueId (empty string for default Worker Queue)
     */
    private final ConcurrentHashMap<String, WorkerQueueState> workerQueueStates = new ConcurrentHashMap<>();

    /**
     * Secondary index: worker IDs by worker group ID.
     * Used for efficient lookup when a worker group's Worker Queue subscriptions change (dynamic reconfiguration).
     * Key: workerGroupId, Value: set of worker IDs
     */
    private final ConcurrentHashMap<String, Set<String>> workerIdsByWorkerGroup = new ConcurrentHashMap<>();

    /**
     * Cache of killed execution IDs for pre-dispatch filtering.
     */
    private final Cache<String, Boolean> killedExecutionIds = Caffeine.newBuilder()
        .expireAfterWrite(KILLED_CACHE_TTL)
        .build();

    /**
     * Subscription to the execution killed broadcast queue.
     */
    private final QueueSubscriber<ExecutionKilled> killQueueSubscriber;

    /**
     * Subscription to the cluster event broadcast queue.
     */
    private volatile QueueSubscriber<ClusterEvent> clusterEventSubscriber;

    /**
     * Listener that subscribes to metastore broadcast queues and forwards changes here.
     */
    private final MetadataChangeListener metadataChangeListener;

    private final WorkerQueueResolver workerQueueResolver;

    /**
     * Listeners notified on worker stream lifecycle transitions. Set once at
     * construction from Micronaut DI; never mutated afterwards.
     */
    private final List<WorkerLifecycleListener> lifecycleListeners;

    @Inject
    public WorkerJobDispatcher(
        KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue,
        WorkerJobRunningStateStore workerJobRunningStateStore,
        BroadcastQueueInterface<ExecutionKilled> executionKilledQueue,
        @Nullable BroadcastQueueInterface<ClusterEvent> clusterEventQueue,
        DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue,
        TriggerEventQueue triggerEventQueue,
        MetricRegistry metricRegistry,
        MetadataChangeListener metadataChangeListener,
        WorkerQueueResolver workerQueueResolver,
        List<WorkerLifecycleListener> lifecycleListeners) {
        this.workerJobEventQueue = workerJobEventQueue;
        this.workerJobRunningStateStore = workerJobRunningStateStore;
        this.workerTaskResultQueue = workerTaskResultQueue;
        this.triggerEventQueue = triggerEventQueue;
        this.metricRegistry = metricRegistry;
        this.metadataChangeListener = metadataChangeListener;
        this.workerQueueResolver = workerQueueResolver;
        this.lifecycleListeners = List.copyOf(lifecycleListeners);

        // Construct broadcast subscribers and gauges with cleanup on partial failure.
        // @PreDestroy is not invoked when bean construction fails, so any subscriber that has
        // already been attached to a broadcast queue must be closed locally on the failure path.
        try {
            // Subscribe to execution killed events
            this.killQueueSubscriber = executionKilledQueue.subscriber();
            this.killQueueSubscriber.subscribe(either ->
            {
                if (either.isRight()) {
                    log.error("Deserialization error for ExecutionKilled: {}", either.getRight().getMessage());
                    return;
                }
                ExecutionKilled killed = either.getLeft();
                if (killed.getState() == ExecutionKilled.State.EXECUTED) {
                    onExecutionKilled(killed);
                }
            });

            // Subscribe to cluster events to forward them to gRPC workers
            if (clusterEventQueue != null) {
                this.clusterEventSubscriber = clusterEventQueue.subscriber();
                this.clusterEventSubscriber.subscribe(either ->
                {
                    if (either.isRight()) {
                        log.error("Deserialization error for ClusterEvent: {}", either.getRight().getMessage());
                        return;
                    }
                    onClusterEvent(either.getLeft());
                });
            }

            this.metadataChangeListener.start(this::onMetadataChanged);

            // Register global gauges
            this.metricRegistry.gauge(
                MetricRegistry.METRIC_CONTROLLER_WORKER_ACTIVE_ALL,
                MetricRegistry.METRIC_CONTROLLER_WORKER_ACTIVE_ALL_DESCRIPTION,
                (Supplier<Integer>) activeStreams::size
            );
            this.metricRegistry.gauge(
                MetricRegistry.METRIC_CONTROLLER_PERMITS_AVAILABLE_ALL,
                MetricRegistry.METRIC_CONTROLLER_PERMITS_AVAILABLE_ALL_DESCRIPTION,
                (Supplier<Integer>) () -> activeStreams.values().stream()
                    .mapToInt(WorkerStreamContext::getAvailablePermits)
                    .sum()
            );

            // Notify lifecycle listeners that the dispatcher is fully wired. No worker can
            // register yet — the gRPC service that drives registerWorker is built after this
            // constructor returns — so listeners are safe to capture {@code this} here.
            for (WorkerLifecycleListener listener : this.lifecycleListeners) {
                safelyInvoke(() -> listener.init(this), "init");
            }
        } catch (Throwable t) {
            try {
                metadataChangeListener.stop();
            } catch (Exception e) {
                log.warn("Error stopping metadata change listener during failure recovery: {}", e.getMessage());
            }
            closeBroadcastSubscribersQuietly();
            throw t;
        }
    }

    /**
     * Closes the broadcast subscribers (kill queue, cluster event queue) ignoring any errors.
     * Used both on construction failure and during {@link #close()}.
     */
    private void closeBroadcastSubscribersQuietly() {
        if (killQueueSubscriber != null) {
            try {
                killQueueSubscriber.close();
            } catch (Exception e) {
                log.warn("Error closing kill queue subscription: {}", e.getMessage());
            }
        }
        if (clusterEventSubscriber != null) {
            try {
                clusterEventSubscriber.close();
            } catch (Exception e) {
                log.warn("Error closing cluster event queue subscription: {}", e.getMessage());
            }
        }
    }

    /**
     * Handles an execution killed event from the broadcast queue.
     * Adds to the local cache and broadcasts to all connected workers.
     */
    private void onExecutionKilled(ExecutionKilled killed) {
        if (killed instanceof ExecutionKilledExecution killedExecution) {
            killedExecutionIds.put(killedExecution.getExecutionId(), Boolean.TRUE);
            log.info("Received execution killed event for execution '{}'", killedExecution.getExecutionId());
        }

        broadcastEvent(new WorkerBroadcastEvent.KillEvent(killed), "kill command");
    }

    /**
     * Cluster event types that should NOT be forwarded to gRPC workers.
     */
    private static final Set<ClusterEvent.EventType> EXCLUDED_EVENT_TYPES = Set.of(
        ClusterEvent.EventType.MAINTENANCE_ENTER,
        ClusterEvent.EventType.MAINTENANCE_EXIT,
        ClusterEvent.EventType.KILL_SWITCH_SYNC_REQUESTED,
        ClusterEvent.EventType.WORKER_GROUP_SYNC_REQUESTED
    );

    /**
     * Handles a cluster event from the broadcast queue and forwards it to all connected workers.
     * Maintenance and executor-only events are excluded.
     */
    private void onClusterEvent(ClusterEvent event) {
        if (event.eventType() == ClusterEvent.EventType.WORKER_GROUP_SYNC_REQUESTED) {
            onWorkerGroupSync(event.message());
            return;
        }
        if (EXCLUDED_EVENT_TYPES.contains(event.eventType())) {
            log.debug("Skipping cluster event not relevant to workers: type={}", event.eventType());
            return;
        }
        log.info("Received cluster event: type={}, message={}", event.eventType(), event.message());
        broadcastEvent(new WorkerBroadcastEvent.ClusterBroadcast(event), "cluster event");
    }

    /**
     * Broadcasts a {@link WorkerBroadcastEvent} to all connected workers via the events field of
     * the existing long-lived gRPC stream. Used both for internally-sourced events (kill, cluster)
     * and by EE publishers that subscribe to additional broadcast queues (e.g. metastore changes).
     */
    public void broadcastToAllWorkers(WorkerBroadcastEvent event) {
        broadcastEvent(event, event.getClass().getSimpleName());
    }

    /**
     * Broadcasts a metastore metadata change to every connected worker so worker-side caches can
     * invalidate the affected entries. Convenience wrapper around {@link #broadcastToAllWorkers}.
     */
    public void onMetadataChanged(MetadataChangePayload payload) {
        broadcastToAllWorkers(new WorkerBroadcastEvent.MetadataChangeEvent(payload));
    }

    /**
     * Handles a worker group sync event by re-registering all workers in that worker group
     * with the updated Worker Queue subscriptions.
     */
    private void onWorkerGroupSync(String workerGroupId) {
        Set<String> workerIds = getWorkerIdsByWorkerGroup(workerGroupId);
        if (workerIds.isEmpty()) {
            log.debug("No active workers for worker group '{}', nothing to re-register", workerGroupId);
            return;
        }

        List<QueueSubscription> newSubscriptions = workerQueueResolver.resolve(workerGroupId);
        log.info(
            "Worker group '{}' updated, re-registering {} worker(s) with {} subscription(s)",
            workerGroupId, workerIds.size(), newSubscriptions.size()
        );

        for (String workerId : workerIds) {
            try {
                reRegisterWorker(workerId, newSubscriptions);
            } catch (Exception e) {
                log.error(
                    "Failed to re-register worker '{}' for worker group '{}': {}",
                    workerId, workerGroupId, e.getMessage(), e
                );
            }
        }
    }

    /**
     * Broadcasts a {@link WorkerBroadcastEvent} to all connected workers via the events field.
     */
    private void broadcastEvent(WorkerBroadcastEvent event, String description) {
        ByteString eventData = MessageFormats.JSON.toByteString(event);

        activeStreams.forEach((workerId, context) ->
        {
            try {
                WorkerJobResponse response = WorkerJobResponse.newBuilder()
                    .setHeader(RequestOrResponseHeaderFactory.create(workerId))
                    .addEvents(eventData)
                    .build();
                context.sendResponse(response);
                log.debug("Broadcast {} to worker {}", description, workerId);
            } catch (Exception e) {
                log.warn("Failed to send {} to worker {}: {}", description, workerId, e.getMessage());
            }
        });
    }

    /**
     * Registers a new worker stream connection.
     * Creates queue subscriptions for all Task Queues this worker is subscribed to.
     *
     * @param context the worker stream context
     * @throws IllegalStateException if the dispatcher is closed
     */
    public void registerWorker(WorkerStreamContext<WorkerJobResponse> context) {
        checkNotClosed();

        // Add to global index
        activeStreams.put(context.getWorkerId(), context);

        // Track every worker (including those in the default group) in the worker-group
        // index so that subscription changes can trigger a re-registration via
        // {@code WORKER_GROUP_SYNC_REQUESTED}. EE persists a configurable default
        // worker group, so the previous "default is immutable" assumption no longer
        // holds — workers in that group must also be reachable for reconfiguration.
        String workerGroupId = context.getWorkerGroupId();
        workerIdsByWorkerGroup.computeIfAbsent(workerGroupId, k -> ConcurrentHashMap.newKeySet())
            .add(context.getWorkerId());

        // Register in each subscribed Worker Queue, acquiring locks in consistent order.
        // Retry on disposed-state races, but cap so a real bug can't spin forever.
        for (String workerQueueId : sortedWorkerQueueKeys(context)) {
            int attempts = 0;
            while (!tryRegisterInWorkerQueue(context, workerQueueId)) {
                if (++attempts >= REGISTER_MAX_RETRIES) {
                    throw new IllegalStateException(
                        "Could not register worker '" + context.getWorkerId()
                            + "' in Worker Queue '" + workerQueueId
                            + "' after " + attempts + " retries (concurrent dispose)"
                    );
                }
            }
        }

        fireWorkerRegistered(context);
    }

    private static final int REGISTER_MAX_RETRIES = 50;

    /**
     * Attempts to register a worker in a single Worker Queue against the current {@link WorkerQueueState}.
     * Returns {@code false} if the state was disposed concurrently and the caller must retry.
     * Throws {@link IllegalStateException} if the dispatcher is closed.
     */
    private boolean tryRegisterInWorkerQueue(WorkerStreamContext<WorkerJobResponse> context, String workerQueueId) {
        WorkerQueueState state = getOrCreateWorkerQueueState(workerQueueId);
        state.lock.lock();
        try {
            if (closed.get()) {
                // close() may have finished iterating workerQueueStates before we created this state.
                // Take responsibility for closing the subscriber to avoid a leak.
                if (workerQueueStates.remove(workerQueueId, state)) {
                    closeSubscriberQuietly(state.subscriber(), workerQueueId);
                    removeWorkerQueueGauges(workerQueueId);
                }
                throw new IllegalStateException("WorkerJobDispatcher is closed");
            }
            if (workerQueueStates.get(workerQueueId) != state) {
                // Concurrent unregisterWorker(last) disposed this state while we waited for the lock.
                return false;
            }

            workerIdsByWorkerQueue.computeIfAbsent(workerQueueId, k -> ConcurrentHashMap.newKeySet())
                .add(context.getWorkerId());

            log.info("Registered worker {} for Worker Queue '{}'", context.getWorkerId(), WorkerQueues.forLog(workerQueueId));
            metricRegistry.counter(
                MetricRegistry.METRIC_CONTROLLER_WORKER_REGISTERED_TOTAL,
                MetricRegistry.METRIC_CONTROLLER_WORKER_REGISTERED_TOTAL_DESCRIPTION,
                metricRegistry.workerGroupAndQueueTags(context.getWorkerGroupId(), workerQueueId)
            ).increment();

            // Resume subscription if worker has permits
            if (context.getAvailablePermits() > 0) {
                resumeSubscription(state, workerQueueId);
            }
            return true;
        } finally {
            state.lock.unlock();
        }
    }

    /**
     * Gets an existing Worker Queue state or creates a new one atomically.
     */
    private WorkerQueueState getOrCreateWorkerQueueState(String workerQueueId) {
        return workerQueueStates.computeIfAbsent(workerQueueId, this::createWorkerQueueState);
    }

    /**
     * Creates a new Worker Queue state with subscription for a Worker Queue.
     */
    private WorkerQueueState createWorkerQueueState(String workerQueueId) {
        String workerQueueIdOrNull = workerQueueId.isEmpty() ? null : workerQueueId;
        QueueSubscriber<WorkerJobEvent> subscriber = workerJobEventQueue.subscriber(workerQueueIdOrNull);
        try {
            subscriber.pause(); // Start paused until workers connect with permits
            subscriber.subscribe(either -> handleIncomingJob(workerQueueId, either));
            log.info("Created queue subscription for Worker Queue '{}' (initially paused)", WorkerQueues.forLog(workerQueueId));

            // Queue-scoped gauges: a Worker Queue can be served by workers from multiple
            // Worker Groups, so worker_group is not a meaningful dimension here.
            String[] metricTags = metricRegistry.workerQueueTags(workerQueueId);
            metricRegistry.gauge(
                MetricRegistry.METRIC_CONTROLLER_WORKER_ACTIVE,
                MetricRegistry.METRIC_CONTROLLER_WORKER_ACTIVE_DESCRIPTION,
                (Supplier<Integer>) () -> {
                    Set<String> ids = workerIdsByWorkerQueue.get(workerQueueId);
                    return ids == null ? 0 : ids.size();
                },
                metricTags
            );
            metricRegistry.gauge(
                MetricRegistry.METRIC_CONTROLLER_PERMITS_AVAILABLE,
                MetricRegistry.METRIC_CONTROLLER_PERMITS_AVAILABLE_DESCRIPTION,
                (Supplier<Integer>) () -> getTotalPermitsForWorkerQueue(workerQueueId),
                metricTags
            );
            metricRegistry.gauge(
                MetricRegistry.METRIC_CONTROLLER_JOB_INFLIGHT,
                MetricRegistry.METRIC_CONTROLLER_JOB_INFLIGHT_DESCRIPTION,
                (Supplier<Integer>) () -> getWorkersInWorkerQueue(workerQueueId).mapToInt(WorkerStreamContext::getInFlightCount).sum(),
                metricTags
            );

            return new WorkerQueueState(subscriber);
        } catch (Throwable t) {
            // The subscriber has been allocated but is not yet owned by a WorkerQueueState in workerQueueStates.
            // Close it here; nothing else will.
            closeSubscriberQuietly(subscriber, workerQueueId);
            throw t;
        }
    }

    /**
     * Unregisters a worker when the stream disconnects.
     * Removes it from all subscribed Task Queues. If it was the last worker for a Worker Queue, disposes the subscription immediately.
     * <p>
     * This method is scoped to a specific stream context: it only removes the registration
     * if the given {@code context} is still the one currently registered for the worker id.
     * This prevents a late-firing {@code onError}/{@code onCancel} callback for a stale
     * stream (e.g., after an HTTP/2 GOAWAY reconnect) from wiping out a fresh registration
     * the same worker has already established on a new stream.
     *
     * @param context the worker stream context whose stream has closed
     */
    public void unregisterWorker(WorkerStreamContext<WorkerJobResponse> context) {
        Objects.requireNonNull(context, "context must not be null");

        String workerId = context.getWorkerId();

        // Atomic compare-and-remove: only proceed if THIS context is still the registered one.
        // A superseded stale stream must not remove a newer registration for the same workerId.
        if (!activeStreams.remove(workerId, context)) {
            log.debug("Ignoring stale unregister for worker [{}]: stream already superseded or unknown", workerId);
            return;
        }

        // Release bucket reservations held by any job still in-flight on this stream.
        // The worker's still-running tasks will deliver their results via
        // sendWorkerTaskResults independently; on this controller they simply stop
        // counting toward bucket usage.
        context.releaseAllInFlightBuckets();

        // Remove from worker group index (mirror of registerWorker — default group included)
        String workerGroupId = context.getWorkerGroupId();
        Set<String> workerGroupWorkers = workerIdsByWorkerGroup.get(workerGroupId);
        if (workerGroupWorkers != null) {
            workerGroupWorkers.remove(workerId);
            if (workerGroupWorkers.isEmpty()) {
                workerIdsByWorkerGroup.remove(workerGroupId);
            }
        }

        // Remove from each subscribed Worker Queue, acquiring locks in consistent order
        for (String workerQueueId : sortedWorkerQueueKeys(context)) {
            WorkerQueueState state = workerQueueStates.get(workerQueueId);
            if (state == null) {
                continue;
            }

            state.lock.lock();
            try {
                // If the same worker id has been re-registered on a new stream (HTTP/2
                // GOAWAY reconnect) and the new context still subscribes to this Task
                // Queue, the new stream owns the registration and we must leave it alone.
                WorkerStreamContext<WorkerJobResponse> current = activeStreams.get(workerId);
                if (current != null && current.subscribedWorkerQueueIds().contains(workerQueueId)) {
                    log.debug("Skipping unregister of worker [{}] from Worker Queue '{}': replaced by fresh registration",
                        workerId, WorkerQueues.forLog(workerQueueId));
                    continue;
                }

                Set<String> workerQueueWorkers = workerIdsByWorkerQueue.get(workerQueueId);
                if (workerQueueWorkers != null) {
                    workerQueueWorkers.remove(workerId);
                }

                log.info("Unregistered worker {} from Worker Queue '{}', had {} in-flight jobs", workerId, WorkerQueues.forLog(workerQueueId), context.getInFlightCount());
                metricRegistry.counter(
                    MetricRegistry.METRIC_CONTROLLER_WORKER_UNREGISTERED_TOTAL,
                    MetricRegistry.METRIC_CONTROLLER_WORKER_UNREGISTERED_TOTAL_DESCRIPTION,
                    metricRegistry.workerGroupAndQueueTags(context.getWorkerGroupId(), workerQueueId)
                ).increment();

                int remainingWorkers = workerQueueWorkers == null ? 0 : workerQueueWorkers.size();

                if (remainingWorkers == 0) {
                    // No more workers - dispose immediately.
                    // ORDER MATTERS: keep this state in workerQueueStates until after the subscriber and
                    // gauges are torn down. While the entry remains, a concurrent registerWorker
                    // for this Worker Queue will block on state.lock instead of creating a parallel
                    // WorkerQueueState — which would race with our cleanup of workerIdsByWorkerQueue/gauges.
                    log.info("Disposing subscription for Worker Queue '{}': no workers remaining", WorkerQueues.forLog(workerQueueId));
                    workerIdsByWorkerQueue.remove(workerQueueId);
                    closeSubscriberQuietly(state.subscriber(), workerQueueId);
                    removeWorkerQueueGauges(workerQueueId);
                    workerQueueStates.remove(workerQueueId, state);
                } else if (!hasAnyPermitsInWorkerQueue(workerQueueId)) {
                    // Workers exist but no permits - pause
                    pauseSubscription(state, workerQueueId);
                }
            } finally {
                state.lock.unlock();
            }
        }

        fireWorkerUnregistered(context);
    }

    /**
     * Handles new permits received from a worker.
     * The permits value represents the worker's total remaining capacity.
     * Resumes queue subscriptions for all Task Queues this worker serves if capacity is available.
     *
     * @param context the worker stream context
     * @param newPermits the worker's total remaining capacity (0 or more)
     */
    public void onPermitsReceived(WorkerStreamContext<WorkerJobResponse> context, int newPermits) {
        if (closed.get() || newPermits < 0) {
            return;
        }

        // Verify worker is still registered
        if (!activeStreams.containsKey(context.getWorkerId())) {
            return;
        }

        context.setPermits(newPermits);

        log.trace("Permits received: worker {}, permits={}", context.getWorkerId(), newPermits);
        reEvaluateSubscriptions(context);
    }

    /**
     * Handles terminal-state notifications from a worker. Releases each job's
     * reserved bucket slot so per-queue capacity reservations hold across the
     * whole job lifetime, and re-evaluates pause/resume for every Worker Queue
     * this worker serves so a previously-paused subscription resumes immediately
     * when slots free.
     *
     * @param context the worker stream context
     * @param jobIds list of job UIDs that reached a terminal state
     */
    public void onCompletionsReceived(WorkerStreamContext<WorkerJobResponse> context, List<String> jobIds) {
        for (String jobId : jobIds) {
            context.completeJob(jobId);
        }
        log.debug("Worker {} signaled completion of {} jobs", context.getWorkerId(), jobIds.size());

        if (closed.get()) {
            return;
        }
        reEvaluateSubscriptions(context);
    }

    /**
     * Re-evaluates pause/resume for every Worker Queue the worker serves. Call after
     * any change that could have affected dispatch eligibility (permit update or
     * bucket release via ACK).
     */
    private void reEvaluateSubscriptions(WorkerStreamContext<WorkerJobResponse> context) {
        for (String workerQueueId : sortedWorkerQueueKeys(context)) {
            WorkerQueueState state = workerQueueStates.get(workerQueueId);
            if (state == null) {
                continue;
            }

            state.lock.lock();
            try {
                if (hasAnyPermitsInWorkerQueue(workerQueueId)) {
                    resumeSubscription(state, workerQueueId);
                } else {
                    pauseSubscription(state, workerQueueId);
                }
            } finally {
                state.lock.unlock();
            }
        }
    }

    /**
     * Handles a job event received from the queue.
     * <p>
     * If a worker with permits is available, the job is dispatched immediately.
     * If no worker has capacity, the job is re-queued and the subscription is paused.
     */
    private void handleIncomingJob(String workerQueueId, Either<WorkerJobEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Deserialization error for job in Worker Queue '{}': {}", workerQueueId, either.getRight().getMessage());
            handleDeserializationError(either.getRight());
            return;
        }

        WorkerJobEvent event = either.getLeft();
        WorkerJob job = event.job();

        // Pre-dispatch killed check: if the execution is already killed, produce a KILLED result directly
        if (job instanceof WorkerTask workerTask) {
            String executionId = workerTask.getTaskRun().getExecutionId();
            if (
                !Boolean.TRUE.equals(workerTask.getTaskRun().getForceExecution())
                    && killedExecutionIds.getIfPresent(executionId) != null
            ) {
                log.info("Skipping dispatch of task '{}' for killed execution '{}'", job.uid(), executionId);
                metricRegistry.counter(
                    MetricRegistry.METRIC_CONTROLLER_JOB_KILLED_TOTAL,
                    MetricRegistry.METRIC_CONTROLLER_JOB_KILLED_TOTAL_DESCRIPTION,
                    metricRegistry.workerQueueTags(workerQueueId)
                ).increment();
                try {
                    workerTaskResultQueue.emit(new WorkerTaskResult(workerTask.getTaskRun().withState(State.Type.KILLED)));
                } catch (QueueException e) {
                    log.error("Failed to emit KILLED result for task '{}': {}", job.uid(), e.getMessage(), e);
                }
                return;
            }
        }

        WorkerQueueState state = workerQueueStates.get(workerQueueId);
        if (state == null) {
            log.error("No state for Worker Queue '{}', re-queuing job {}", WorkerQueues.forLog(workerQueueId), job.uid());
            requeue(event);
            return;
        }

        state.lock.lock();
        try {
            // Find a worker with permits and atomically reserve a bucket slot
            Optional<ReservedSlot> reserved = findAndReserveWorker(workerQueueId);

            if (reserved.isPresent()) {
                ReservedSlot slot = reserved.get();
                dispatchJobToWorker(slot.context(), job, event, workerQueueId, slot.bucket());

                // Check if we should pause after this dispatch (no more capacity)
                if (!hasAnyPermitsInWorkerQueue(workerQueueId)) {
                    pauseSubscription(state, workerQueueId);
                }
            } else {
                // No worker with capacity - pause subscription and re-queue job
                pauseSubscription(state, workerQueueId);
                log.debug("No workers with permits for Worker Queue '{}', re-queuing job {}", WorkerQueues.forLog(workerQueueId), job.uid());
                metricRegistry.counter(
                    MetricRegistry.METRIC_CONTROLLER_JOB_REQUEUED_TOTAL,
                    MetricRegistry.METRIC_CONTROLLER_JOB_REQUEUED_TOTAL_DESCRIPTION,
                    metricRegistry.workerQueueTags(workerQueueId)
                ).increment();
                requeue(event);
            }
        } finally {
            state.lock.unlock();
        }
    }

    private void handleDeserializationError(DeserializationException deserializationException) {
        if (deserializationException.getRecord() != null) {
            try {
                var json = MAPPER.readTree(deserializationException.getRecord());
                var job = json.get("job");
                if (job != null) {
                    var type = job.get("type") != null ? job.get("type").asText() : null;
                    if ("task".equals(type)) {
                        // try to deserialize the taskRun to fail it
                        var taskRun = MAPPER.treeToValue(job.get("taskRun"), TaskRun.class);
                        this.workerTaskResultQueue.emit(new WorkerTaskResult(taskRun.fail()));
                    } else if ("trigger".equals(type)) {
                        // try to deserialize the triggerContext to fail it
                        var triggerContext = MAPPER.treeToValue(job.get("triggerContext"), TriggerContext.class);
                        var workerTriggerResult = new TriggerEvaluated(
                            TriggerId.of(triggerContext.getTenantId(), triggerContext.getNamespace(), triggerContext.getFlowId(), triggerContext.getTriggerId()), null
                        );
                        this.triggerEventQueue.send(workerTriggerResult);
                    }
                }
            } catch (IOException | QueueException e) {
                // ignore the message if we cannot do anything about it
                log.error("Unexpected exception when trying to handle a deserialization error", e);
            }
        }
    }

    /**
     * Returns a snapshot of all active worker stream contexts. Iteration is safe; the
     * returned collection is an unmodifiable view backed by a {@link ConcurrentHashMap}
     * whose iterators are weakly consistent.
     */
    public Collection<WorkerStreamContext<WorkerJobResponse>> activeStreams() {
        return Collections.unmodifiableCollection(activeStreams.values());
    }

    /**
     * Gets workers in a specific Worker Queue using the secondary index.
     */
    private Stream<WorkerStreamContext<WorkerJobResponse>> getWorkersInWorkerQueue(String workerQueueId) {
        Set<String> workerIds = workerIdsByWorkerQueue.get(workerQueueId);
        if (workerIds == null || workerIds.isEmpty()) {
            return Stream.empty();
        }
        return workerIds.stream()
            .map(activeStreams::get)
            .filter(Objects::nonNull);
    }

    /**
     * Checks if any worker in the Worker Queue has both available permits and bucket capacity.
     */
    private boolean hasAnyPermitsInWorkerQueue(String workerQueueId) {
        return getWorkersInWorkerQueue(workerQueueId)
            .anyMatch(ctx -> ctx.getAvailablePermits() > 0 && ctx.hasCapacityForQueue(workerQueueId));
    }

    /**
     * Finds a worker with capacity for the Worker Queue and atomically reserves both a
     * permit and a bucket slot. Preference: least-loaded worker (fewest in-flight).
     * Must be called while holding the Worker Queue lock.
     * <p>
     * The permit is consumed first via CAS (so a worker serving multiple Task Queues
     * cannot be over-dispatched when dispatch threads on different Worker Queue locks
     * race). If the bucket reservation then fails, the permit is restored.
     * <p>
     * Eligible candidates are iterated in priority order so a CAS race lost on the
     * preferred worker (concurrent dispatch from another Worker Queue stole the permit
     * or last bucket slot) falls through to the next candidate instead of pausing
     * the subscription.
     *
     * @return a {@link ReservedSlot} if both a permit and a bucket were reserved, empty otherwise
     */
    private Optional<ReservedSlot> findAndReserveWorker(String workerQueueId) {
        List<WorkerStreamContext<WorkerJobResponse>> candidates = getWorkersInWorkerQueue(workerQueueId)
            .filter(ctx -> ctx.getAvailablePermits() > 0 && ctx.hasCapacityForQueue(workerQueueId))
            .sorted(Comparator.comparingInt(WorkerStreamContext::getInFlightCount))
            .toList();

        for (WorkerStreamContext<WorkerJobResponse> ctx : candidates) {
            if (!ctx.tryConsumePermit()) {
                continue;
            }
            String bucket = ctx.tryReserveBucket(workerQueueId);
            if (bucket == null) {
                ctx.addPermits(1);
                continue;
            }
            return Optional.of(new ReservedSlot(ctx, bucket));
        }
        return Optional.empty();
    }

    /**
     * A worker that has reserved a slot in a specific bucket for an incoming job.
     */
    private record ReservedSlot(WorkerStreamContext<WorkerJobResponse> context, String bucket) {}

    /**
     * Dispatches a job to a worker.
     * <p>
     * CRITICAL: The job is persisted to WorkerJobRunningStateStore BEFORE sending.
     * If sending fails, the permit is restored and the job is re-queued.
     *
     * @param context the worker stream context
     * @param job the worker job
     * @param originalEvent the original job event (for re-queuing on failure)
     * @param dispatchWorkerQueueId the Worker Queue this job was dispatched for
     */
    private void dispatchJobToWorker(WorkerStreamContext<WorkerJobResponse> context, WorkerJob job,
        WorkerJobEvent originalEvent, String dispatchWorkerQueueId, String bucket) {
        String jobId = job.uid();

        // The permit and bucket slot were already atomically reserved in findAndReserveWorker.

        // 1. PERSIST before sending (critical for recovery)
        persistJobToStateStore(context, job, dispatchWorkerQueueId);

        // 2. Track in-flight locally
        context.trackInFlight(jobId, job, bucket);

        // 3. Send to worker
        try {
            WorkerJobResponse response = WorkerJobResponse.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(context.getWorkerId()))
                .addJobs(
                    WorkerJobPayload.newBuilder()
                        .setJobId(jobId)
                        .setJobData(MessageFormats.JSON.toByteString(job))
                        .build()
                )
                .build();

            context.sendResponse(response);
            log.debug("Dispatched job {} to worker {}", jobId, context.getWorkerId());
            metricRegistry.counter(
                MetricRegistry.METRIC_CONTROLLER_JOB_DISPATCHED_TOTAL,
                MetricRegistry.METRIC_CONTROLLER_JOB_DISPATCHED_TOTAL_DESCRIPTION,
                metricRegistry.workerGroupAndQueueTags(context.getWorkerGroupId(), dispatchWorkerQueueId)
            ).increment();

        } catch (Exception e) {
            log.error("Failed to send job {} to worker {}: {}", jobId, context.getWorkerId(), e.getMessage());
            handleDispatchFailure(context, job, originalEvent, dispatchWorkerQueueId, bucket);
        }
    }

    /**
     * Persists a job to the state store for recovery.
     *
     * @param context the worker stream context
     * @param job the worker job
     * @param dispatchWorkerQueueId the Worker Queue this job was dispatched for
     */
    private void persistJobToStateStore(WorkerStreamContext<WorkerJobResponse> context, WorkerJob job, String dispatchWorkerQueueId) {
        WorkerInstance workerInstance = new WorkerInstance(context.getWorkerId(), dispatchWorkerQueueId.isEmpty() ? null : dispatchWorkerQueueId);
        if (job instanceof WorkerTask workerTask) {
            workerJobRunningStateStore.save(NoTransactionContext.INSTANCE, WorkerTaskRunning.of(workerTask, workerInstance));
        } else if (job instanceof WorkerTrigger workerTrigger) {
            workerJobRunningStateStore.save(NoTransactionContext.INSTANCE, WorkerTriggerRunning.of(workerTrigger, workerInstance));
            triggerEventQueue.send(new TriggerReceived(workerTrigger.triggerId(), context.getWorkerId()));
        }
    }

    /**
     * Handles failure to send a job to a worker.
     * Restores the permit and re-queues the job.
     */
    private void handleDispatchFailure(WorkerStreamContext<WorkerJobResponse> context, WorkerJob job,
        WorkerJobEvent originalEvent, String dispatchWorkerQueueId, String bucket) {
        metricRegistry.counter(
            MetricRegistry.METRIC_CONTROLLER_JOB_DISPATCH_FAILED_TOTAL,
            MetricRegistry.METRIC_CONTROLLER_JOB_DISPATCH_FAILED_TOTAL_DESCRIPTION,
            metricRegistry.workerGroupAndQueueTags(context.getWorkerGroupId(), dispatchWorkerQueueId)
        ).increment();

        // Restore permit to the worker
        context.addPermits(1);

        // Remove from in-flight tracking and release the reserved bucket — the job
        // never reached the worker, so the slot must return to the pool immediately.
        context.completeJob(job.uid());

        // Delete from state store
        workerJobRunningStateStore.deleteByKey(NoTransactionContext.INSTANCE, job.uid());

        // Re-queue the job
        requeue(originalEvent);
    }

    /**
     * Re-queues a job event back to the queue.
     */
    private void requeue(WorkerJobEvent event) {
        try {
            workerJobEventQueue.emit(event.key(), event);
        } catch (QueueException e) {
            log.error("Failed to re-queue job {}: {}", event.job().uid(), e.getMessage(), e);
            throw new RuntimeException("Failed to re-queue job", e);
        }
    }

    private void pauseSubscription(WorkerQueueState state, String workerQueueId) {
        if (state.isPaused.compareAndSet(false, true)) {
            state.subscriber.pause();
            log.info("Paused subscription for Worker Queue '{}'", WorkerQueues.forLog(workerQueueId));
            metricRegistry.counter(
                MetricRegistry.METRIC_CONTROLLER_SUBSCRIPTION_PAUSED_TOTAL,
                MetricRegistry.METRIC_CONTROLLER_SUBSCRIPTION_PAUSED_TOTAL_DESCRIPTION,
                metricRegistry.workerQueueTags(workerQueueId)
            ).increment();
        }
    }

    private void resumeSubscription(WorkerQueueState state, String workerQueueId) {
        if (state.isPaused.compareAndSet(true, false)) {
            state.subscriber.resume();
            log.info("Resumed subscription for Worker Queue '{}'", WorkerQueues.forLog(workerQueueId));
            metricRegistry.counter(
                MetricRegistry.METRIC_CONTROLLER_SUBSCRIPTION_RESUMED_TOTAL,
                MetricRegistry.METRIC_CONTROLLER_SUBSCRIPTION_RESUMED_TOTAL_DESCRIPTION,
                metricRegistry.workerQueueTags(workerQueueId)
            ).increment();
        }
    }

    private void closeSubscriberQuietly(QueueSubscriber<WorkerJobEvent> subscriber, String workerQueueId) {
        try {
            subscriber.close();
        } catch (Exception e) {
            log.warn("Error closing subscription for Worker Queue '{}': {}", WorkerQueues.forLog(workerQueueId), e.getMessage());
        }
    }

    private void removeWorkerQueueGauges(String workerQueueId) {
        String workerQueueTag = WorkerQueues.normalize(workerQueueId);
        for (String metricName : List.of(
            MetricRegistry.METRIC_CONTROLLER_WORKER_ACTIVE,
            MetricRegistry.METRIC_CONTROLLER_PERMITS_AVAILABLE,
            MetricRegistry.METRIC_CONTROLLER_JOB_INFLIGHT)) {
            metricRegistry.find(metricName)
                .tag(MetricRegistry.TAG_WORKER_QUEUE, workerQueueTag)
                .gauges()
                .forEach(metricRegistry::removeMeter);
        }
    }

    /**
     * Re-registers a worker with new Worker Queue subscriptions (for dynamic reconfiguration).
     * Unregisters the worker from its old Task Queues and registers it in the new ones,
     * updating the Worker Queue maps under per-Task-Queue locks before switching the context's
     * subscriptions so that concurrent dispatch threads see a consistent state.
     * In-flight jobs and permits are preserved.
     *
     * @param workerId the worker's unique identifier
     * @param newSubscriptions the new Worker Queue subscriptions
     */
    public void reRegisterWorker(String workerId, List<QueueSubscription> newSubscriptions) {
        checkNotClosed();

        WorkerStreamContext<WorkerJobResponse> context = activeStreams.get(workerId);
        if (context == null) {
            log.warn("Cannot re-register worker [{}]: not found", workerId);
            return;
        }

        Set<String> oldWorkerQueueIds = context.subscribedWorkerQueueIds();
        Set<String> newWorkerQueueIds = newSubscriptions.stream()
            .map(QueueSubscription::normalizedWorkerQueueId)
            .collect(Collectors.toCollection(TreeSet::new));

        Set<String> toRemove = new TreeSet<>(oldWorkerQueueIds);
        toRemove.removeAll(newWorkerQueueIds);
        Set<String> toAdd = new TreeSet<>(newWorkerQueueIds);
        toAdd.removeAll(oldWorkerQueueIds);

        // Swap subscriptions before touching the queue maps so an added-queue dispatch
        // that races the map update sees the new bucket sizes the moment its lookup hits.
        context.replaceQueueSubscriptions(newSubscriptions);

        List<String> allAffected = new ArrayList<>(toRemove);
        allAffected.addAll(toAdd);
        allAffected.sort(String::compareTo);

        for (String workerQueueId : allAffected) {
            if (toRemove.contains(workerQueueId)) {
                // Remove from this Worker Queue
                WorkerQueueState state = workerQueueStates.get(workerQueueId);
                if (state == null)
                    continue;

                state.lock.lock();
                try {
                    Set<String> workerQueueWorkers = workerIdsByWorkerQueue.get(workerQueueId);
                    if (workerQueueWorkers != null) {
                        workerQueueWorkers.remove(workerId);
                    }

                    log.info("Re-register: removed worker {} from Worker Queue '{}'", workerId, WorkerQueues.forLog(workerQueueId));
                    metricRegistry.counter(
                        MetricRegistry.METRIC_CONTROLLER_WORKER_UNREGISTERED_TOTAL,
                        MetricRegistry.METRIC_CONTROLLER_WORKER_UNREGISTERED_TOTAL_DESCRIPTION,
                        metricRegistry.workerGroupAndQueueTags(context.getWorkerGroupId(), workerQueueId)
                    ).increment();

                    int remaining = workerQueueWorkers == null ? 0 : workerQueueWorkers.size();
                    if (remaining == 0) {
                        workerQueueStates.remove(workerQueueId);
                        workerIdsByWorkerQueue.remove(workerQueueId);
                        closeSubscriberQuietly(state.subscriber(), workerQueueId);
                        removeWorkerQueueGauges(workerQueueId);
                    } else if (!hasAnyPermitsInWorkerQueue(workerQueueId)) {
                        pauseSubscription(state, workerQueueId);
                    }
                } finally {
                    state.lock.unlock();
                }
            }
            if (toAdd.contains(workerQueueId)) {
                // Add to this Worker Queue
                WorkerQueueState state = getOrCreateWorkerQueueState(workerQueueId);
                state.lock.lock();
                try {
                    workerIdsByWorkerQueue.computeIfAbsent(workerQueueId, k -> ConcurrentHashMap.newKeySet())
                        .add(workerId);

                    log.info("Re-register: added worker {} to Worker Queue '{}'", workerId, WorkerQueues.forLog(workerQueueId));
                    metricRegistry.counter(
                        MetricRegistry.METRIC_CONTROLLER_WORKER_REGISTERED_TOTAL,
                        MetricRegistry.METRIC_CONTROLLER_WORKER_REGISTERED_TOTAL_DESCRIPTION,
                        metricRegistry.workerGroupAndQueueTags(context.getWorkerGroupId(), workerQueueId)
                    ).increment();

                    if (context.getAvailablePermits() > 0) {
                        resumeSubscription(state, workerQueueId);
                    }
                } finally {
                    state.lock.unlock();
                }
            }
        }

        // Re-evaluate pause/resume for all Task Queues the worker is now subscribed to.
        // This is critical when percentages change without adding/removing subscriptions:
        // the capacity computation (guaranteedCapacity, sharedCapacity) changes
        // immediately, so a previously-paused subscription may now have capacity.
        for (String workerQueueId : newWorkerQueueIds) {
            WorkerQueueState state = workerQueueStates.get(workerQueueId);
            if (state == null) {
                continue;
            }
            state.lock.lock();
            try {
                if (hasAnyPermitsInWorkerQueue(workerQueueId)) {
                    resumeSubscription(state, workerQueueId);
                } else {
                    pauseSubscription(state, workerQueueId);
                }
            } finally {
                state.lock.unlock();
            }
        }

        fireWorkerSubscriptionsChanged(context, toAdd, toRemove);
    }

    /**
     * Returns the set of worker IDs associated with a given worker group ID.
     *
     * @param workerGroupId the worker group ID
     * @return set of worker IDs, or empty set if none
     */
    public Set<String> getWorkerIdsByWorkerGroup(String workerGroupId) {
        Set<String> workerIds = workerIdsByWorkerGroup.get(workerGroupId);
        return workerIds != null ? Set.copyOf(workerIds) : Set.of();
    }

    /**
     * Returns the sorted Worker Queue keys for a worker context.
     * Used for consistent lock ordering to prevent deadlocks.
     */
    private List<String> sortedWorkerQueueKeys(WorkerStreamContext<?> context) {
        return context.subscribedWorkerQueueIds().stream().sorted().toList();
    }

    private void checkNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("WorkerJobDispatcher is closed");
        }
    }

    @PreDestroy
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return; // Already closed
        }

        log.info("Closing WorkerJobDispatcher with {} active workers", activeStreams.size());

        // Stop metastore change subscriptions first so no more events arrive while we tear down streams.
        try {
            metadataChangeListener.stop();
        } catch (Exception e) {
            log.warn("Error stopping metadata change listener: {}", e.getMessage());
        }

        // Close broadcast subscriptions (kill queue, cluster event queue)
        closeBroadcastSubscribersQuietly();

        // Close all queue subscriptions
        workerQueueStates.forEach((workerQueueId, state) ->
        {
            state.lock.lock();
            try {
                // Mark as closing - no new operations
                closeSubscriberQuietly(state.subscriber, workerQueueId);
            } finally {
                state.lock.unlock();
            }
        });

        // Complete all active worker streams so gRPC can release them.
        // complete() synchronizes on the same lock as sendResponse so we cannot
        // race an in-flight onNext from a dispatch thread.
        activeStreams.forEach((workerId, context) -> context.complete());

        workerQueueStates.clear();
        workerIdsByWorkerQueue.clear();
        workerIdsByWorkerGroup.clear();
        activeStreams.clear();
        killedExecutionIds.invalidateAll();
    }

    /**
     * Gets the total number of active workers across all Task Queues.
     *
     * @return the number of active workers.
     */
    public int getActiveWorkerCount() {
        return activeStreams.size();
    }

    /**
     * Gets the number of active workers in a specific Worker Queue.
     *
     * @param workerQueueId the Worker Queue
     * @return the number of active workers
     */
    public int getActiveWorkerCount(String workerQueueId) {
        Set<String> workerIds = workerIdsByWorkerQueue.get(workerQueueId);
        return workerIds == null ? 0 : workerIds.size();
    }

    /**
     * Gets the total available permits across all workers in a Worker Queue.
     *
     * @param workerQueueId the Worker Queue
     * @return the total available permits
     */
    public int getTotalPermitsForWorkerQueue(String workerQueueId) {
        return getWorkersInWorkerQueue(workerQueueId)
            .mapToInt(WorkerStreamContext::getAvailablePermits)
            .sum();
    }

    /**
     * Checks if the dispatcher is closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed.get();
    }

    private void fireWorkerRegistered(WorkerStreamContext<WorkerJobResponse> context) {
        for (WorkerLifecycleListener l : lifecycleListeners) {
            safelyInvoke(() -> l.onWorkerRegistered(context), "onWorkerRegistered");
        }
    }

    private void fireWorkerUnregistered(WorkerStreamContext<WorkerJobResponse> context) {
        for (WorkerLifecycleListener l : lifecycleListeners) {
            safelyInvoke(() -> l.onWorkerUnregistered(context), "onWorkerUnregistered");
        }
    }

    private void fireWorkerSubscriptionsChanged(
        WorkerStreamContext<WorkerJobResponse> context,
        Set<String> added,
        Set<String> removed
    ) {
        if (added.isEmpty() && removed.isEmpty()) {
            return;
        }
        for (WorkerLifecycleListener l : lifecycleListeners) {
            safelyInvoke(() -> l.onWorkerSubscriptionsChanged(context, added, removed), "onWorkerSubscriptionsChanged");
        }
    }

    private void safelyInvoke(Runnable action, String description) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("Worker lifecycle listener failed on '{}': {}", description, e.getMessage(), e);
        }
    }

    /**
     * Holds the state for a Worker Queue: the queue subscription and the synchronization lock.
     */
    private record WorkerQueueState(
        QueueSubscriber<WorkerJobEvent> subscriber,
        AtomicBoolean isPaused,
        ReentrantLock lock) {

        public WorkerQueueState(QueueSubscriber<WorkerJobEvent> subscriber) {
            this(subscriber, new AtomicBoolean(true), new ReentrantLock());
        }
    }
}
