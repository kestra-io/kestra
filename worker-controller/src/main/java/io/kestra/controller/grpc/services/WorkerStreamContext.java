package io.kestra.controller.grpc.services;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.kestra.core.worker.QueueSubscription;
import io.kestra.core.runners.WorkerJob;

import io.grpc.stub.StreamObserver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Holds the state for a single worker's bidirectional stream connection.
 * <p>
 * This class tracks:
 * <ul>
 * <li>Available permits (how many jobs we can send to this worker)</li>
 * <li>In-flight jobs (sent but not yet ACKed by the worker)</li>
 * <li>Connection metadata (workerId, queue subscriptions, etc.)</li>
 * </ul>
 * <p>
 * Capacity accounting (slot reservation, per-queue bucket math) is delegated
 * to a {@link WorkerCapacityPolicy} chosen at construction time by
 * {@link WorkerCapacityPolicyFactory}.
 * <p>
 * Thread-safe: all operations are safe for concurrent access from multiple threads.
 *
 * @param <T> the response type for the stream observer (WorkerJobResponse after proto compilation)
 */
@Slf4j
@Getter
public class WorkerStreamContext<T> {

    private final String workerId;
    private final String workerGroupId;
    private volatile List<QueueSubscription> queueSubscriptions;
    private final int maxConcurrency;
    private final StreamObserver<T> responseObserver;
    private final WorkerCapacityPolicy capacityPolicy;

    /**
     * Number of jobs the worker has capacity to receive.
     * Decremented when a job is sent, incremented when worker sends permits.
     */
    private final AtomicInteger availablePermits = new AtomicInteger(0);

    /**
     * Jobs that have been dispatched to the worker and have not yet reached a terminal
     * state. Key: job UID, Value: pending job info (carries the reserved bucket so
     * {@link #completeJob} can return the slot to the right counter).
     * <p>
     * Entries live from dispatch ({@link #trackInFlight}) to completion
     * ({@link #completeJob}). They count toward {@link #getInFlightCount()} for
     * least-loaded worker selection and toward
     * {@code METRIC_CONTROLLER_JOB_INFLIGHT}, so both signals now reflect real
     * concurrent work rather than network pipeline depth.
     * <p>
     * The actual job state is persisted in {@code WorkerJobRunningStateStore} before
     * dispatch; this map is purely in-memory bookkeeping for the connected stream.
     */
    private final ConcurrentHashMap<String, PendingJob> inFlightJobs = new ConcurrentHashMap<>();

    /**
     * Timestamp of last activity (permit request or completion) from this worker.
     */
    private volatile Instant lastActivity;

    /**
     * Creates a new worker stream context with an explicit capacity policy.
     *
     * @param workerId unique identifier for this worker instance
     * @param workerGroupId the resolved worker group ID; the controller normalizes the
     *                      absent case to {@link io.kestra.core.worker.WorkerGroups#DEFAULT_ID}
     *                      before the worker echoes it here, so the value is always set.
     * @param queueSubscriptions the Worker Queue subscriptions with reservedPercent values
     * @param maxConcurrency maximum concurrent jobs this worker can handle
     * @param responseObserver gRPC stream observer for sending jobs to the worker
     * @param capacityPolicy slot reservation policy; must already reflect
     *                       {@code maxConcurrency} and {@code queueSubscriptions}
     */
    public WorkerStreamContext(String workerId,
        String workerGroupId,
        List<QueueSubscription> queueSubscriptions,
        int maxConcurrency,
        StreamObserver<T> responseObserver,
        WorkerCapacityPolicy capacityPolicy) {
        this.workerId = workerId;
        this.workerGroupId = workerGroupId;
        this.queueSubscriptions = queueSubscriptions == null ? List.of() : List.copyOf(queueSubscriptions);
        this.maxConcurrency = maxConcurrency;
        this.responseObserver = responseObserver;
        this.capacityPolicy = Objects.requireNonNull(capacityPolicy, "capacityPolicy must not be null");
        this.lastActivity = Instant.now();
    }

