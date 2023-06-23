package io.kestra.core.runners;

import io.micronaut.context.ApplicationContext;

import java.util.concurrent.TimeUnit;

public class TestMethodScopedWorker extends Worker {
    public TestMethodScopedWorker(ApplicationContext applicationContext, int thread, String workerGroupKey) {
        super(applicationContext, thread, workerGroupKey);
    }

    @Override
    public void close() throws InterruptedException {
        this.executors.shutdown();
        this.executors.awaitTermination(60, TimeUnit.SECONDS);
    }
}