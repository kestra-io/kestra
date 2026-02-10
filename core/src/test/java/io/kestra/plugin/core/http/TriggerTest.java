package io.kestra.plugin.core.http;

import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.Scheduler;
import io.kestra.core.runners.Worker;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest(startRunner = true, startScheduler = true)
class TriggerTest {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private DispatchQueueInterface<Execution> executionQueue;

    @Inject
    protected Scheduler scheduler;

    @Test
    @LoadFlows({"flows/valids/http-listen.yaml"})
    void trigger() throws Exception {
        Await.until(() -> scheduler.isActive(), Duration.ofMillis(100), Duration.ofSeconds(20));

        // mock flow listeners
        CountDownLatch queueCount = new CountDownLatch(1);

        // scheduler
        try (
            Worker worker = applicationContext.createBean(Worker.class);
        ) {
            // wait for execution
            executionQueue.addListener(execution -> {
                if (execution.getFlowId().equals("http-listen")) {
                    queueCount.countDown();
                }
            });

            worker.start(1, null);

            assertTrue(queueCount.await(1, TimeUnit.MINUTES));
        }
    }

    @Test
    @LoadFlows({"flows/valids/http-listen-encrypted.yaml"})
    void trigger_EncryptedBody() throws Exception {
        Await.until(() -> scheduler.isActive(), Duration.ofMillis(100), Duration.ofSeconds(20));
        // mock flow listeners
        CountDownLatch queueCount = new CountDownLatch(1);

        // scheduler
        try (
            Worker worker = applicationContext.createBean(Worker.class)
        ) {
            // wait for execution
            executionQueue.addListener(execution -> {
                if (execution.getFlowId().equals("http-listen-encrypted")) {
                    queueCount.countDown();
                }
            });

            worker.start(1, null);

            assertTrue(queueCount.await(1, TimeUnit.MINUTES));
        }
    }
}
