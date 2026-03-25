package io.kestra.plugin.core.http;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.Scheduler;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest(startRunner = true, startScheduler = true)
class TriggerTest {
    @Inject
    private DispatchQueueInterface<Execution> executionQueue;

    @Inject
    protected Scheduler scheduler;

    @Test
    @LoadFlows({ "flows/valids/http-listen.yaml" })
    void shouldExecuteFlowForHttpTrigger() throws Exception {
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> scheduler.isActive());
        CountDownLatch queueCount = new CountDownLatch(1);
        // wait for execution
        executionQueue.addListener(execution ->
        {
            if (execution.getFlowId().equals("http-listen")) {
                queueCount.countDown();
            }
        });
        assertTrue(queueCount.await(1, TimeUnit.MINUTES));
    }

    @Test
    @LoadFlows({ "flows/valids/http-listen-encrypted.yaml" })
    void shouldExecuteFlowForHttpTriggerWithEncryptedBody() throws Exception {
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> scheduler.isActive());
        CountDownLatch queueCount = new CountDownLatch(1);
        // wait for execution
        executionQueue.addListener(execution ->
        {
            if (execution.getFlowId().equals("http-listen-encrypted")) {
                queueCount.countDown();
            }
        });
        assertTrue(queueCount.await(1, TimeUnit.MINUTES));
    }
}
