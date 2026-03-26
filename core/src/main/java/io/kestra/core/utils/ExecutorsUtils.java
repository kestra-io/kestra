package io.kestra.core.utils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

import io.kestra.core.contexts.KestraContext;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class to create {@link java.util.concurrent.ExecutorService} with {@link java.util.concurrent.ExecutorService} instances.
 * WARNING: those instances will use the {@link ThreadUncaughtExceptionHandler} which terminates Kestra if an error occurs in any thread,
 * so it should not be used inside plugins.
 */
@Singleton
@Slf4j
public class ExecutorsUtils {

    @Inject
    private MeterRegistry meterRegistry;

    @Value("${" + KestraContext.KESTRA_ALLOCATED_CPU_CORES + ":0}")
    private int allocatedCpuCores;

    public int getAllocatedCpuCores() {
        return allocatedCpuCores == 0 ? Runtime.getRuntime().availableProcessors() : allocatedCpuCores;
    }

    public ExecutorService cachedThreadPool(String name) {
        return this.wrap(
            name,
            Executors.newCachedThreadPool(
                ThreadMainFactoryBuilder.build(name + "_%d")
            )
        );
    }

    public ExecutorService maxCachedThreadPool(int maxThread, String name) {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
            maxThread,
            maxThread,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            ThreadMainFactoryBuilder.build(name + "_%d")
        );

        threadPoolExecutor.allowCoreThreadTimeOut(true);

        return this.wrap(
            name,
            threadPoolExecutor
        );
    }

    public ExecutorService singleThreadExecutor(String name) {
        return this.wrap(
            name,
            Executors.newSingleThreadExecutor(
                ThreadMainFactoryBuilder.build(name + "_%d")
            )
        );
    }

    public ExecutorService singleThreadScheduledExecutor(String name) {
        return this.wrap(
            name,
            Executors.newSingleThreadScheduledExecutor(
                ThreadMainFactoryBuilder.build(name + "_%d")
            )
        );
    }

    public static void closeScheduledThreadPool(ScheduledExecutorService scheduledExecutorService, Duration gracePeriod, List<ScheduledFuture<?>> taskFutures) {
        scheduledExecutorService.shutdown();
        if (scheduledExecutorService.isTerminated()) {
            return;
        }

        try {
            if (!scheduledExecutorService.awaitTermination(gracePeriod.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Failed to shutdown the ScheduledThreadPoolExecutor during grace period, forcing it to shutdown now");

                // Ensure the scheduled task reaches a terminal state to avoid possible memory leak
                ListUtils.emptyOnNull(taskFutures).forEach(taskFuture -> taskFuture.cancel(true));

                scheduledExecutorService.shutdownNow();
            }
            log.debug("Stopped scheduled ScheduledThreadPoolExecutor.");
        } catch (InterruptedException e) {
            scheduledExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
            log.debug("Failed to shutdown the ScheduledThreadPoolExecutor.");
        }
    }

    private ExecutorService wrap(String name, ExecutorService executorService) {
        return ExecutorServiceMetrics.monitor(
            meterRegistry,
            executorService,
            name
        );
    }

    /**
     * Gracefully shutdown the given {@link ExecutorService} and wait for its termination within the specified timeout.
     *
     * @param name the name of the executor service, used for logging purposes.
     * @param executorService the executor service to shutdown.
     * @param awaitTermination the duration to wait for the executor service to terminate before forcing shutdown.
     */
    public static void closeExecutorService(String name, ExecutorService executorService, Duration awaitTermination) {
        executorService.shutdown();
        if (executorService.isTerminated()) {
            return;
        }
        try {
            if (!executorService.awaitTermination(awaitTermination.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Executor service [{}] did not terminate within timeout, forcing shutdown", name);
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.debug("Interrupted while shutting down executor service [{}]", name, e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