    /**
     * Convenience constructor that installs the default
     * {@link SinglePoolCapacityPolicy}. Used by unit tests; production paths
     * build the context via {@link WorkerCapacityPolicyFactory}.
     */
    public WorkerStreamContext(String workerId,
        String workerGroupId,
        List<QueueSubscription> queueSubscriptions,
        int maxConcurrency,
        StreamObserver<T> responseObserver) {
        this(workerId, workerGroupId, queueSubscriptions, maxConcurrency, responseObserver,
            new SinglePoolCapacityPolicy(maxConcurrency));
    }

    /**
     * Returns the set of Worker Queue ids this worker is subscribed to.
     */
    public Set<String> subscribedWorkerQueueIds() {
        return queueSubscriptions.stream()
            .map(QueueSubscription::normalizedWorkerQueueId)
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Slots guaranteed to {@code workerQueueId} by the policy. Single-pool
     * policies report 0 — the entire pool sits in {@link #sharedCapacity()}.
     */
    public int guaranteedCapacity(String workerQueueId) {
        return capacityPolicy.allocated(workerQueueId);
    }

    /** Size of the shared (unreserved) pool. */
    public int sharedCapacity() {
        return capacityPolicy.sharedAllocated();
    }

    /** Free slots currently available in the shared pool. */
    public int sharedFree() {
        return capacityPolicy.sharedAllocated() - capacityPolicy.sharedUsed();
    }

    /** Free slots currently available in {@code workerQueueId}'s guaranteed bucket. */
    public int guaranteedFree(String workerQueueId) {
        return capacityPolicy.allocated(workerQueueId) - capacityPolicy.used(workerQueueId);
    }

    /** Slots currently used in {@code workerQueueId}'s guaranteed bucket. */
    public int guaranteedUsed(String workerQueueId) {
        return capacityPolicy.used(workerQueueId);
    }

    /** Slots currently used in the shared pool. */
    public int sharedUsed() {
        return capacityPolicy.sharedUsed();
    }

    /**
     * Attempts to atomically reserve a slot for the given Worker Queue. See
     * {@link WorkerCapacityPolicy#tryReserve(String)} for tier ordering.
     */
    public String tryReserveBucket(String workerQueueId) {
        return capacityPolicy.tryReserve(workerQueueId);
    }

    /**
     * Releases a previously reserved bucket slot.
     *
     * @param bucket {@link PendingJob#SHARED} or a Worker Queue id
     */
    public void releaseBucket(String bucket) {
        capacityPolicy.release(bucket);
    }

    /**
     * Returns {@code true} if the worker has any available capacity for tasks from
     * the given Worker Queue (direct or via policy-internal borrowing).
     */
    public boolean hasCapacityForQueue(String workerQueueId) {
        return capacityPolicy.hasCapacity(workerQueueId);
    }

    /**
     * Replaces this worker's queue subscriptions (dynamic reconfiguration) and
     * forwards the change to the capacity policy. In-flight reservations are
     * preserved.
     */
    public void replaceQueueSubscriptions(List<QueueSubscription> newSubscriptions) {
        List<QueueSubscription> resolved = newSubscriptions == null ? List.of() : List.copyOf(newSubscriptions);
        this.queueSubscriptions = resolved;
        this.capacityPolicy.replaceSubscriptions(resolved);
    }

    /**
     * Sets the total available permits (capacity) for this worker.
     * Called when the worker sends its total remaining capacity.
     *
     * @param count the total number of permits (remaining capacity)
     */
    public void setPermits(int count) {
        if (count >= 0) {
            availablePermits.set(count);
            lastActivity = Instant.now();
            log.trace("Worker {} permits set to {}", workerId, count);
        }
    }

    /**
     * Adds permits (capacity) for this worker.
     * Used to restore permits after a dispatch failure.
     *
     * @param count number of permits to add
     */
    public void addPermits(int count) {
        if (count > 0) {
            availablePermits.addAndGet(count);
            lastActivity = Instant.now();
            log.trace("Worker {} added {} permits, now has {}", workerId, count, availablePermits.get());
        }
    }

    /**
     * Atomically decrements the available permits by one only if at least one is available.
     * Returns {@code true} if a permit was consumed, {@code false} if none were available.
     * Used to atomically reserve worker capacity before dispatching a job.
     */
    public boolean tryConsumePermit() {
        int current;
        do {
            current = availablePermits.get();
            if (current <= 0) return false;
        } while (!availablePermits.compareAndSet(current, current - 1));
        return true;
    }

    /**
     * Gets the current number of available permits.
     *
     * @return number of jobs we can still send to this worker
     */
    public int getAvailablePermits() {
        return availablePermits.get();
    }

    /**
     * Tracks a dispatched job. The entry lives in {@link #inFlightJobs} until the
     * worker signals a terminal state via {@link #completeJob}, holding the bucket
     * reservation for the job's full lifetime.
     *
     * @param jobId the job's unique identifier
     * @param job the worker job
     * @param bucket either {@link PendingJob#SHARED} or a worker queue key
     */
    public void trackInFlight(String jobId, WorkerJob job, String bucket) {
        inFlightJobs.put(jobId, new PendingJob(jobId, job, Instant.now(), bucket));
        log.trace("Worker {} tracking in-flight job {} in bucket {}", workerId, jobId, bucket);
    }

    /**
     * Removes the job from in-flight tracking and releases the bucket slot reserved
     * for it. Called when the worker signals a terminal state over the bidi stream
     * (see {@code completedJobIds} in {@code WorkerJobRequest}) or when a dispatch
     * fails before reaching the worker.
     *
     * @param jobId the job's unique identifier
     * @return the bucket that was released, or {@code null} if the job was unknown
     *         (e.g., the result arrived on a new controller after a restart)
     */
    public String completeJob(String jobId) {
        PendingJob removed = inFlightJobs.remove(jobId);
        if (removed == null) {
            return null;
        }
        releaseBucket(removed.bucket());
        lastActivity = Instant.now();
        log.trace("Worker {} completed job {} (released bucket={})", workerId, jobId, removed.bucket());
        return removed.bucket();
    }

    /**
     * Releases bucket slots for every job still in-flight on this stream (called when
     * the worker disconnects). The worker's still-running tasks will post their
     * results via {@code sendWorkerTaskResults} independently; they just no longer
     * count toward bucket usage on this controller.
     */
    public void releaseAllInFlightBuckets() {
        inFlightJobs.values().forEach(p -> releaseBucket(p.bucket()));
        inFlightJobs.clear();
    }

    /**
     * Gets all in-flight jobs for this worker.
     * Useful for cleanup when the worker disconnects.
     *
     * @return collection of pending jobs
     */
    public Collection<PendingJob> getInFlightJobs() {
        return inFlightJobs.values();
    }

    /**
     * Gets the count of in-flight jobs.
     *
     * @return number of jobs sent but not yet ACKed
     */
    public int getInFlightCount() {
        return inFlightJobs.size();
    }

    /**
     * Lock to serialize onNext() calls on the response stream observer.
     * gRPC's StreamObserver is not thread-safe for concurrent onNext() calls.
     */
    private final Object streamLock = new Object();

    /**
     * Sends a response to the worker via the stream observer.
     *
     * @param response the response to send
     */
    public void sendResponse(T response) {
        synchronized (streamLock) {
            responseObserver.onNext(response);
        }
    }

    /**
     * Completes the worker stream.
     * Synchronized on the same lock as {@link #sendResponse} so onNext/onCompleted
     * are never called concurrently — gRPC requires StreamObserver callbacks to be
     * serialized.
     */
    public void complete() {
        synchronized (streamLock) {
            try {
                responseObserver.onCompleted();
            } catch (Exception e) {
                log.debug("Error completing stream for worker {}: {}", workerId, e.getMessage());
            }
        }
    }

    /**
     * Represents a job that has been sent to a worker but not yet acknowledged.
     *
     * @param jobId unique identifier of the job
     * @param job the worker job
     * @param sentAt when the job was sent to the worker
     * @param bucket where this job was slotted on the worker ({@link #SHARED} or a group key)
     */
    public record PendingJob(String jobId, WorkerJob job, Instant sentAt, String bucket) {
        /** Sentinel bucket value for the shared (unreserved) slot pool. */
        public static final String SHARED = "__shared__";
    }
}
