package io.kestra.core.runners;

import io.micronaut.context.ApplicationContext;

import java.util.concurrent.TimeUnit;

/**
 * This worker is a special worker which won't close every queue allowing it to be ran and closed within a test without
 * preventing the Micronaut context to be used for further tests with queues still being up
 */
public class TestMethodScopedWorker extends Worker {
    public TestMethodScopedWorker(ApplicationContext applicationContext, int thread, String workerGroupKey) {
        super(applicationContext, thread, workerGroupKey);
    }

    /**
     * Override is done to prevent closing the queue. However please note that this is not failsafe because we ideally need
     * to stop worker's subscriptions to every queue before cutting of the executors pool.
     */
    @Override
    public void close() throws InterruptedException {
        this.executors.shutdown();
        this.executors.awaitTermination(60, TimeUnit.SECONDS);
    }
}