package io.kestra.controller.grpc.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.protobuf.ByteString;
import io.kestra.controller.grpc.WorkerJobPayload;
import io.kestra.controller.grpc.WorkerJobResponse;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.executor.WorkerJobRunningStateStore;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.runners.NoTransactionContext;
import io.kestra.core.runners.WorkerInstance;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.runners.WorkerTaskRunning;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.runners.WorkerTriggerRunning;
import io.kestra.core.utils.Either;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.ThreadSafe;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Central coordinator for dispatching {@link WorkerJob} to workers using the pull/ack pattern.
 * <p>
 * This component:
 * <ul>
 *   <li>Manages all active worker stream connections</li>
 *   <li>Subscribes to the {@link KeyedDispatchQueueInterface} per worker group</li>
 *   <li>Dispatches jobs to workers based on their available permits</li>
 *   <li>Uses pause/resume for backpressure when no workers have capacity</li>
 * </ul>
 * <p>
 * When no workers have capacity, the subscription is paused and the job is re-queued.
 * This ensures jobs are never lost even if the controller crashes, as they are
 * always either in the durable queue or in the WorkerJobRunningStateStore.
 * <p>
 * When all workers for a group disconnect, the subscription is disposed immediately
 * to free resources. A new subscription will be created when workers reconnect.
 * <p>
 * Worker groups are optional. Workers without a group (null or empty string) are assigned
 * to the default group (represented as empty string internally).
 * <p>
 * Thread-safe: Operations are synchronized per worker group to prevent race conditions
 * between job dispatching and worker registration/permit updates.
 */
@ThreadSafe
@Singleton
@Slf4j
public class WorkerJobDispatcher {
    
    // TTL for killed execution IDs in the local cache to prevent unbounded growth
    // This cache is used for pre-dispatch filtering of tasks belonging to killed executions avoiding unnecessary dispatch and allowing for faster kill response times.
    private static final Duration KILLED_CACHE_TTL = Duration.ofHours(24);

    private final KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue;
    private final WorkerJobRunningStateStore workerJobRunningStateStore;
    private final DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue;

    /**
     * Global closed flag to prevent operations after shutdown.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * All active worker stream connections, keyed by workerId.
     */
    private final ConcurrentHashMap<String, WorkerStreamContext<WorkerJobResponse>> activeStreams = new ConcurrentHashMap<>();

    /**
     * Secondary index: worker IDs by group for O(1) group lookups.
     * Key: workerGroup (empty string for default group), Value: set of worker IDs
     */
    private final ConcurrentHashMap<String, Set<String>> workerIdsByGroup = new ConcurrentHashMap<>();

    /**
     * Queue subscriptions and state per worker group.
     * Key: workerGroup (empty string for default group)
     */
    private final ConcurrentHashMap<String, GroupState> groupStates = new ConcurrentHashMap<>();

    /**
     * Cache of killed execution IDs for pre-dispatch filtering.
     */
    private final Cache<String, Boolean> killedExecutionIds = Caffeine.newBuilder()
        .expireAfterWrite(KILLED_CACHE_TTL)
        .build();

    /**
     * Subscription to the execution killed broadcast queue.
     */
    private volatile QueueSubscriber<ExecutionKilled> killQueueSubscriber;

