package io.kestra.worker.senders;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.controller.grpc.OpaqueData;
import io.kestra.controller.messages.BatchMessage;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.worker.WorkerLoop;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.Nullable;

/**
 * Sends worker data to the controller via gRPC.
 * <p>
 * Instances are created by {@link GrpcWorkerIOSenderFactory} with the appropriate
 * event type, gRPC method reference, and {@link SendStrategy}.
 *
 * @param <T> the type of event to send.
 * @see GrpcWorkerIOSenderFactory
 */
public class GrpcWorkerIOSender<T> extends WorkerLoop implements WorkerIOSender {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcWorkerIOSender.class);
    private static final int MAX_BATCH_SIZE = 100; // TODO to test and fine-tune
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);
    /** Throttle applied after a retryable send failure, so a network partition cannot spin the loop. */
    private static final Duration RESEND_BACKOFF = Duration.ofSeconds(1);

    private final WorkerQueueRegistry workerQueueRegistry;
    private final Class<T> eventType;
    private final SendStrategy sendStrategy;
    private final BiConsumer<OpaqueData, StreamObserver<OpaqueData>> grpcSendMethod;
    private final boolean resendOnFailure;
    private WorkerQueue<T> queue;
    private WorkerContext workerContext;
    /** {@code nanoTime} until which the loop throttles after a retryable send failure. */
    private volatile long resendBackoffUntilNanos;
    @Nullable
    private final Function<T, T> fallbackMapperOnResourceExhausted;

    /**
     * Strategy for sending data to the controller.
     */
    enum SendStrategy {
        /** Send each item individually via separate gRPC calls. */
        PER_ITEM,
        /** Send the entire batch in a single gRPC call. */
        BATCH
    }

    /**
     * Creates a new {@code GrpcWorkerIOSender} instance.
     *
     * @param workerQueueRegistry the worker queue factory.
     * @param name the name of the sender.
     * @param eventType the event type.
     * @param sendStrategy the strategy for sending data (per-item or batch).
     * @param grpcSendMethod the gRPC method to call for sending data.
     * @param fallbackMapperOnResourceExhausted optional mapper applied to each item when the server rejects the
     *        message with {@code RESOURCE_EXHAUSTED}; the mapped item is
     *        re-sent once. Pass {@code null} to disable fallback.
     * @param resendOnFailure when {@code true}, a batch that fails to send with a retryable transport error
     *        (e.g. the controller is unreachable during a network partition) is re-queued so the loop
     *        redrives it once the channel recovers, instead of being dropped. Enable for results that must
     *        not be lost; leave {@code false} for best-effort, high-volume streams (logs, metrics) where
     *        re-queuing would risk back-pressuring the worker.
     */
    GrpcWorkerIOSender(final WorkerQueueRegistry workerQueueRegistry,
        final String name,
        final Class<T> eventType,
        final SendStrategy sendStrategy,
        final BiConsumer<OpaqueData, StreamObserver<OpaqueData>> grpcSendMethod,
        @Nullable final Function<T, T> fallbackMapperOnResourceExhausted,
        final boolean resendOnFailure) {
        super(name);
        this.eventType = eventType;
        this.workerQueueRegistry = workerQueueRegistry;
        this.sendStrategy = Objects.requireNonNull(sendStrategy, "sendStrategy must not be null");
        this.grpcSendMethod = Objects.requireNonNull(grpcSendMethod, "grpcSendMethod must not be null");
        this.fallbackMapperOnResourceExhausted = fallbackMapperOnResourceExhausted;
        this.resendOnFailure = resendOnFailure;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public synchronized void init(WorkerContext workerContext) {
        this.queue = workerQueueRegistry.getOrCreate(workerContext, eventType);
        this.workerContext = workerContext;
        this.resendBackoffUntilNanos = System.nanoTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doOnLoop() throws Exception {
        throttleAfterSendFailure();
        send(queue.poll(MAX_BATCH_SIZE, POLL_TIMEOUT));
    }

    /**
     * Sleeps off any remaining {@link #RESEND_BACKOFF} window opened by a retryable send failure.
     * While the controller is unreachable, this keeps a re-queued batch from spinning the loop
     * (poll → fail → re-queue → poll …) into a hot loop. A successful send leaves the window in the
     * past, so the steady state pays no cost.
     */
    private void throttleAfterSendFailure() throws InterruptedException {
        long remainingNanos = resendBackoffUntilNanos - System.nanoTime();
        if (remainingNanos > 0) {
            TimeUnit.NANOSECONDS.sleep(remainingNanos);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void cleanup() throws Exception {
        // Clear the interrupt flag so we can drain the queue without
        // LinkedBlockingQueue.poll() throwing InterruptedException
        // from lockInterruptibly(). We restore it afterwards.
        boolean interrupted = Thread.interrupted();
        try {
            List<T> results;
            do {
                results = queue.poll(MAX_BATCH_SIZE, Duration.ZERO);
                send(results);
            } while (!results.isEmpty());
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        stop(Duration.ZERO); // no need to wait for termination here
    }

    /**
     * Sends the given results to the controller using the configured {@link SendStrategy}.
     *
     * @param results the results to send.
     */
    void send(final List<T> results) {
        if (results == null || results.isEmpty())
            return;

        switch (sendStrategy) {
            case PER_ITEM -> results.forEach(result -> sendOpaqueData(BatchMessage.of(List.of(result))));
            case BATCH -> sendOpaqueData(BatchMessage.of(results));
        }
    }

    private void sendOpaqueData(final BatchMessage<T> batchMessage) {
        OpaqueData request = buildRequest(batchMessage);
        StreamObserver<OpaqueData> baseObserver = fallbackMapperOnResourceExhausted != null
            ? new FallbackOnResourceExhaustedObserver(batchMessage)
            : new ResendOnFailureObserver(batchMessage);
        StreamObserver<OpaqueData> observer = new RetryOnUnauthenticatedObserver(batchMessage, baseObserver);
        grpcSendMethod.accept(request, observer);
    }

    private OpaqueData buildRequest(final BatchMessage<T> batchMessage) {
        return OpaqueData.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerContext))
            .setMessage(MessageFormats.JSON.toByteString(batchMessage))
            .build();
    }

    /**
     * Terminal handler for a send that could not be delivered. When {@link #resendOnFailure} is enabled
     * and the error is a retryable transport failure (the controller is transiently unreachable, e.g. a
     * network partition), the batch is re-queued so the loop redrives it once the channel recovers — the
     * alternative is silently dropping a worker result, leaving a completed task stuck RUNNING. Re-queuing
     * is intentionally bounded to the running loop: past the liveness timeout the worker self-disconnects
     * and the executor resubmits its tasks, so we never re-queue during shutdown (which would also turn the
     * drain in {@link #cleanup()} into an unbounded loop while partitioned).
     */
    private void onSendFailed(final BatchMessage<T> batchMessage, final Throwable t) {
        if (resendOnFailure && isRunning() && isRetryable(t)) {
            LOG.warn("Retryable error while sending {} — re-queuing {} item(s) for redelivery",
                eventType.getSimpleName(), batchMessage.records().size(), t);
            batchMessage.records().forEach(queue::put);
            resendBackoffUntilNanos = System.nanoTime() + RESEND_BACKOFF.toNanos();
        } else {
            LOG.error("Error while sending request", t);
        }
    }

    /**
     * Whether a send failure is a transient transport error worth re-queuing. {@code UNAVAILABLE} covers a
     * partition or a controller restart; {@code DEADLINE_EXCEEDED} a send that timed out in transit.
     */
    private static boolean isRetryable(final Throwable t) {
        return t instanceof StatusRuntimeException sre
            && switch (sre.getStatus().getCode()) {
                case UNAVAILABLE, DEADLINE_EXCEEDED -> true;
                default -> false;
            };
    }

    /**
     * A per-call {@link StreamObserver} that retries once when the server
     * rejects the request with {@code UNAUTHENTICATED} (e.g. expired access token).
     * <p>
     * The retry re-invokes the gRPC method, which goes through the auth interceptor
     * again and picks up a refreshed token. The delegate observer handles any further
     * errors, preventing infinite retry loops.
     */
    private class RetryOnUnauthenticatedObserver implements StreamObserver<OpaqueData> {

        private final BatchMessage<T> originalBatch;
        private final StreamObserver<OpaqueData> delegate;

        RetryOnUnauthenticatedObserver(final BatchMessage<T> originalBatch, final StreamObserver<OpaqueData> delegate) {
            this.originalBatch = originalBatch;
            this.delegate = delegate;
        }

        @Override
        public void onNext(OpaqueData value) {
            delegate.onNext(value);
        }

        @Override
        public void onError(Throwable t) {
            if (isUnauthenticated(t)) {
                LOG.warn("UNAUTHENTICATED error while sending {}, retrying once", eventType.getSimpleName());
                grpcSendMethod.accept(buildRequest(originalBatch), delegate);
            } else {
                delegate.onError(t);
            }
        }

        @Override
        public void onCompleted() {
            delegate.onCompleted();
        }

        private static boolean isUnauthenticated(final Throwable t) {
            return t instanceof StatusRuntimeException sre
                && sre.getStatus().getCode() == Status.Code.UNAUTHENTICATED;
        }
    }

    /**
     * A per-call {@link StreamObserver} that retries with a fallback message when the server
     * rejects the original message with {@code RESOURCE_EXHAUSTED} (e.g. message too large).
     */
    private class FallbackOnResourceExhaustedObserver implements StreamObserver<OpaqueData> {

        private final BatchMessage<T> originalBatch;

        FallbackOnResourceExhaustedObserver(final BatchMessage<T> originalBatch) {
            this.originalBatch = originalBatch;
        }

        @Override
        public void onNext(OpaqueData value) {
        }

        @Override
        public void onError(Throwable t) {
            if (isResourceExhausted(t) && fallbackMapperOnResourceExhausted != null) {
                List<T> fallbackItems = originalBatch.records().stream()
                    .map(fallbackMapperOnResourceExhausted)
                    .filter(Objects::nonNull)
                    .toList();
                if (!fallbackItems.isEmpty()) {
                    // Route the fallback (failed-state) resend through the same resend handler as the primary
                    // send: if it too fails with a retryable transport error, the failed-state result is
                    // re-queued instead of dropped — otherwise the task would still be left stuck RUNNING.
                    BatchMessage<T> fallbackBatch = BatchMessage.of(fallbackItems);
                    grpcSendMethod.accept(buildRequest(fallbackBatch), new ResendOnFailureObserver(fallbackBatch));
                }
            } else {
                onSendFailed(originalBatch, t);
            }
        }

        @Override
        public void onCompleted() {
        }

        private static boolean isResourceExhausted(final Throwable t) {
            return t instanceof StatusRuntimeException sre
                && sre.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED;
        }
    }

    /**
     * Default per-call observer: ignores successful responses and routes errors to
     * {@link #onSendFailed(BatchMessage, Throwable)}, which re-queues retryable failures when
     * {@link #resendOnFailure} is set and otherwise logs and drops.
     */
    private class ResendOnFailureObserver implements StreamObserver<OpaqueData> {

        private final BatchMessage<T> batchMessage;

        ResendOnFailureObserver(final BatchMessage<T> batchMessage) {
            this.batchMessage = batchMessage;
        }

        @Override
        public void onNext(OpaqueData value) {
        }

        @Override
        public void onError(Throwable t) {
            onSendFailed(batchMessage, t);
        }

        @Override
        public void onCompleted() {
        }
    }
}
