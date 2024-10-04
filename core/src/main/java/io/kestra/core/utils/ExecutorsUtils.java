package io.kestra.core.utils;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;

import java.util.concurrent.*;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ExecutorsUtils {
    @Inject
    private ThreadMainFactoryBuilder threadFactoryBuilder;

    @Inject
    private MeterRegistry meterRegistry;

    public ExecutorService cachedThreadPool(String name) {
        return this.wrap(
            name,
            Executors.newCachedThreadPool(
                threadFactoryBuilder.build(name + "_%d")
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
            threadFactoryBuilder.build(name + "_%d")
        );

        threadPoolExecutor.allowCoreThreadTimeOut(true);

        return this.wrap(
            name,
            threadPoolExecutor
        );
    }

    public ExecutorService fixedThreadPool(int thread, String name) {
        return this.wrap(
            name,
            Executors.newFixedThreadPool(
                thread,
                threadFactoryBuilder.build(name + "_%d")
            )
        );
    }

    public ExecutorService singleThreadExecutor(String name) {
        return this.wrap(
            name,
            Executors.newSingleThreadExecutor(
                threadFactoryBuilder.build(name + "_%d")
            )
        );
    }

    public ExecutorService singleThreadScheduledExecutor(String name) {
        return this.wrap(
            name,
            Executors.newSingleThreadScheduledExecutor(
                threadFactoryBuilder.build(name + "_%d")
            )
        );
    }

    public ExecutorService newThreadPerTaskExecutor(String name) {
        // TODO we cannot wrap it inside the ExecutorServiceMetrics.monitor as it is not supported yet.
        return Executors.newThreadPerTaskExecutor(
            threadFactoryBuilder.build(name + "_%d")
        );
    }

    private ExecutorService wrap(String name, ExecutorService executorService) {
        return ExecutorServiceMetrics.monitor(
            meterRegistry,
            executorService,
            name
        );
    }

}