    @Inject
    public WorkerJobDispatcher(
        KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue,
        WorkerJobRunningStateStore workerJobRunningStateStore,
        BroadcastQueueInterface<ExecutionKilled> executionKilledQueue,
        DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue) {
        this.workerJobEventQueue = workerJobEventQueue;
        this.workerJobRunningStateStore = workerJobRunningStateStore;
        this.workerTaskResultQueue = workerTaskResultQueue;

        // Subscribe to execution killed events
        this.killQueueSubscriber = executionKilledQueue.subscriber();
        this.killQueueSubscriber.subscribe(either -> {
            if (either.isRight()) {
                log.error("Deserialization error for ExecutionKilled: {}", either.getRight().getMessage());
                return;
            }
            ExecutionKilled killed = either.getLeft();
            if (killed.getState() == ExecutionKilled.State.EXECUTED) {
                onExecutionKilled(killed);
            }
        });
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

        // Serialize the kill command
        ByteString killData = MessageFormats.JSON.toByteString(killed);

        // Broadcast to all connected workers
        activeStreams.forEach((workerId, context) -> {
            try {
                WorkerJobResponse response = WorkerJobResponse.newBuilder()
                    .setHeader(RequestOrResponseHeaderFactory.create(workerId))
                    .addKillCommands(killData)
                    .build();
                context.sendResponse(response);
                log.debug("Broadcast kill command to worker {}", workerId);
            } catch (Exception e) {
                log.warn("Failed to send kill command to worker {}: {}", workerId, e.getMessage());
            }
        });
    }

    /**
     * Registers a new worker stream connection.
     * Creates a queue subscription for the worker group if one doesn't exist.
     *
     * @param context the worker stream context
     * @throws IllegalStateException if the dispatcher is closed
     */
    public void registerWorker(WorkerStreamContext<WorkerJobResponse> context) {
        checkNotClosed();

        String workerGroup = context.getWorkerGroup();
        GroupState state = getOrCreateGroupState(workerGroup);

        state.lock.lock();
        try {
            // Add worker to indexes
            activeStreams.put(context.getWorkerId(), context);
            workerIdsByGroup.computeIfAbsent(workerGroup, k -> ConcurrentHashMap.newKeySet())
                .add(context.getWorkerId());

            log.info("Registered worker {} for group '{}'", context.getWorkerId(), WorkerGroup.forLog(workerGroup));

            // Resume subscription if worker has permits
            if (context.getAvailablePermits() > 0) {
                resumeSubscription(state, workerGroup);
            }
        } finally {
            state.lock.unlock();
        }
    }

    /**
     * Gets an existing group state or creates a new one atomically.
     */
    private GroupState getOrCreateGroupState(String workerGroup) {
        return groupStates.computeIfAbsent(workerGroup, this::createGroupState);
    }

    /**
     * Creates a new group state with subscription for a worker group.
     */
    private GroupState createGroupState(String workerGroup) {
        String workerGroupOrNull = workerGroup.isEmpty() ? null : workerGroup;
        QueueSubscriber<WorkerJobEvent> subscriber = workerJobEventQueue.subscriber(workerGroupOrNull);
        subscriber.subscribe(either -> handleIncomingJob(workerGroup, either));
        
        // TODO we should be able to start in paused state and only resume when the first worker with permits connects,
        //  but that requires a code change in the queue implementation to support starting paused
        subscriber.pause();  // Start paused until workers connect with permits
        log.info("Created queue subscription for worker group '{}' (initially paused)", WorkerGroup.forLog(workerGroup));
        return new GroupState(subscriber);
    }

    /**
     * Unregisters a worker when the stream disconnects.
     * If this was the last worker for the group, disposes the subscription immediately.
     *
     * @param workerId the worker's unique identifier
     */
    public void unregisterWorker(String workerId) {
        Objects.requireNonNull(workerId, "workerId must not be null");

        WorkerStreamContext<WorkerJobResponse> context = activeStreams.get(workerId);
        if (context == null) {
            log.warn("Worker [{}] not found", workerId);
            return;
        }

        String workerGroup = context.getWorkerGroup();
        GroupState state = groupStates.get(workerGroup);
        if (state == null) {
            activeStreams.remove(workerId);
            return;
        }

        QueueSubscriber<WorkerJobEvent> subscriberToClose = null;

        state.lock.lock();
        try {
            // Remove worker from indexes
            context = activeStreams.remove(workerId);
            if (context == null) {
                return;
            }

            Set<String> groupWorkers = workerIdsByGroup.get(workerGroup);
            if (groupWorkers != null) {
                groupWorkers.remove(workerId);
            }

            log.info("Unregistered worker {} from group '{}', had {} in-flight jobs", workerId, WorkerGroup.forLog(workerGroup), context.getInFlightCount());

            // Check if there are any workers left for this group
            int remainingWorkers = groupWorkers == null ? 0 : groupWorkers.size();

            if (remainingWorkers == 0) {
                // No more workers - dispose immediately
                groupStates.remove(workerGroup);
                workerIdsByGroup.remove(workerGroup);
                subscriberToClose = state.subscriber();
                log.info("Disposing subscription for group '{}': no workers remaining", WorkerGroup.forLog(workerGroup));
            } else if (!hasAnyPermitsInGroup(workerGroup)) {
                // Workers exist but no permits - pause
                pauseSubscription(state, workerGroup);
            }
        } finally {
            state.lock.unlock();
        }

        if (subscriberToClose != null) {
            closeSubscriberQuietly(subscriberToClose, workerGroup);
        }
    }

