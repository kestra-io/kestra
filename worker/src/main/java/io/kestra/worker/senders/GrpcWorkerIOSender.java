package io.kestra.worker.senders;

import io.kestra.controller.grpc.OpaqueData;
import io.kestra.controller.messages.BatchMessage;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.worker.WorkerLoop;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.senders.internals.LogStreamObserver;
import io.grpc.stub.StreamObserver;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

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

    private static final int MAX_BATCH_SIZE = 100; // TODO to test and fine-tune

    private final WorkerQueueRegistry workerQueueRegistry;
    private final Class<T> eventType;
    private final SendStrategy sendStrategy;
    private final BiConsumer<OpaqueData, StreamObserver<OpaqueData>> grpcSendMethod;
    private WorkerQueue<T> queue;
    private WorkerContext workerContext;

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
     * @param workerQueueRegistry   the worker queue factory.
     * @param name                  the name of the sender.
     * @param eventType             the event type.
     * @param sendStrategy          the strategy for sending data (per-item or batch).
     * @param grpcSendMethod        the gRPC method to call for sending data.
     */
    GrpcWorkerIOSender(final WorkerQueueRegistry workerQueueRegistry,
                              final String name,
                              final Class<T> eventType,
                              final SendStrategy sendStrategy,
                              final BiConsumer<OpaqueData, StreamObserver<OpaqueData>> grpcSendMethod) {
        super(name);
        this.eventType = eventType;
        this.workerQueueRegistry = workerQueueRegistry;
        this.sendStrategy = Objects.requireNonNull(sendStrategy, "sendStrategy must not be null");
        this.grpcSendMethod = Objects.requireNonNull(grpcSendMethod, "grpcSendMethod must not be null");
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
        send(queue.poll(MAX_BATCH_SIZE, Duration.ofMillis(Long.MAX_VALUE)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void cleanup() throws Exception {
        List<T> results;
        do {
            results = queue.poll(MAX_BATCH_SIZE, Duration.ZERO);
            send(results);
        } while (!results.isEmpty());
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
        if (results.isEmpty()) return;

        switch (sendStrategy) {
            case PER_ITEM -> results.forEach(result -> sendOpaqueData(BatchMessage.of(List.of(result))));
            case BATCH -> sendOpaqueData(BatchMessage.of(results));
        }
    }

    private void sendOpaqueData(final BatchMessage<T> batchMessage) {
        OpaqueData request = OpaqueData
            .newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerContext))
            .setMessage(MessageFormats.JSON.toByteString(batchMessage))
            .build();

        grpcSendMethod.accept(request, new LogStreamObserver<>());
    }
}
