package io.kestra.core.utils;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

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

}
