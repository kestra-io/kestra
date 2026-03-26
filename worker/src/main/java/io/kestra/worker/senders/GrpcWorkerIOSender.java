package io.kestra.worker.senders;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
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
 * Instances are created by {@link WorkerIOSenderFactory} with the appropriate
 * event type, gRPC method reference, and {@link SendStrategy}.
 *
 * @param <T> the type of event to send.
 * @see WorkerIOSenderFactory
 */
public class GrpcWorkerIOSender<T> extends WorkerLoop implements WorkerIOSender {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcWorkerIOSender.class);
    private static final int MAX_BATCH_SIZE = 100; // TODO to test and fine-tune
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);

    /** Default response observer: ignores successful responses, logs errors. */
    private static final StreamObserver<OpaqueData> DEFAULT_OBSERVER = new StreamObserver<>() {
        @Override
        public void onNext(OpaqueData value) {
        }

        @Override
        public void onError(Throwable t) {
            LOG.error("Error while sending request", t);
        }

        @Override
        public void onCompleted() {
        }
    };

    private final WorkerQueueRegistry workerQueueRegistry;
    private final Class<T> eventType;
    private final SendStrategy sendStrategy;
    private final BiConsumer<OpaqueData, StreamObserver<OpaqueData>> grpcSendMethod;
    private WorkerQueue<T> queue;
    private WorkerContext workerContext;
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
     */
    GrpcWorkerIOSender(final WorkerQueueRegistry workerQueueRegistry,
        final String name,
        final Class<T> eventType,
        final SendStrategy sendStrategy,
        final BiConsumer<OpaqueData, StreamObserver<OpaqueData>> grpcSendMethod,
        @Nullable final Function<T, T> fallbackMapperOnResourceExhausted) {
        super(name);
        this.eventType = eventType;
        this.workerQueueRegistry = workerQueueRegistry;
        this.sendStrategy = Objects.requireNonNull(sendStrategy, "sendStrategy must not be null");
        this.grpcSendMethod = Objects.requireNonNull(grpcSendMethod, "grpcSendMethod must not be null");
        this.fallbackMapperOnResourceExhausted = fallbackMapperOnResourceExhausted;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public synchronized void init(WorkerContext workerContext) {
        this.queue = workerQueueRegistry.getOrCreate(workerContext, eventType);
        this.workerContext = workerContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doOnLoop() throws Exception {
        send(queue.poll(MAX_BATCH_SIZE, POLL_TIMEOUT));
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
        StreamObserver<OpaqueData> observer = fallbackMapperOnResourceExhausted != null ? new FallbackOnResourceExhaustedObserver(batchMessage) : DEFAULT_OBSERVER;
        grpcSendMethod.accept(request, observer);
    }

    private OpaqueData buildRequest(final BatchMessage<T> batchMessage) {
        return OpaqueData.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerContext))
            .setMessage(MessageFormats.JSON.toByteString(batchMessage))
            .build();
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
                    grpcSendMethod.accept(buildRequest(BatchMessage.of(fallbackItems)), DEFAULT_OBSERVER);
                }
            } else {
                LOG.error("Error while sending request", t);
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
}
