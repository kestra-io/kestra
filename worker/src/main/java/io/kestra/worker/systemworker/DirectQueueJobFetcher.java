package io.kestra.worker.systemworker;

import java.time.Duration;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.utils.Either;
import io.kestra.core.worker.WorkerQueues;
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.worker.WorkerLoop;
import io.kestra.worker.fetchers.JobFetcher;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Job fetcher used by the {@link SystemWorker} hosted inside the executor /
 * standalone process. Subscribes directly to the {@link WorkerJobEvent}
 * keyed-dispatch queue with the reserved {@link WorkerQueues#SYSTEM_ID}
 * Worker Queue, bypassing the gRPC pull path used by regular workers.
 * <p>
 * Mirrors the lifecycle shape of {@link io.kestra.worker.fetchers.WorkerJobFetcher}
 * (init / pause / resume / cleanup) so callers can host it the same way, but
 * is a separate concrete type with no shared interface: regular workers and
 * the {@code SystemWorker} are wired with their own fetcher class explicitly.
 */
@Singleton
@Requires(property = "kestra.server-type", pattern = "(EXECUTOR|STANDALONE)")
@Slf4j
public class DirectQueueJobFetcher extends WorkerLoop implements JobFetcher {

    private static final Duration IDLE_SLEEP = Duration.ofSeconds(1);

    private final KeyedDispatchQueueInterface<WorkerJobEvent> workerJobQueue;
    private final WorkerQueueRegistry workerQueueRegistry;

    private WorkerQueue<WorkerJob> inMemoryQueue;
    private QueueSubscriber<WorkerJobEvent> subscription;

    @Inject
    public DirectQueueJobFetcher(
        final KeyedDispatchQueueInterface<WorkerJobEvent> workerJobQueue,
        final WorkerQueueRegistry workerQueueRegistry
    ) {
        super(DirectQueueJobFetcher.class.getSimpleName());
        this.workerJobQueue = workerJobQueue;
        this.workerQueueRegistry = workerQueueRegistry;
    }

    /**
     * Initialize the fetcher and subscribe to the reserved {@code "system"}
     * routing key. Must be called before the loop starts.
     */
    public synchronized void init(final WorkerContext context) {
        this.inMemoryQueue = workerQueueRegistry.getOrCreate(context, WorkerJob.class);
        this.subscription = workerJobQueue
            .subscriber(WorkerQueues.SYSTEM_ID)
            .subscribe(this::onEvent);
        log.info("SystemWorker subscribed to Worker Queue '{}'", WorkerQueues.SYSTEM_ID);
    }

    private void onEvent(final Either<WorkerJobEvent, DeserializationException> either) {
        if (either.isLeft()) {
            inMemoryQueue.put(either.getLeft().job());
        } else {
            DeserializationException error = either.getRight();
            log.error("Failed to deserialize SystemTask WorkerJobEvent: {}", error.getMessage(), error);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The {@link QueueSubscriber} delivers events asynchronously into the
     * in-memory queue; this loop body only needs to keep the worker IO
     * thread alive until {@link #stop(Duration)} is invoked.
     */
    @Override
    protected void doOnLoop() throws Exception {
        Thread.sleep(IDLE_SLEEP.toMillis());
    }

    @Override
    public void pause() {
        if (subscription != null) {
            subscription.pause();
        }
        super.pause();
    }

    @Override
    public void resume() {
        if (subscription != null) {
            subscription.resume();
        }
        super.resume();
    }

    @Override
    protected void cleanup() {
        if (subscription != null) {
            try {
                subscription.close();
            } catch (Exception e) {
                log.warn("Error closing SystemWorker queue subscription: {}", e.getMessage(), e);
            }
        }
    }
}
