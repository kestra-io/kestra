package io.kestra.worker.queues;

import io.kestra.core.metrics.MetricRegistry;
import io.micrometer.core.instrument.Counter;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Decorate a queue with monitoring capabilities.
 *
 * @param <T>
 */
public class MonitoredWorkerQueue<T> extends AbstractDelegateWorkerQueue<T> {

    public static final String QUEUE_SIZE = "worker.queue.size";
    public static final String QUEUE_REMAINING_CAPACITY = "worker.queue.remaining.capacity";
    public static final String QUEUE_ENQUEUED = "worker.queue.enqueued";
    public static final String QUEUE_DEQUEUED = "worker.queue.dequeued";

    private final Counter enqueuedCounter;
    private final Counter dequeuedCounter;

    public MonitoredWorkerQueue(MetricRegistry metricRegistry, String queueName, WorkerQueue<T> queue) {
        super(queue);

        String[] tags = new String[]{"name", queueName};
        metricRegistry.gauge(QUEUE_SIZE, "Current number of items in the queue", (Supplier<Integer>) this::size, tags);
        metricRegistry.gauge(QUEUE_REMAINING_CAPACITY, "Remaining capacity in the queue", (Supplier<Integer>) this::remainingCapacity, tags);
        this.enqueuedCounter = metricRegistry.counter(QUEUE_ENQUEUED, "Number of items enqueued", tags);
        this.dequeuedCounter = metricRegistry.counter(QUEUE_DEQUEUED, "Number of items dequeued", tags);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T poll(Duration timeout) throws InterruptedException {
        T item = super.poll(timeout);
        dequeuedCounter.increment();
        return item;
    }

    @Override
    public List<T> poll(int maxMessages, Duration timeout) throws InterruptedException {
        List<T> items = super.poll(maxMessages, timeout);
        dequeuedCounter.increment(items.size());
        return items;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(T event) {
        super.put(event);
        enqueuedCounter.increment();
    }
}
