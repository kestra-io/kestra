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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Slf4j
public abstract class AbstractSubscriber<T extends Event> implements QueueSubscriber<T> {
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition unpaused = pauseLock.newCondition();
    private final AtomicReference<State> state = new AtomicReference<>(State.STOPPED);

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
     * is called.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    protected void waitIfPaused() throws InterruptedException {
        // return immediately if not paused.
        if (!this.state.get().equals(State.PAUSED)) {
            return;
        }

        // lock and wait until resumed
        pauseLock.lock();
        try {
            while (this.state.get().equals(State.PAUSED)) {
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

    protected boolean isRunning() {
        return this.state.get() == State.RUNNING;
    }

    protected boolean isPaused() {
        return this.state.get() == State.PAUSED;
    }

    private boolean changeState(State expected, State newState) {
        if (log.isDebugEnabled()) {
            log.debug("{} change state requested {} to {}", logPrefix, expected, newState);
        }

        if (this.state.compareAndSet(expected, newState)) {
            return true;
        }

        throw new IllegalStateException(logPrefix + " illegal state change to " + newState + " from " + expected + ", current state is " + this.state.get());
    }

    protected void markReady() {
        if (log.isDebugEnabled()) {
            log.debug("{} Mark ready received", logPrefix);
        }

        this.changeState(State.STOPPED, State.RUNNING);
    }

    @Override
    public void pause() {
        if (log.isDebugEnabled()) {
            log.debug("{} pause received", logPrefix);
        }

        if (this.state.get() == State.PAUSED) {
            return; // already paused
        }

        this.changeState(State.RUNNING, State.PAUSED);
    }

    @Override
    public void resume() {
        if (this.state.get() == State.STOPPED) {
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("{} resume received", logPrefix);
        }

        pauseLock.lock();
        try {
            if (this.state.get() == State.RUNNING) {
                return; // already running
            }

            if (this.changeState(State.PAUSED, State.RUNNING)) {
                unpaused.signalAll();
            }
        } finally {
            pauseLock.unlock();
        }
    }

    protected void markEnd() {
        if (log.isDebugEnabled()) {
            log.debug("{} mark end received", logPrefix);
        }

        if (this.isRunning()) {
            this.changeState(State.RUNNING, State.STOPPED);
        }

        this.stopped.countDown();
    }

    @Override
    public void close() {
        if (this.state.get() == State.STOPPED) {
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("{} close received", logPrefix);
        }

        // in case it's paused and blocked
        resume();

        // already stopped
        try {
            this.changeState(State.RUNNING, State.STOPPED);
        } catch (IllegalStateException ignored) {
            return;
        }

        // wait for the queue to be stopped
        try {
            stopped.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{} interrupted while waiting to be stopped.", logPrefix);
        }
    }

    public enum State {
        RUNNING,
        PAUSED,
        STOPPED
    }
}
