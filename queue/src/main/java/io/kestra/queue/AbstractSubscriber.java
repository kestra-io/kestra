package io.kestra.queue;

import io.kestra.core.queues.QueueException;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public abstract class AbstractSubscriber<T extends Event> implements QueueSubscriber<T> {
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition unpaused = pauseLock.newCondition();
    private final AtomicReference<State> state = new AtomicReference<>(State.STOPPED);
    protected final Class<T> cls;
    protected final QueueService queueService;

    public AbstractSubscriber(Class<T> cls, QueueService queueService) {
        this.cls = cls;
        this.queueService = queueService;
    }

    protected void waitIfPaused() throws QueueException {
        // return immediately if not paused.
        if (!this.state.get().equals(State.PAUSED)) {
            return;
        }

        // lock and wait until resumed
        pauseLock.lock();
        try {
            while (this.state.get().equals(State.PAUSED)) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] paused, waiting to resume", this.cls.getSimpleName());
                }

                try {
                    unpaused.await(); // Wait until resume() signals
                } catch (InterruptedException e) {
                    throw new QueueException("[" + this.cls.getSimpleName() + "] interrupted while paused", e);
                }

                if (log.isDebugEnabled()) {
                    log.debug("[{}] resumed", this.cls.getSimpleName());
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
            log.debug("[{}] change state requested {} to {}", this.cls.getSimpleName(), expected, newState);
        }

        if (this.state.compareAndSet(expected, newState)) {
            return true;
        }

        throw new IllegalStateException("[" + this.cls.getSimpleName() + "] illegal state change to " + newState + " from " + newState + ", current state is " + this.state.get());
    }

    protected void markReady() {
        if (log.isDebugEnabled()) {
            log.debug("[{}] Mark ready received", this.cls.getSimpleName());
        }

        this.changeState(State.STOPPED, State.RUNNING);
    }

    public void pause() {
        if (log.isDebugEnabled()) {
            log.debug("[{}] pause received", this.cls.getSimpleName());
        }

        this.changeState(State.RUNNING, State.PAUSED);
    }

    public void resume() {
        if (log.isDebugEnabled()) {
            log.debug("[{}] resume received", this.cls.getSimpleName());
        }

        pauseLock.lock();
        try {
            if (this.changeState(State.PAUSED, State.RUNNING)) {
                unpaused.signalAll();
            }
        } finally {
            pauseLock.unlock();
        }
    }

    protected void markEnd() {
        if (log.isDebugEnabled()) {
            log.debug("[{}] mark end received", this.cls.getSimpleName());
        }

        if (this.isRunning()) {
            this.changeState(State.RUNNING, State.STOPPED);
        }

        this.stopped.countDown();
    }

    public void close() {
        if (log.isDebugEnabled()) {
            log.debug("[{}] close received", this.cls.getSimpleName());
        }

        // in case it's paused and blocked
        if (this.isPaused()) {
            resume();
        }

        // already stopped
        try {
            this.changeState(State.RUNNING, State.STOPPED);
        } catch (IllegalStateException ignored) {
            return;
        }

        // wait for the queue to be stooped
        try {
            stopped.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[{}}] interrupted while waiting to be stopped.", this.cls.getSimpleName());
        }
    }

    public enum State {
        RUNNING,
        PAUSED,
        STOPPED
    }
}
