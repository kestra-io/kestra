package io.kestra.worker.fetchers;

import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.google.protobuf.ByteString;

import io.kestra.controller.grpc.WorkerConnectionInfo;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceStub;
import io.kestra.controller.grpc.WorkerJobPayload;
import io.kestra.controller.grpc.WorkerJobRequest;
import io.kestra.controller.grpc.WorkerJobResponse;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;

import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.server.ClusterEvent;
import io.kestra.core.worker.WorkerBroadcastEvent;
import io.kestra.core.worker.WorkerMetadataChangeHandler;
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.worker.WorkerLoop;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.services.ExecutionKilledManager;

import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Component responsible for fetching worker jobs using a bidirectional streaming pattern
 * with permit-based flow control.
 * <p>
 * This client:
 * <ul>
 * <li>Opens a bidirectional stream to the controller on startup</li>
 * <li>Sends initial connection info (workerId, workerGroupId, maxConcurrency) + initial permits</li>
 * <li>Receives jobs from the controller and puts them in the local queue</li>
 * <li>Sends updated permit values back to the controller as local capacity changes</li>
 * <li>Piggy-backs job completion signals (job UIDs that reached a terminal state) on
 *     the same stream so the controller can release the per-queue bucket reserved
 *     for the job — see {@link #onJobCompleted(String)}</li>
 * </ul>
 * <p>
 * The controller only sends jobs when the worker has permits (capacity), providing flow control
 * and preventing worker overload.
 */
@Singleton
@Slf4j
public class WorkerJobFetcher extends WorkerLoop implements JobFetcher {

    /**
     * Qualifier of the worker-local cluster-event broadcast bus.
     * <p>
     * Bound only on a dedicated worker process, which has no direct access to the shared
     * broadcast queue backend. In every other topology the qualifier is unbound and the
     * gRPC relay is a no-op.
     */
    public static final String WORKER_LOCAL_CLUSTER_EVENTS = "workerLocalClusterEvents";

    /**
     * Interval for checking capacity changes and sending permit updates.
     */
    private static final Duration PERMIT_CHECK_INTERVAL = Duration.ofMillis(100);

    /**
     * Minimum delay before the first reconnection attempt after a stream error.
     */
    private static final long MIN_RECONNECT_DELAY_MS = Duration.ofMillis(500).toMillis();

    /**
     * Maximum delay between reconnection attempts (exponential backoff ceiling).
     */
    private static final long MAX_RECONNECT_DELAY_MS = Duration.ofSeconds(30).toMillis();

    private final WorkerControllerServiceStub workerControllerServiceStub;
    private final WorkerQueueRegistry workerQueueRegistry;
    private final ExecutionKilledManager executionKilledManager;
    private final BroadcastQueueInterface<ClusterEvent> clusterEventQueue;
    private final List<WorkerMetadataChangeHandler> metadataChangeHandlers;

    private WorkerQueue<WorkerJob> workerJobQueue;
    private WorkerContext workerContext;

    /**
     * Reference to the current stream's request observer for sending permits and
     * completion signals back to the controller.
     */
    private final AtomicReference<ClientCallStreamObserver<WorkerJobRequest>> requestObserverRef = new AtomicReference<>();

    /**
     * Lock to serialize onNext() calls on the request stream observer.
     * gRPC's StreamObserver is not thread-safe for concurrent onNext() calls.
     */
    private final Object streamLock = new Object();

    /**
     * Tracks the last permits value sent to avoid sending duplicates.
     */
    private final AtomicInteger lastSentPermits = new AtomicInteger(-1);

    /**
     * UIDs of jobs that reached a terminal state on this worker and need to be
     * signaled back to the owning controller on the next outgoing
     * {@link WorkerJobRequest}. Drained into the {@code completedJobIds} field;
     * a non-empty queue also triggers a permit-update flush so completions don't
     * sit when permits aren't changing.
     */
    private final Queue<String> pendingCompletions = new ConcurrentLinkedQueue<>();

    /**
     * Latch to detect when stream completes.
     */
    private volatile CountDownLatch streamCompleted;

    /**
     * Current reconnect backoff delay. Doubles after each failure, capped at {@link #MAX_RECONNECT_DELAY_MS}.
     * Reset to {@link #MIN_RECONNECT_DELAY_MS} on successful connection.
     */
    private final AtomicLong currentReconnectDelayMs = new AtomicLong(MIN_RECONNECT_DELAY_MS);

    /**
     * Epoch-millisecond timestamp before which no reconnection attempt should be made.
     * Set in {@code onError()} and cleared on successful connection.
     */
    private final AtomicLong reconnectNotBefore = new AtomicLong(0L);

    /**
     * Creates a new {@code WorkerJobFetcher} instance.
     *
     * @param workerControllerServiceStub the gRPC worker controller service stub.
     * @param workerQueueRegistry the worker queue registry.
     * @param executionKilledManager the execution killed manager.
     * @param clusterEventQueue the worker-local cluster-event broadcast queue used to relay events
     *                          received from the controller to in-process subscribers. May be {@code null}
     *                          when the process has direct access to the shared broadcast queue (see
     *                          {@link #WORKER_LOCAL_CLUSTER_EVENTS}).
     * @param metadataChangeHandlers worker-side handlers invoked for each
     *                               {@link WorkerBroadcastEvent.MetadataChangeEvent} received from the controller
     *                               and on stream (re-)connection. Typically one handler per cached metastore;
     *                               may be empty on workers that do not participate in metastore caching.
     */
    @Inject
    public WorkerJobFetcher(final WorkerControllerServiceStub workerControllerServiceStub,
        final WorkerQueueRegistry workerQueueRegistry,
        final ExecutionKilledManager executionKilledManager,
        @Nullable @Named(WORKER_LOCAL_CLUSTER_EVENTS) final BroadcastQueueInterface<ClusterEvent> clusterEventQueue,
        final List<WorkerMetadataChangeHandler> metadataChangeHandlers) {
        super(WorkerJobFetcher.class.getSimpleName());
        this.workerQueueRegistry = workerQueueRegistry;
        this.workerControllerServiceStub = workerControllerServiceStub;
        this.executionKilledManager = executionKilledManager;
        this.clusterEventQueue = clusterEventQueue;
        this.metadataChangeHandlers = metadataChangeHandlers;
    }

    /**
     * Initialize the fetcher.
     *
     * @param workerContext the worker context.
     */
    public synchronized void init(final WorkerContext workerContext) {
        this.workerJobQueue = workerQueueRegistry.getOrCreate(workerContext, WorkerJob.class);
        this.workerContext = workerContext;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    protected void doOnLoop() throws Exception {
        // Check if we need to establish a new stream connection
        if (requestObserverRef.get() == null) {
            long remainingBackoffMs = reconnectNotBefore.get() - System.currentTimeMillis();
            if (remainingBackoffMs > 0) {
                // Honor the exponential backoff window; wake up periodically so stop signals are respected
                Thread.sleep(Math.min(remainingBackoffMs, PERMIT_CHECK_INTERVAL.toMillis()));
                return;
            }
            startStream();
            return;
        }

        // Stream is active - wait for a short period and check for capacity changes
        boolean completed = streamCompleted.await(PERMIT_CHECK_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);

        if (completed) {
            // Stream ended, will reconnect on next loop iteration
            return;
        }

        // Check if capacity has changed and send permit update if needed
        sendPermitUpdateIfNeeded();
    }

    /**
     * Starts a new bidirectional stream to the controller.
     */
    private void startStream() {
        // Reset state for new connection
        lastSentPermits.set(-1);
        streamCompleted = new CountDownLatch(1);

        ClientResponseObserver<WorkerJobRequest, WorkerJobResponse> responseObserver = new ClientResponseObserver<>() {

            @Override
            public void beforeStart(ClientCallStreamObserver<WorkerJobRequest> requestStream) {
                requestObserverRef.set(requestStream);
            }

            @Override
            public void onNext(WorkerJobResponse response) {
                handleJobResponse(response);
            }

            @Override
            public void onError(Throwable t) {
                if (!isRunning()) {
                    log.debug("Stream closed during shutdown: {}", t.getMessage());
                    // log with WARN level if stream fails during normal operation because it will be automatically retried and can indicate transient issues
                } else if (t.getCause() != null) {
                    log.warn("Stream error: {}. Cause: {}", t.getMessage(), t.getCause().getMessage());
                } else {
                    log.warn("Stream error: {}", t.getMessage());
                }
                requestObserverRef.set(null);
                scheduleReconnectBackoff();
                streamCompleted.countDown();
            }

            @Override
            public void onCompleted() {
                log.trace("Stream completed by server");
                requestObserverRef.set(null);
                streamCompleted.countDown();
            }
        };

        // Start the bidirectional stream
        workerControllerServiceStub.streamWorkerJobs(responseObserver);

        // Send initial connection request with connection info and initial permits.
        // requestObserverRef is set by beforeStart() which is called synchronously
        // during streamWorkerJobs(), but an onError() callback on another thread
        // may have already nulled it if the stream failed immediately.
        ClientCallStreamObserver<WorkerJobRequest> requestStream = requestObserverRef.get();
        if (requestStream != null) {
            sendInitialRequest(requestStream);
        } else {
            log.debug("Stream failed before initial request could be sent, will retry on next loop");
        }
    }

    /**
     * Sends the initial request with connection info and initial permits.
     */
    private void sendInitialRequest(ClientCallStreamObserver<WorkerJobRequest> requestStream) {
        int initialPermits = calculatePermits();

        String workerGroupId = workerContext.workerGroupId();

        // Advertise the worker's true maximum in-flight capacity: threads currently
        // executing plus jobs pending in the buffer. Controller bases its reservation
        // math (guaranteedCapacity / sharedCapacity) on this value.
        int maxConcurrency = workerContext.workerThreads() + workerJobQueue.capacity();

        WorkerJobRequest.Builder requestBuilder = WorkerJobRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerContext))
            .setPermits(initialPermits)
            .setConnectionInfo(
                WorkerConnectionInfo.newBuilder()
                    .setWorkerId(workerContext.workerId())
                    .setWorkerGroupId(workerGroupId)
                    .setMaxConcurrency(maxConcurrency)
                    .build()
            );
        addPendingCompletions(requestBuilder);

        doSend(requestStream, requestBuilder.build());
        lastSentPermits.set(initialPermits);
        // Connection established - reset backoff so the next disconnection starts from the minimum delay
        currentReconnectDelayMs.set(MIN_RECONNECT_DELAY_MS);
        reconnectNotBefore.set(0L);
        log.info(
            "Connected to controller: workerId={}, workerGroup={}, maxConcurrency={}, initialPermits={}",
            workerContext.workerId(),
            workerGroupId,
            maxConcurrency,
            initialPermits
        );
        for (WorkerMetadataChangeHandler handler : metadataChangeHandlers) {
            try {
                handler.onReconnect();
            } catch (Exception e) {
                log.warn("Metadata-change handler {} failed onReconnect: {}",
                    handler.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * Handles a job response from the controller.
     */
    private void handleJobResponse(WorkerJobResponse response) {
        ClientCallStreamObserver<WorkerJobRequest> observer = requestObserverRef.get();
        if (observer == null || !isRunning()) {
            return;
        }

        // Process broadcast events (kill commands, cluster events, metadata changes)
        for (ByteString eventData : response.getEventsList()) {
            try {
                WorkerBroadcastEvent event = MessageFormats.JSON.fromByteString(eventData, WorkerBroadcastEvent.class);
                onBroadcastEvent(event);
            } catch (Exception e) {
                log.error("Error processing broadcast event: {}", e.getMessage(), e);
            }
        }

        int receivedJobs = 0;
        for (WorkerJobPayload payload : response.getJobsList()) {
            try {
                String jobId = payload.getJobId();
                WorkerJob job = MessageFormats.JSON.fromByteString(payload.getJobData(), WorkerJob.class);

                log.debug("Received job: {}", jobId);

                // Put job in local queue (blocking if full - provides local backpressure)
                workerJobQueue.put(job);
                receivedJobs++;

            } catch (Exception e) {
                log.error("Error processing job payload: {}", e.getMessage(), e);
            }
        }

        // After buffering jobs, push an updated permit value back. Event-only
        // responses don't need this update.
        if (receivedJobs > 0) {
            sendPermitsForReceivedJobs(observer, calculatePermits(), receivedJobs);
        }
    }

    /**
     * Routes a single decoded {@link WorkerBroadcastEvent} to its handler. Package-private for
     * unit testing.
     */
    void onBroadcastEvent(WorkerBroadcastEvent event) {
        switch (event) {
            case WorkerBroadcastEvent.KillEvent killEvent ->
                executionKilledManager.onKillReceived(killEvent.payload());
            case WorkerBroadcastEvent.ClusterBroadcast clusterBroadcast -> {
                if (clusterEventQueue != null) {
                    log.debug("Received cluster event via gRPC: type={}", clusterBroadcast.payload().eventType());
                    try {
                        clusterEventQueue.emit(clusterBroadcast.payload());
                    } catch (QueueException e) {
                        log.error("Error emitting cluster event to local queue: {}", e.getMessage(), e);
                    }
                }
            }
            case WorkerBroadcastEvent.MetadataChangeEvent metadataChange -> {
                if (!metadataChangeHandlers.isEmpty()) {
                    log.debug("Received metadata change via gRPC: type={}, tenantId={}, namespace={}",
                        metadataChange.payload().type(),
                        metadataChange.payload().tenantId(),
                        metadataChange.payload().namespace());
                    for (WorkerMetadataChangeHandler handler : metadataChangeHandlers) {
                        try {
                            handler.onMetadataChange(metadataChange.payload());
                        } catch (Exception e) {
                            log.warn("Metadata-change handler {} failed: {}",
                                handler.getClass().getSimpleName(), e.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * Sends an updated permit value after buffering jobs received from the controller,
     * piggy-backing any pending completion signals.
     */
    private void sendPermitsForReceivedJobs(ClientCallStreamObserver<WorkerJobRequest> observer, int permits, int receivedJobs) {
        WorkerJobRequest.Builder builder = WorkerJobRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerContext))
            .setPermits(permits);
        int completions = addPendingCompletions(builder);

        try {
            doSend(observer, builder.build());
            lastSentPermits.set(permits);
            log.trace("Sent permits={}, receivedJobs={}, completions={}", permits, receivedJobs, completions);
        } catch (Exception e) {
            log.error("Error sending permits: {}", e.getMessage());
        }
    }

    /**
     * Calculates the number of permits to request based on local queue remaining capacity.
     */
    private int calculatePermits() {
        return workerJobQueue.remainingCapacity();
    }

    /**
     * Sends a permit update to the controller if capacity has changed since last
     * send or if there are pending completion notifications to flush. The completion
     * piggy-back ensures terminal-state signals don't sit indefinitely when permits
     * happen to be stable.
     */
    private void sendPermitUpdateIfNeeded() {
        ClientCallStreamObserver<WorkerJobRequest> observer = requestObserverRef.get();
        if (observer == null) {
            return;
        }

        int currentPermits = calculatePermits();
        int lastPermits = lastSentPermits.get();

        if (currentPermits != lastPermits || !pendingCompletions.isEmpty()) {
            sendPermits(observer, currentPermits);
        }
    }

    /**
     * Records a terminal-state signal for {@code jobId} to be flushed to the owning
     * controller on the next outgoing {@link WorkerJobRequest}. The controller uses
     * this to release the per-queue bucket slot reserved for the job, so reservations
     * hold across the whole job lifetime instead of only until receipt.
     */
    public void onJobCompleted(String jobId) {
        if (jobId == null || jobId.isEmpty()) {
            return;
        }
        pendingCompletions.offer(jobId);
    }

    /**
     * Drains {@link #pendingCompletions} into the request builder and returns the
     * number of completions appended. Safe to call when the queue is empty (no-op).
     */
    private int addPendingCompletions(WorkerJobRequest.Builder builder) {
        int added = 0;
        String jobId;
        while ((jobId = pendingCompletions.poll()) != null) {
            builder.addCompletedJobIds(jobId);
            added++;
        }
        return added;
    }

    /**
     * Sends permits to the controller and updates the last sent value.
     */
    private void sendPermits(ClientCallStreamObserver<WorkerJobRequest> observer, int permits) {
        WorkerJobRequest.Builder builder = WorkerJobRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerContext))
            .setPermits(permits);
        int completions = addPendingCompletions(builder);

        try {
            doSend(observer, builder.build());
            lastSentPermits.set(permits);
            log.debug("Sent permit update: permits={}, completions={}", permits, completions);
        } catch (Exception e) {
            log.error("Error sending permit update: {}", e.getMessage());
        }
    }

    /**
     * Records the current backoff delay as the reconnection deadline, then doubles the delay
     * for the next failure, capped at {@link #MAX_RECONNECT_DELAY_MS}.
     */
    private void scheduleReconnectBackoff() {
        long backoffMs = currentReconnectDelayMs.get();
        currentReconnectDelayMs.set(Math.min(currentReconnectDelayMs.get() * 2, MAX_RECONNECT_DELAY_MS));
        reconnectNotBefore.set(System.currentTimeMillis() + backoffMs);
        log.debug("Stream error, will reconnect in {}ms", backoffMs);
    }

    private void doSend(ClientCallStreamObserver<WorkerJobRequest> observer, WorkerJobRequest request) {
        synchronized (streamLock) {
            try {
                observer.onNext(request);
            } catch (IllegalStateException e) {
                log.warn("Stream cancelled, will reconnect: {}", e.getMessage());
                requestObserverRef.set(null);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void cleanup() {
        ClientCallStreamObserver<WorkerJobRequest> observer = requestObserverRef.getAndSet(null);
        if (observer != null) {
            try {
                observer.onCompleted();
            } catch (Exception e) {
                log.debug("Error completing stream on cleanup: {}", e.getMessage());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void signalJobStop() {
        ClientCallStreamObserver<WorkerJobRequest> observer = requestObserverRef.get();
        if (observer != null) {
            try {
                observer.cancel("Worker stopping", null);
            } catch (Exception e) {
                log.debug("Error cancelling stream: {}", e.getMessage());
            }
        }
    }
}
