package io.kestra.queue;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public abstract class AbstractSubscriber<T extends GenericEvent> extends AbstractQueue<T> implements QueueSubscriber<T> {
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition unpaused = pauseLock.newCondition();

    private final AtomicReference<State> state = new AtomicReference<>(State.STOPPED);

    public AbstractSubscriber(Class<T> cls, ExecutorService executorService) {
        super(cls, executorService);
    }

    protected void waitIfPaused() throws InterruptedException {
        if (!this.state.get().equals(State.PAUSED)) {
            return; // return immediately if not paused.
        }

        pauseLock.lock();
        try {
            while (this.state.get().equals(State.PAUSED)) {
                log.debug("Paused. Waiting for {} to resume", AbstractSubscriber.class.getSimpleName());
                unpaused.await(); // Wait until resume() signals
                log.debug("Resumed");
            }
        } finally {
            pauseLock.unlock();
        }
    }

    protected boolean isRunning() {
        return this.state.get() == State.RUNNING;
    }

    protected void markReady() {
        if (!this.state.compareAndSet(State.STOPPED, State.RUNNING)) {
            throw new IllegalStateException("Subscriber can't be ready, current state: " + this.state.get());
        }
    }


    public void pause() {
        this.state.set(State.PAUSED);
    }

    public void resume() {
        pauseLock.lock();
        try {
            if (state.compareAndSet(State.PAUSED, State.RUNNING)) {
                unpaused.signalAll();
            }
        } finally {
            pauseLock.unlock();
        }
    }

    protected void markEnd() {

        if (!this.state.compareAndSet(State.STOPPED, State.RUNNING)) {
            throw new IllegalStateException("Subscriber can't be ready, current state: " + this.state.get());
        }

        this.stopped.countDown();
    }

    public void close() {
        // in case it's paused and blocked
        resume();

        // already stopped
        if (!this.state.compareAndSet(State.RUNNING, State.STOPPED)) {
            return;
        }

        // wait for the queue to be stooped
        try {
            stopped.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for {} to be stopped.", this.getClass().getSimpleName());
        }
    }

    public enum State {
        RUNNING,
        PAUSED,
        STOPPED
    }
}
