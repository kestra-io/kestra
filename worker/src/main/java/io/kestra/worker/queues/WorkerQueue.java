package io.kestra.worker.queues;

import java.time.Duration;
import java.util.List;

/**
 * Represents an event queue used for worker intra-processes communication.
 * <p>
 * Implementations of this interface are expected to be in-memory oriented.
 *
 * @param <T> type of the queue.
 * @see io.kestra.core.models.executions.LogEntry
 * @see io.kestra.core.models.executions.MetricEntry
 * @see io.kestra.core.runners.WorkerJob
 * @see io.kestra.core.runners.WorkerTaskResult
 */
public interface WorkerQueue<T> {

    /**
     * Retrieves and removes the head of this queue, waiting up to the specified wait time if necessary.
     *
     * @param timeout the maximum time to wait
     * @return the head of this queue, or null if the specified waiting time elapses before an element is available
     * @throws InterruptedException if interrupted while waiting
     */
    T poll(Duration timeout) throws InterruptedException;

    /**
     * Retrieves and removes up to the specified number of elements from this queue.
     * <p>
     * The method waits up to the specified wait time if necessary for at least one element to become available.
     * If elements are available, the method returns immediately with at most maxMessages elements.
     * If no elements are available, the method blocks for at most the given duration.
     *
     * @param maxMessages the maximum number of messages to retrieve
     * @param timeout     the maximum time to wait
     * @return a list of elements retrieved from the queue (maybe empty if timeout expires)
     * @throws InterruptedException if interrupted while waiting
     */
    List<T> poll(int maxMessages, Duration timeout) throws InterruptedException;

    /**
     * Inserts the specified element into this queue.
     *
     * @param event the element to add
     */
    void put(T event);

    /**
     * Returns the number of additional elements that this queue can ideally accept without blocking.
     *
     * @return the remaining capacity
     */
    int remainingCapacity();

    /**
     * Returns the maximum capacity of this queue.
     *
     * @return the capacity
     */
    int capacity();

    /**
     * Returns the number of elements currently in this queue.
     *
     * @return the number of elements in this queue
     */
    int size();
}
