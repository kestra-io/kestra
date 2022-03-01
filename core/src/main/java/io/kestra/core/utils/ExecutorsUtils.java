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

    public ExecutorService maxCachedThreadPool(int minThread, int maxThread, String name) {
        return this.wrap(
            name,
            new ThreadPoolExecutor(
                minThread,
                maxThread,
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                threadFactoryBuilder.build(name + "_%d")
            )
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

    private ExecutorService wrap(String name, ExecutorService executorService) {
        return ExecutorServiceMetrics.monitor(
            meterRegistry,
            executorService,
            name
        );
    }
}
