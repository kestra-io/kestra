package io.kestra.worker.queues;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public abstract class AbstractDelegateWorkerQueue<T> implements WorkerQueue<T> {

    private final WorkerQueue<T> queue;

    public AbstractDelegateWorkerQueue(final WorkerQueue<T> queue) {
        this.queue = Objects.requireNonNull(queue, "queue must not be null.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T poll(Duration timeout) throws InterruptedException {
        return queue.poll(timeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<T> poll(int maxMessages, Duration timeout) throws InterruptedException {
        return queue.poll(maxMessages, timeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(T event) {
        queue.put(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int capacity() {
        return queue.capacity();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return queue.size();
    }
}
