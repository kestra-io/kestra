package io.kestra.worker.systemworker;

import java.time.Duration;
import java.util.List;

import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.event.DispatchEvent;
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.worker.WorkerLoop;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.senders.WorkerIOSender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Direct-queue analog of {@link io.kestra.worker.senders.GrpcWorkerIOSender}.
 * <p>
 * Polls a per-worker in-memory {@link WorkerQueue} produced by the
 * {@code WorkerJobProcessor} and dispatches each event to the corresponding
 * cross-process {@link DispatchQueueInterface}, bypassing the gRPC controller
 * relay used by regular workers. Used by the {@code SystemWorker} hosted
 * inside the executor / standalone process, which has direct queue access.
 *
 * @param <T> the type of event drained from the in-memory queue.
 */
public class DirectQueueWorkerIOSender<T extends DispatchEvent> extends WorkerLoop implements WorkerIOSender {

    private static final Logger LOG = LoggerFactory.getLogger(DirectQueueWorkerIOSender.class);

    private static final int MAX_BATCH_SIZE = 100;
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);

    private final WorkerQueueRegistry workerQueueRegistry;
    private final DispatchQueueInterface<T> dispatchQueue;
    private final Class<T> eventType;

    private WorkerQueue<T> queue;

    public DirectQueueWorkerIOSender(
        final WorkerQueueRegistry workerQueueRegistry,
        final DispatchQueueInterface<T> dispatchQueue,
        final String name,
        final Class<T> eventType
    ) {
        super(name);
        this.workerQueueRegistry = workerQueueRegistry;
        this.dispatchQueue = dispatchQueue;
        this.eventType = eventType;
    }

    @Override
    public synchronized void init(final WorkerContext workerContext) {
        this.queue = workerQueueRegistry.getOrCreate(workerContext, eventType);
    }

    @Override
    protected void doOnLoop() throws Exception {
        emit(queue.poll(MAX_BATCH_SIZE, POLL_TIMEOUT));
    }

    @Override
    protected void cleanup() {
        // Drain remaining events. Clear the interrupt flag so the blocking
        // poll does not throw immediately; restore it afterwards.
        boolean interrupted = Thread.interrupted();
        try {
            List<T> remaining;
            do {
                remaining = queue.poll(MAX_BATCH_SIZE, Duration.ZERO);
                emit(remaining);
            } while (!remaining.isEmpty());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void stop() {
        stop(Duration.ZERO);
    }

    private void emit(final List<T> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (T event : events) {
            try {
                dispatchQueue.emit(event);
            } catch (QueueException e) {
                LOG.error("Error dispatching {} event to direct queue: {}",
                    eventType.getSimpleName(), e.getMessage(), e);
            }
        }
    }
}
