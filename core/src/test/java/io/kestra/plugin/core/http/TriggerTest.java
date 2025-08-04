package io.kestra.plugin.core.http;

import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.Scheduler;
import io.kestra.core.runners.TestMethodScopedWorker;
import io.kestra.core.runners.Worker;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Objects;
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
            Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 8, null);
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
            Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 8, null)
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
