package io.kestra.controller.grpc.services;

import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
 * <li>Connection metadata (workerId, workerGroup, etc.)</li>
 * </ul>
 * <p>
 * Thread-safe: all operations are safe for concurrent access from multiple threads.
 *
 * @param <T> the response type for the stream observer (WorkerJobResponse after proto compilation)
 */
@Slf4j
@Getter
public class WorkerStreamContext<T> {

    private final String workerId;
    private final String workerGroup;
    private final int maxConcurrency;
    private final StreamObserver<T> responseObserver;

    /**
     * Number of jobs the worker has capacity to receive.
     * Decremented when a job is sent, incremented when worker sends permits.
     */
    private final AtomicInteger availablePermits = new AtomicInteger(0);

    /**
     * Jobs that have been sent to the worker but not yet acknowledged.
     * Key: job UID, Value: pending job info.
     * <p>
     * This is used for controller-side memory cleanup only.
     * The actual job state is persisted in WorkerJobRunningStateStore before sending.
     */
    private final ConcurrentHashMap<String, PendingJob> inFlightJobs = new ConcurrentHashMap<>();

    /**
     * Timestamp of last activity (permit request or ACK) from this worker.
     */
    private volatile Instant lastActivity;

    /**
     * Creates a new worker stream context.
     *
     * @param workerId unique identifier for this worker instance
     * @param workerGroup worker group this worker belongs to (may be null for default)
     * @param maxConcurrency maximum concurrent jobs this worker can handle
     * @param responseObserver gRPC stream observer for sending jobs to the worker
     */
    public WorkerStreamContext(String workerId,
        String workerGroup,
        int maxConcurrency,
        StreamObserver<T> responseObserver) {
        this.workerId = workerId;
        this.workerGroup = workerGroup == null || workerGroup.isEmpty() ? "" : workerGroup;
        this.maxConcurrency = maxConcurrency;
        this.responseObserver = responseObserver;
        this.lastActivity = Instant.now();
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
     * Attempts to consume a single permit.
     *
     * @return true if a permit was consumed, false if no permits available
     */
    public boolean tryConsumePermit() {
        int current;
        do {
            current = availablePermits.get();
            if (current <= 0) {
                return false;
            }
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
     * Tracks a job as in-flight (sent to worker, awaiting ACK).
     *
     * @param jobId the job's unique identifier
     * @param job the worker job
     */
    public void trackInFlight(String jobId, WorkerJob job) {
        inFlightJobs.put(jobId, new PendingJob(jobId, job, Instant.now()));
        log.trace("Worker {} tracking in-flight job {}", workerId, jobId);
    }

    /**
     * Acknowledges receipt of a job, removing it from in-flight tracking.
     * This is for controller memory cleanup only; the job state in
     * WorkerJobRunningStateStore is cleaned up when the job completes.
     *
     * @param jobId the job's unique identifier
     * @return the pending job if it was in-flight, null otherwise
     */
    public PendingJob acknowledgeJob(String jobId) {
        PendingJob removed = inFlightJobs.remove(jobId);
        if (removed != null) {
            lastActivity = Instant.now();
            log.trace("Worker {} acknowledged job {}", workerId, jobId);
        }
        return removed;
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
     * Represents a job that has been sent to a worker but not yet acknowledged.
     *
     * @param jobId unique identifier of the job
     * @param job the worker job
     * @param sentAt when the job was sent to the worker
     */
    public record PendingJob(String jobId, WorkerJob job, Instant sentAt) {
    }
}
