package io.kestra.worker.senders;

import io.kestra.core.worker.models.WorkerContext;
import io.kestra.worker.WorkerLoop;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceStub;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.List;

/**
 * Abstract class for sending worker data.
 */
public abstract class GrpcWorkerIOSender<T> extends WorkerLoop implements WorkerIOSender {

    private static final int MAX_BATCH_SIZE = 100; // TODO to test and fine-tune

    protected final WorkerControllerServiceStub controllerServiceStub;
    private final WorkerQueueRegistry workerQueueRegistry;
    private final Class<T> eventType;
    private WorkerQueue<T> queue;
    protected WorkerContext workerContext;

    /**
     * Creates a new {@code AbstractWorkerIOSender} instance.
     *
     * @param controllerServiceStub the gRPC controller service stub.
     * @param workerQueueRegistry    the worker queue factory.
     * @param name                  the name of the sender.
     * @param eventType             the event type.
     */
    @Inject
    public GrpcWorkerIOSender(final WorkerControllerServiceStub controllerServiceStub, final WorkerQueueRegistry workerQueueRegistry, final String name, final Class<T> eventType) {
        super(name);
        this.eventType = eventType;
        this.workerQueueRegistry = workerQueueRegistry;
        this.controllerServiceStub = controllerServiceStub;
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

    abstract protected void send(final List<T> results);
}
