package io.kestra.worker.queues;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.worker.WorkerGroups;
import io.kestra.core.worker.models.WorkerContext;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Registry for managing {@link WorkerQueue} instances.
 */
@Singleton
public class WorkerQueueRegistry {

    /**
     * Canonical {@code name} tag value for the worker's job buffer queue. Must stay
     * in sync with the {@code queueName} computed in
     * {@link #getOrCreate(WorkerContext, Class)}.
     */
    public static final String WORKER_JOB_QUEUE_NAME = io.kestra.core.runners.WorkerJob.class.getSimpleName().toLowerCase();

    private final Map<QueueKey, WorkerQueue<?>> queues;

    private final MetricRegistry metricRegistry;

    @Inject
    public WorkerQueueRegistry(final MetricRegistry metricRegistry) {
        this.queues = new ConcurrentHashMap<>();
        this.metricRegistry = metricRegistry;
    }

    /**
     * Computes the in-memory job buffer size for a worker with the given thread count.
     * The worker's total maximum in-flight capacity is
     * {@code workerThreads + bufferSize(workerThreads)}.
     */
    public static int bufferSize(int workerThreads) {
        return workerThreads;
    }

    /**
     * Retrieves an existing {@code WorkerQueue} for the given {@code WorkerContext} and type, or creates a new one if it does not exist.
     * <p>
     * The created queue is wrapped in a {@code MonitoredWorkerQueue} to provide monitoring capabilities.
     *
     * @param <T> the type of elements in the queue
     * @param context the worker context, including worker-specific configurations
     * @param type the class type of the queue elements
     * @return the retrieved or newly created {@code WorkerQueue} associated with the given context and type
     */
    @SuppressWarnings("unchecked")
    public synchronized <T> WorkerQueue<T> getOrCreate(final WorkerContext context, final Class<T> type) {
        QueueKey key = new QueueKey(context.workerId(), type);
        return (WorkerQueue<T>) queues.computeIfAbsent(key, unused ->
        {
            int queueCapacity = bufferSize(context.workerThreads());
            String queueName = type.getSimpleName().toLowerCase();
            return new MonitoredWorkerQueue<T>(
                metricRegistry,
                queueName,
                new InMemoryWorkerQueue<>(queueCapacity),
                MetricRegistry.TAG_WORKER_GROUP, WorkerGroups.normalize(context.workerGroupId())
            );
        }
        );
    }

    /**
     * Retrieves the first {@code WorkerQueue} associated with the given type.
     *
     * @param type the class type of the queue elements
     * @param <T>  the type of elements in the queue
     * @return the {@code WorkerQueue} for the given type
     * @throws IllegalStateException if no queue is registered for {@code type}
     */
    @SuppressWarnings("unchecked")
    public <T> WorkerQueue<T> get(Class<T> type) {
        return queues.entrySet().stream()
            .filter(entry -> entry.getKey().type().equals(type))
            .findFirst()
            .map(Map.Entry::getValue)
            .map(queue -> (WorkerQueue<T>) queue)
            .orElseThrow(() -> new IllegalStateException("No queue found for type: " + type.getName()));
    }

    private record QueueKey(String workerId, Class<?> type) {
    }
}
