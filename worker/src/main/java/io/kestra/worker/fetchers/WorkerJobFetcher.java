package io.kestra.worker.fetchers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.server.ClusterEvent;
import io.kestra.core.worker.WorkerBroadcastEvent;
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.worker.WorkerLoop;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.services.ExecutionKilledManager;

import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Component responsible for fetching worker jobs using the pull/ack bidirectional streaming pattern.
 * <p>
 * This client:
 * <ul>
 * <li>Opens a bidirectional stream to the controller on startup</li>
 * <li>Sends initial connection info (workerId, workerGroup, maxConcurrency) + initial permits</li>
 * <li>Receives jobs from the controller and puts them in the local queue</li>
 * <li>Sends ACKs for received jobs along with new permit requests</li>
 * </ul>
 * <p>
 * The controller only sends jobs when the worker has permits (capacity), providing flow control
 * and preventing worker overload.
 * <p>
 * ACKs are "receipt ACKs" (not completion ACKs) - they signal that the job was received and
 * is queued locally, allowing the controller to clean up its in-memory tracking.
 */
@Singleton
@Slf4j
public class WorkerJobFetcher extends WorkerLoop {

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

    private WorkerQueue<WorkerJob> workerJobQueue;
    private WorkerContext workerContext;

    /**
     * Reference to the current stream's request observer for sending permits/acks.
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
     * @param clusterEventQueue the local cluster event broadcast queue for re-emitting events received from the controller.
     */
    @Inject
    public WorkerJobFetcher(final WorkerControllerServiceStub workerControllerServiceStub,
        final WorkerQueueRegistry workerQueueRegistry,
        final ExecutionKilledManager executionKilledManager,
        @Nullable final BroadcastQueueInterface<ClusterEvent> clusterEventQueue) {
        super(WorkerJobFetcher.class.getSimpleName());
        this.workerQueueRegistry = workerQueueRegistry;
        this.workerControllerServiceStub = workerControllerServiceStub;
        this.executionKilledManager = executionKilledManager;
        this.clusterEventQueue = clusterEventQueue;
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

        // workerGroup is optional - use empty string for default group
        final String workerGroup = workerContext.workerGroup();

        WorkerJobRequest.Builder requestBuilder = WorkerJobRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerContext))
            .setPermits(initialPermits)
            .setConnectionInfo(
                WorkerConnectionInfo.newBuilder()
                    .setWorkerId(workerContext.workerId())
                    .setWorkerGroup(workerGroup == null ? "" : workerGroup)
                    .setMaxConcurrency(workerContext.workerThreads())
                    .build()
            );

        doSend(requestStream, requestBuilder.build());
        lastSentPermits.set(initialPermits);
        // Connection established - reset backoff so the next disconnection starts from the minimum delay
        currentReconnectDelayMs.set(MIN_RECONNECT_DELAY_MS);
        reconnectNotBefore.set(0L);
        log.info(
            "Connected to controller: workerId={}, workerGroup={}, maxConcurrency={}, initialPermits={}",
            workerContext.workerId(),
            WorkerGroup.forLog(workerGroup),
            workerContext.workerThreads(),
            initialPermits
        );
    }

    /**
     * Handles a job response from the controller.
     */
    private void handleJobResponse(WorkerJobResponse response) {
        ClientCallStreamObserver<WorkerJobRequest> observer = requestObserverRef.get();
        if (observer == null || !isRunning()) {
            return;
        }

        // Process broadcast events (kill commands, cluster events, etc.)
        for (ByteString eventData : response.getEventsList()) {
            try {
                WorkerBroadcastEvent event = MessageFormats.JSON.fromByteString(eventData, WorkerBroadcastEvent.class);
                switch (event) {
                    case WorkerBroadcastEvent.KillEvent killEvent ->
                        executionKilledManager.onKillReceived(killEvent.payload());
                    case WorkerBroadcastEvent.ClusterBroadcast clusterBroadcast -> {
                        if (clusterEventQueue != null) {
                            log.debug("Received cluster event via gRPC: type={}", clusterBroadcast.payload().eventType());
                            clusterEventQueue.emit(clusterBroadcast.payload());
                        } else {
                            log.warn("Received cluster event but no BroadcastQueue<ClusterEvent> is available, ignoring: type={}", clusterBroadcast.payload().eventType());
                        }
                    }
                }
            } catch (QueueException e) {
                log.error("Error emitting cluster event to local queue: {}", e.getMessage(), e);
            } catch (Exception e) {
                log.error("Error processing broadcast event: {}", e.getMessage(), e);
            }
        }

        List<String> acks = new ArrayList<>();
        for (WorkerJobPayload payload : response.getJobsList()) {
            try {
                String jobId = payload.getJobId();
                WorkerJob job = MessageFormats.JSON.fromByteString(payload.getJobData(), WorkerJob.class);

                log.debug("Received job: {}", jobId);

                // Put job in local queue (blocking if full - provides local backpressure)
                workerJobQueue.put(job);

                // Collect ACK for this job (receipt acknowledgment)
                acks.add(jobId);

            } catch (Exception e) {
                log.error("Error processing job payload: {}", e.getMessage(), e);
            }
        }

        // Send ACKs and request more permits based on remaining capacity
        // Only send if there were jobs (event-only responses don't need permit updates)
        if (!acks.isEmpty()) {
            sendPermitsAndAcks(observer, calculatePermits(), acks);
        }
    }

    /**
     * Sends permits and ACKs to the controller.
     */
    private void sendPermitsAndAcks(ClientCallStreamObserver<WorkerJobRequest> observer, int permits, List<String> acks) {
        WorkerJobRequest request = WorkerJobRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerContext))
            .setPermits(permits)
            .addAllAcknowledgedJobIds(acks)
            .build();

        try {
            doSend(observer, request);
            lastSentPermits.set(permits);
            log.trace("Sent permits={}, acks={}", permits, acks.size());
        } catch (Exception e) {
            log.error("Error sending permits/acks: {}", e.getMessage());
        }
    }

    /**
     * Calculates the number of permits to request based on local queue remaining capacity.
     */
    private int calculatePermits() {
        return workerJobQueue.remainingCapacity();
    }

    /**
     * Sends a permit update to the controller if capacity has changed since last send.
     * This ensures the controller is notified when jobs complete and capacity becomes available.
     */
    private void sendPermitUpdateIfNeeded() {
        ClientCallStreamObserver<WorkerJobRequest> observer = requestObserverRef.get();
        if (observer == null) {
            return;
        }

        int currentPermits = calculatePermits();
        int lastPermits = lastSentPermits.get();

        // Only send if permits have changed
        if (currentPermits != lastPermits) {
            sendPermits(observer, currentPermits);
        }
    }

    /**
     * Sends permits to the controller and updates the last sent value.
     */
    private void sendPermits(ClientCallStreamObserver<WorkerJobRequest> observer, int permits) {
        WorkerJobRequest request = WorkerJobRequest.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerContext))
            .setPermits(permits)
            .build();

        try {
            doSend(observer, request);
            lastSentPermits.set(permits);
            log.debug("Sent permit update: permits={}", permits);
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
