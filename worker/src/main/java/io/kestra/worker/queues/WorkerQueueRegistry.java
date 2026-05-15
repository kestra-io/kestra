package io.kestra.worker.queues;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.worker.models.WorkerContext;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Registry for managing {@link WorkerQueue} instances.
 */
@Singleton
public class WorkerQueueRegistry {

    /**
     * Canonical {@code name} tag value for the worker's job buffer queue. Derived
     * from the queue's element type so it stays in sync with the generic
     * {@code queueName} computed in {@link #getOrCreate(WorkerContext, Class)}.
     * Both the worker's heartbeat metric ({@code worker.queue.size {name=workerjob}})
     * and any downstream consumer that needs to single out this queue from other
     * {@code MonitoredWorkerQueue} instances (e.g., EE's broadcast queues) should
     * import this constant rather than hard-coding the string.
     */
    public static final String WORKER_JOB_QUEUE_NAME = io.kestra.core.runners.WorkerJob.class.getSimpleName().toLowerCase();

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
     * Computes the in-memory job buffer size for a worker with the given thread count.
     * The buffer holds pending jobs (between dispatch from the controller and execution
     * by a worker thread). The worker's total maximum in-flight capacity is
     * {@code workerThreads + bufferSize(workerThreads)}.
     *
     * <p>Single source of truth — callers that need the buffer size before the queue
     * is constructed (e.g., metric registration in {@code WorkerAgent}) should call
     * this helper instead of recomputing the formula.
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
            // Buffer mirrors the thread count so the worker's total in-flight capacity
            // is `threads (executing) + threads (queued) = 2 × threads`. Advertised to
            // the controller as `WorkerConnectionInfo.maxConcurrency` for reservation math.
            int queueCapacity = bufferSize(context.workerThreads());
            String queueName = type.getSimpleName().toLowerCase();
            return new MonitoredWorkerQueue<T>(metricRegistry, queueName, new InMemoryWorkerQueue<>(queueCapacity));
        }
        );
    }

    /**
     * Retrieves the first {@code WorkerQueue} associated with the given type.
     *
     * @param type the class type of the queue elements
     * @param <T> the type of elements in the queue
     * @return the {@code WorkerQueue}, or {@code null} if no such queue exists
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