    /**
     * Handles new permits received from a worker.
     * The permits value represents the worker's total remaining capacity.
     * Resumes the queue subscription if any worker has capacity, pauses otherwise.
     *
     * @param context    the worker stream context
     * @param newPermits the worker's total remaining capacity (0 or more)
     */
    public void onPermitsReceived(WorkerStreamContext<WorkerJobResponse> context, int newPermits) {
        if (closed.get() || newPermits < 0) {
            return;
        }

        String workerGroup = context.getWorkerGroup();
        GroupState state = groupStates.get(workerGroup);
        if (state == null) {
            return;
        }

        state.lock.lock();
        try {
            // Verify worker is still registered
            if (!activeStreams.containsKey(context.getWorkerId())) {
                return;
            }

            log.trace("Permits received: worker {}, group '{}', permits={}", context.getWorkerId(), WorkerGroup.forLog(workerGroup), newPermits);
            context.setPermits(newPermits);

            // Resume or pause based on whether any worker in the group has capacity
            if (hasAnyPermitsInGroup(workerGroup)) {
                resumeSubscription(state, workerGroup);
            } else {
                pauseSubscription(state, workerGroup);
            }
        } finally {
            state.lock.unlock();
        }
    }

    /**
     * Handles job receipt acknowledgments from a worker.
     * Removes jobs from the controller's in-memory tracking.
     *
     * @param context the worker stream context
     * @param jobIds  list of job UIDs that were received
     */
    public void onAcksReceived(WorkerStreamContext<WorkerJobResponse> context, List<String> jobIds) {
        for (String jobId : jobIds) {
            context.acknowledgeJob(jobId);
        }
        log.debug("Worker {} acknowledged {} jobs", context.getWorkerId(), jobIds.size());
    }

    /**
     * Handles a job event received from the queue.
     * <p>
     * If a worker with permits is available, the job is dispatched immediately.
     * If no worker has capacity, the job is re-queued and the subscription is paused.
     */
    private void handleIncomingJob(String workerGroup, Either<WorkerJobEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Deserialization error for job in group '{}': {}", workerGroup, either.getRight().getMessage());
            return;
        }

        WorkerJobEvent event = either.getLeft();
        WorkerJob job = event.job();

        // Pre-dispatch killed check: if the execution is already killed, produce a KILLED result directly
        if (job instanceof WorkerTask workerTask) {
            String executionId = workerTask.getTaskRun().getExecutionId();
            if (!Boolean.TRUE.equals(workerTask.getTaskRun().getForceExecution())
                && killedExecutionIds.getIfPresent(executionId) != null) {
                log.info("Skipping dispatch of task '{}' for killed execution '{}'", job.uid(), executionId);
                try {
                    workerTaskResultQueue.emit(new WorkerTaskResult(workerTask.getTaskRun().withState(State.Type.KILLED)));
                } catch (QueueException e) {
                    log.error("Failed to emit KILLED result for task '{}': {}", job.uid(), e.getMessage(), e);
                }
                return;
            }
        }

