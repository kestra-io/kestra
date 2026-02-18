package io.kestra.queue;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.Event;
import io.kestra.core.utils.Either;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Slf4j
public abstract class AbstractSubscriber<T extends Event> implements QueueSubscriber<T> {
    private static final long CLOSE_TIMEOUT_SECONDS = 30;

    private final CountDownLatch stopped = new CountDownLatch(1);
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition unpaused = pauseLock.newCondition();
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);

    protected final Class<T> cls;
    protected final QueueService queueService;
    protected final String logPrefix;
    protected final Timer timer;
    protected final Timer batchTimer;

    public AbstractSubscriber(Class<T> cls, String queueName, QueueService queueService, MetricRegistry metricRegistry) {
        this.cls = cls;
        this.logPrefix = "[%s]".formatted(this.cls.getSimpleName());
        this.queueService = queueService;
        this.timer = metricRegistry.timer(
            MetricRegistry.METRIC_QUEUE_CONSUME_DURATION,
            MetricRegistry.METRIC_QUEUE_CONSUME_DURATION_DESCRIPTION,
            MetricRegistry.TAG_QUEUE_NAME, queueName);
        this.batchTimer = metricRegistry.timer(
            MetricRegistry.METRIC_QUEUE_CONSUME_BATCH_DURATION,
            MetricRegistry.METRIC_QUEUE_CONSUME_BATCH_DURATION_DESCRIPTION,
            MetricRegistry.TAG_QUEUE_NAME, queueName);
    }

    /**
     * Process a message:
     * <ul>
     *   <li>deserialize the message</li>
     *   <li>call the consumer</li>
     *   <li>if there is an exception, log it and rethrow it</li>
     * </ul>
     */
    protected void processMessage(byte[] message, Consumer<Either<T, DeserializationException>> consumer) {
        timer.record(() -> {
            Either<T, DeserializationException> event = this.queueService.deserialize(this.cls, message);
            if (log.isDebugEnabled()) {
                if (event.isLeft()) {
                    log.debug("[{}] receive a message with key {}", cls.getSimpleName(), event.getLeft().key());
                } else {
                    log.debug("[{}] receive a message with a deserialization error: {}", cls.getSimpleName(), event.getRight().getMessage());
                }
            }

            try {
                consumer.accept(event);
            } catch (RuntimeException e) {
                if (event.isLeft()) {
                    log.error(
                        "[{}] message with id '{}' fail and was resubmitted to active queue",
                        cls.getSimpleName(),
                        event.getLeft().key(),
                        e
                    );
                    log.debug(new String(message));
                } else {
                    log.error(
                        "[{}] message fail and was resubmitted to active queue, it was a deserialization error message",
                        cls.getSimpleName(),
                        e
                    );
                    log.debug(new String(message));
                }
                throw e; // TODO check if it would not be better to markEnd()
            }
        });
    }

    /**
     * Process a batch of messages:
     * <ul>
     *   <li>deserialize each message</li>
     *   <li>call the consumer</li>
     *   <li>if there is an exception, process messages one by one to locate the failing message</li>
     * </ul>
     */
    protected void processBatchMessages(List<byte[]> messages, Consumer<List<Either<T, DeserializationException>>> consumer) {
        batchTimer.record(() -> {
            List<Either<T, DeserializationException>> events = messages.stream().map(message -> this.queueService.deserialize(this.cls, message)).toList();
            if (log.isDebugEnabled()) {
                log.debug("[{}] receive a batch of {} message", cls.getSimpleName(), events.size());
            }

            try {
                consumer.accept(events);
            } catch (RuntimeException e) {
                // process messages one by one in case of an error, so we log the message that causes the error.
                messages.forEach(message -> processMessage(message, m -> consumer.accept(List.of(m))));
            }
        });
    }

    /**
     * Blocks the calling thread if this subscriber is currently paused.
     * <p>
     * This method should be called by subclasses before processing each message
     * to honor the pause/resume contract. If the subscriber is not paused, this
     * method returns immediately. If paused, it blocks until {@link #resume()}
     * is called or the subscriber is closed.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    protected void waitIfPaused() throws InterruptedException {
        // return immediately if not paused
        if (!this.paused.get()) {
            return;
        }

        // lock and wait until resumed or closed
        pauseLock.lock();
        try {
            while (this.paused.get() && this.active.get()) {
                if (log.isDebugEnabled()) {
                    log.debug("{} paused, waiting to resume", logPrefix);
                }

                unpaused.await(); // Wait until resume() signals

                if (log.isDebugEnabled()) {
                    log.debug("{} resumed", logPrefix);
                }
            }
        } finally {
            pauseLock.unlock();
        }
    }

    protected boolean isActive() {
        return this.active.get();
    }

    protected boolean isPaused() {
        return this.paused.get();
    }

    protected void markReady() {
        if (log.isDebugEnabled()) {
            log.debug("{} mark ready received", logPrefix);
        }

        this.active.set(true);
    }

    /** {@inheritDoc} */
    @Override
    public void pause() {
        if (log.isDebugEnabled()) {
            log.debug("{} pause received", logPrefix);
        }

        pauseLock.lock();
        try {
            this.paused.set(true);
        } finally {
            pauseLock.unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void resume() {
        if (log.isDebugEnabled()) {
            log.debug("{} resume received", logPrefix);
        }

        pauseLock.lock();
        try {
            if (this.paused.compareAndSet(true, false)) {
                unpaused.signalAll();
            }
        } finally {
            pauseLock.unlock();
        }
    }

    /**
     * Marks the subscriber as stopped and unblocks any waiting threads.
     */
    protected void markEnd() {
        if (log.isDebugEnabled()) {
            log.debug("{} mark end received", logPrefix);
        }
        this.active.set(false);
        this.stopped.countDown();
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        if (!this.active.compareAndSet(true, false)) {
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("{} close received", logPrefix);
        }

        // unblock waitIfPaused() if paused
        resume();

        // wait for the subscriber loop to finish
        try {
            if (!stopped.await(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("{} timed out after {}s waiting for subscriber to stop", logPrefix, CLOSE_TIMEOUT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{} interrupted while waiting to be stopped.", logPrefix);
        }
    }
}
