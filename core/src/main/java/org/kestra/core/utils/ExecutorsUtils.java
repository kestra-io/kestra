package org.kestra.core.utils;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;

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

    private ExecutorService wrap(String name, ExecutorService executorService) {
        return ExecutorServiceMetrics.monitor(
            meterRegistry,
            executorService,
            name
        );
    }
}
