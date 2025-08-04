package io.kestra.worker.queues;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Default in-memory {@link WorkerQueue} based on {@link LinkedBlockingQueue}.
 *
 * @param <T>   the vent type.
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
        List<T> results = new ArrayList<>();

        // Wait for the first element with timeout
        T first = queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (first == null) {
            return results;
        }

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
            throw new RuntimeException(e);
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