        GroupState state = groupStates.get(workerGroup);
        if (state == null) {
            log.error("No state for worker group '{}', re-queuing job {}", WorkerGroup.forLog(workerGroup), job.uid());
            requeue(event);
            return;
        }

        state.lock.lock();
        try {
            // Find a worker with permits and atomically consume the permit
            Optional<WorkerStreamContext<WorkerJobResponse>> target = findAndReserveWorker(workerGroup);

            if (target.isPresent()) {
                dispatchJobToWorker(target.get(), job, event);

                // Check if we should pause after this dispatch (no more capacity)
                if (!hasAnyPermitsInGroup(workerGroup)) {
                    pauseSubscription(state, workerGroup);
                }
            } else {
                // No worker with capacity - pause subscription and re-queue job
                pauseSubscription(state, workerGroup);
                log.debug("No workers with permits for group '{}', re-queuing job {}", WorkerGroup.forLog(workerGroup), job.uid());
                requeue(event);
            }
        } finally {
            state.lock.unlock();
        }
    }

    /**
     * Gets workers in a specific group using the secondary index.
     */
    private Stream<WorkerStreamContext<WorkerJobResponse>> getWorkersInGroup(String workerGroup) {
        Set<String> workerIds = workerIdsByGroup.get(workerGroup);
        if (workerIds == null || workerIds.isEmpty()) {
            return Stream.empty();
        }
        return workerIds.stream()
            .map(activeStreams::get)
            .filter(Objects::nonNull);
    }

    /**
     * Checks if any worker in the group has available permits.
     */
    private boolean hasAnyPermitsInGroup(String workerGroup) {
        return getWorkersInGroup(workerGroup)
            .anyMatch(ctx -> ctx.getAvailablePermits() > 0);
    }

    /**
     * Finds a worker with available permits and atomically consumes one permit.
     * Must be called while holding the group lock.
     *
     * @return the worker context if found and permit consumed, empty otherwise
     */
    private Optional<WorkerStreamContext<WorkerJobResponse>> findAndReserveWorker(String workerGroup) {
        return getWorkersInGroup(workerGroup)
            .filter(ctx -> ctx.getAvailablePermits() > 0)
            .min(Comparator.comparingInt(WorkerStreamContext::getInFlightCount))
            .filter(WorkerStreamContext::tryConsumePermit);
    }

    /**
     * Dispatches a job to a worker.
     * The permit has already been consumed before calling this method.
     * <p>
     * CRITICAL: The job is persisted to WorkerJobRunningStateStore BEFORE sending.
     * If sending fails, the permit is restored and the job is re-queued.
     */
    private void dispatchJobToWorker(WorkerStreamContext<WorkerJobResponse> context, WorkerJob job, 
                                     WorkerJobEvent originalEvent) {
        String jobId = job.uid();

        // 1. PERSIST before sending (critical for recovery)
        persistJobToStateStore(context, job);

        // 2. Track in-flight locally
        context.trackInFlight(jobId, job);

        // 3. Send to worker
        try {
            WorkerJobResponse response = WorkerJobResponse.newBuilder()
                .setHeader(RequestOrResponseHeaderFactory.create(context.getWorkerId()))
                .addJobs(WorkerJobPayload.newBuilder()
                    .setJobId(jobId)
                    .setJobData(MessageFormats.JSON.toByteString(job))
                    .build())
                .build();

            context.sendResponse(response);
            log.debug("Dispatched job {} to worker {}", jobId, context.getWorkerId());

        } catch (Exception e) {
            log.error("Failed to send job {} to worker {}: {}", jobId, context.getWorkerId(), e.getMessage());
            handleDispatchFailure(context, job, originalEvent);
        }
    }

    /**
     * Persists a job to the state store for recovery.
     */
    private void persistJobToStateStore(WorkerStreamContext<WorkerJobResponse> context, WorkerJob job) {
        WorkerInstance workerInstance = new WorkerInstance(context.getWorkerId(), context.getWorkerGroup());
        if (job instanceof WorkerTask workerTask) {
            workerJobRunningStateStore.save(NoTransactionContext.INSTANCE, WorkerTaskRunning.of(workerTask, workerInstance));
        } else if (job instanceof WorkerTrigger workerTrigger) {
            workerJobRunningStateStore.save(NoTransactionContext.INSTANCE, WorkerTriggerRunning.of(workerTrigger, workerInstance));
        }
    }

    /**
     * Handles failure to send a job to a worker.
     * Restores the permit and re-queues the job.
     */
    private void handleDispatchFailure(WorkerStreamContext<WorkerJobResponse> context, WorkerJob job,
                                       WorkerJobEvent originalEvent) {
        // Restore permit to the worker
        context.addPermits(1);

        // Remove from in-flight tracking
        context.acknowledgeJob(job.uid());

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

    private void pauseSubscription(GroupState state, String workerGroup) {
        if (state.isPaused.compareAndSet(false, true)) {
            state.subscriber.pause();
            log.debug("Paused subscription for group '{}'", WorkerGroup.forLog(workerGroup));
        }
    }

    private void resumeSubscription(GroupState state, String workerGroup) {
        if (state.isPaused.compareAndSet(true, false)) {
            state.subscriber.resume();
            log.debug("Resumed subscription for group '{}'", WorkerGroup.forLog(workerGroup));
        }
    }

    private void closeSubscriberQuietly(QueueSubscriber<WorkerJobEvent> subscriber, String workerGroup) {
        try {
            subscriber.close();
        } catch (Exception e) {
            log.warn("Error closing subscription for group '{}': {}", WorkerGroup.forLog(workerGroup), e.getMessage());
        }
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

        // Close kill queue subscription
        if (killQueueSubscriber != null) {
            try {
                killQueueSubscriber.close();
            } catch (Exception e) {
                log.warn("Error closing kill queue subscription: {}", e.getMessage());
            }
        }

        // Close all queue subscriptions
        groupStates.forEach((group, state) -> {
            state.lock.lock();
            try {
                // Mark as closing - no new operations
            } finally {
                state.lock.unlock();
            }
            closeSubscriberQuietly(state.subscriber, group);
        });

        // Complete all active worker streams so gRPC can release them
        activeStreams.forEach((workerId, context) -> {
            try {
                context.getResponseObserver().onCompleted();
            } catch (Exception e) {
                log.debug("Error completing stream for worker {}: {}", workerId, e.getMessage());
            }
        });

        groupStates.clear();
        workerIdsByGroup.clear();
        activeStreams.clear();
    }

    /**
     * Gets the total number of active workers across all groups.
     *
     * @return the number of active workers.
     */
    public int getActiveWorkerCount() {
        return activeStreams.size();
    }

    /**
     * Gets the number of active workers in a specific group.
     *
     * @param workerGroup the worker group
     * @return the number of active workers
     */
    public int getActiveWorkerCount(String workerGroup) {
        Set<String> workerIds = workerIdsByGroup.get(workerGroup);
        return workerIds == null ? 0 : workerIds.size();
    }

    /**
     * Gets the total available permits across all workers in a group.
     *
     * @param workerGroup the worker group
     * @return the total available permits
     */
    public int getTotalPermitsForGroup(String workerGroup) {
        return getWorkersInGroup(workerGroup)
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

    /**
     * Holds the state for a worker group including the queue subscription and synchronization lock.
     */
    private record GroupState(
        QueueSubscriber<WorkerJobEvent> subscriber,
        AtomicBoolean isPaused,
        ReentrantLock lock
    ) {
        
        public GroupState(QueueSubscriber<WorkerJobEvent> subscriber) {
            this(subscriber, new AtomicBoolean(true), new ReentrantLock());
        }
    }
}
