package io.kestra.core.runners;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.utils.ExecutorsUtils;

import java.io.Serial;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread pool based on virtual threads but with a limit on the number of threads used.
 */
class RealtimeTriggerWorkerThreadPool {
    private final int maxThreads;
    private final AtomicInteger count = new AtomicInteger();
    private final ExecutorService executorService;

    /**
     * Create the virtual thread pool with the given ExecutorsUtils and maxThreads.
     * If maxThreads is 0 or less, the pool will be unbounded.
     */
    RealtimeTriggerWorkerThreadPool(ExecutorsUtils executorUtils, int maxThreads) {
        this.maxThreads = maxThreads;
        this.executorService = executorUtils.newThreadPerTaskExecutor("worker-realtime-trigger");
    }

    void initMetrics(MetricRegistry metricRegistry, String[] tags) {
        // create metrics to store thread count, pending jobs and running jobs, so we can have autoscaling easily
        metricRegistry.gauge(MetricRegistry.METRIC_WORKER_REALTIME_TRIGGER_THREAD_COUNT, maxThreads, tags);
        metricRegistry.gauge(MetricRegistry.METRIC_WORKER_REALTIME_TRIGGER_RUNNING_COUNT, count, tags);
    }

    /**
     * Starts and forget a virtual thread with the runnable.
     * It will maintain a count of running threads.
     */
    void startAndForget(Runnable runnable) throws ThreadPoolExcedeedException {
        int actualTheadCount = count.incrementAndGet();
        if (maxThreads > 0  && actualTheadCount > maxThreads) {
            count.decrementAndGet();
            throw new ThreadPoolExcedeedException("Unable to create a new worker thread for a realtime trigger: pool size exceeded (" + maxThreads + ")");
        }

        CompletableFuture.runAsync(runnable, executorService)
            .whenComplete((ignored, exception) -> count.decrementAndGet());
    }

    void shutdown() {
        this.executorService.shutdown();
    }

    static class ThreadPoolExcedeedException extends Exception {
        @Serial
        private static final long serialVersionUID = 1L;

        public ThreadPoolExcedeedException(String message) {
            super(message);
        }
    }
}
