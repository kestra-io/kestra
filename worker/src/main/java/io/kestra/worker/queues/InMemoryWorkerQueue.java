package io.kestra.worker.queues;

import io.kestra.core.exceptions.KestraRuntimeException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Default in-memory {@link WorkerQueue} based on {@link LinkedBlockingQueue}.
 *
 * @param <T>   the event type.
 */
public class InMemoryWorkerQueue<T> implements WorkerQueue<T> {

    private final int capacity;
    private final LinkedBlockingQueue<T> queue;

    public InMemoryWorkerQueue() {
        this.capacity = Integer.MAX_VALUE;
        this.queue = new LinkedBlockingQueue<>(capacity);
    }
    
    public InMemoryWorkerQueue(int capacity) {
        this.capacity = capacity;
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T poll(Duration timeout) throws InterruptedException {
        return queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<T> poll(int maxMessages, Duration timeout) throws InterruptedException {
        // Wait for the first element with timeout
        T first = queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (first == null) {
            return List.of();
        }

        List<T> results = new ArrayList<>();
        results.add(first);

        if (maxMessages == 1) {
            return results;
        }

        // Drain additional elements up to maxMessages - 1 (non-blocking)
        queue.drainTo(results, maxMessages - 1);

        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(T event) {
        try {
            this.queue.put(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KestraRuntimeException("Thread was interrupted while putting an event in the queue", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int remainingCapacity() {
        return this.queue.remainingCapacity();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int capacity() {
        return this.capacity;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return this.queue.size();
    }
}
