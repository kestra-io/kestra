package io.kestra.worker.queues;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import io.kestra.core.metrics.MetricRegistry;

import io.micrometer.core.instrument.Counter;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Decorate a queue with monitoring capabilities.
 *
 * @param <T>
 */
public final class MonitoredWorkerQueue<T> extends AbstractDelegateWorkerQueue<T> {

    public static final String QUEUE_SIZE = "worker.queue.size";
    public static final String QUEUE_REMAINING_CAPACITY = "worker.queue.remaining.capacity";
    public static final String QUEUE_ENQUEUED = "worker.queue.enqueued";
    public static final String QUEUE_DEQUEUED = "worker.queue.dequeued";

    private final Counter enqueuedCounter;
    private final Counter dequeuedCounter;

    public MonitoredWorkerQueue(MetricRegistry metricRegistry, String queueName, WorkerQueue<T> queue) {
        this(metricRegistry, queueName, queue, ArrayUtils.EMPTY_STRING_ARRAY);
    }

    public MonitoredWorkerQueue(MetricRegistry metricRegistry, String queueName, WorkerQueue<T> queue, String... extraTags) {
        super(queue);

        String[] tags = ArrayUtils.addAll(new String[] { "name", queueName }, extraTags);
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
