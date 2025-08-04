package io.kestra.worker.queues;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.worker.models.WorkerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing {@link WorkerQueue} instances.
 */
@Singleton
public class WorkerQueueRegistry {

    private final Map<QueueKey, WorkerQueue<?>> queues;

    private final MetricRegistry metricRegistry;

    /**
     * Create a new {@code WorkerQueueFactory} instance.
     *
     * @param metricRegistry the {@code MetricRegistry} instance.
     */
    @Inject
    public WorkerQueueRegistry(final MetricRegistry metricRegistry) {
        this.queues = new ConcurrentHashMap<>();
        this.metricRegistry = metricRegistry;
    }

    /**
     * Retrieves an existing {@code WorkerQueue} for the given {@code WorkerContext} and type, or creates a new one if it does not exist.
     * <p>
     * The created queue is wrapped in a {@code MonitoredWorkerQueue} to provide monitoring capabilities.
     *
     * @param <T>     the type of elements in the queue
     * @param context the worker context, including worker-specific configurations
     * @param type    the class type of the queue elements
     * @return the retrieved or newly created {@code WorkerQueue} associated with the given context and type
     */
    @SuppressWarnings("unchecked")
    public synchronized <T> WorkerQueue<T> getOrCreate(final WorkerContext context, final Class<T> type) {
        QueueKey key = new QueueKey(context.workerId(), type);
        return (WorkerQueue<T>) queues.computeIfAbsent(key, unused ->
            {
                // by default, queue capacity is twice the number of worker threads
                int queueCapacity = context.workerThreads() * 2;
                String queueName = type.getSimpleName().toLowerCase();
                return new MonitoredWorkerQueue<T>(metricRegistry, queueName, new InMemoryWorkerQueue<>(queueCapacity));
            }
        );
    }

    private record QueueKey(String workerId, Class<?> type) {
    }
}
