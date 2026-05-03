package io.kestra.core.runners;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.queues.QueueException;

import jakarta.inject.Inject;

@KestraTest(startRunner = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractRunnerConcurrencyTest {
    @Inject
    protected FlowConcurrencyCaseTest flowConcurrencyCaseTest;

    @Test
    @FlakyTest
    @LoadFlows(value = { "flows/valids/flow-concurrency-cancel.yml" }, tenantId = "concurrency-cancel")
    void concurrencyCancel() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyCancel("concurrency-cancel");
    }

    @Test
    @LoadFlows(value = { "flows/valids/flow-concurrency-fail.yml" }, tenantId = "concurrency-fail")
    void concurrencyFail() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyFail("concurrency-fail");
    }

    @Test
    @LoadFlows(value = { "flows/valids/flow-concurrency-queue.yml" }, tenantId = "concurrency-queue")
    void concurrencyQueue() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyQueue("concurrency-queue");
    }

    @Test
    @LoadFlows(value = { "flows/valids/flow-concurrency-queue-pause.yml" }, tenantId = "concurrency-queue-pause")
    protected void concurrencyQueuePause() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyQueuePause("concurrency-queue-pause");
    }

    @Test
    @LoadFlows(value = { "flows/valids/flow-concurrency-cancel-pause.yml" }, tenantId = "concurrency-cancel-pause")
    protected void concurrencyCancelPause() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyCancelPause("concurrency-cancel-pause");
    }

    @Test
    @LoadFlows(value = { "flows/valids/flow-concurrency-queue-fail.yml" }, tenantId = "concurrency-queue-restarted")
    protected void concurrencyQueueRestarted() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyQueueRestarted("concurrency-queue-restarted");
    }

    @Test
    @LoadFlows(value = { "flows/valids/flow-concurrency-queue-after-execution.yml" }, tenantId = "concurrency-queue-after-execution")
    void concurrencyQueueAfterExecution() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyQueueAfterExecution("concurrency-queue-after-execution");
    }

    @Test
    @LoadFlows(value = { "flows/valids/flow-concurrency-subflow.yml", "flows/valids/flow-concurrency-cancel.yml" }, tenantId = "flow-concurrency-subflow")
    void flowConcurrencySubflow() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencySubflow("flow-concurrency-subflow");
    }

    @Test
    @FlakyTest(description = "Only flaky in CI")
    @LoadFlows(
        value = { "flows/valids/flow-concurrency-parallel-subflow-kill.yaml", "flows/valids/flow-concurrency-parallel-subflow-kill-child.yaml",
            "flows/valids/flow-concurrency-parallel-subflow-kill-grandchild.yaml" },
        tenantId = "flow-concurrency-parallel-subflow-kill"
    )
    protected void flowConcurrencyParallelSubflowKill() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyParallelSubflowKill("flow-concurrency-parallel-subflow-kill");
    }

    @Test
    @FlakyTest(description = "Only flaky in CI")
    @LoadFlows(value = { "flows/valids/flow-concurrency-queue-killed.yml" }, tenantId = "flow-concurrency-killed")
    void flowConcurrencyKilled() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyKilled("flow-concurrency-killed");
    }

    @Test
    @FlakyTest(description = "Only flaky in CI")
    @LoadFlows(value = { "flows/valids/flow-concurrency-queue-killed.yml" }, tenantId = "flow-concurrency-queue-killed")
    void flowConcurrencyQueueKilled() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyQueueKilled("flow-concurrency-queue-killed");
    }

    @Test
    @LoadFlows(value = { "flows/valids/flow-concurrency-queue.yml" }, tenantId = "flow-concurrency-queued-protection")
    void flowConcurrencyQueuedProtection() throws QueueException, InterruptedException {
        flowConcurrencyCaseTest.flowConcurrencyQueuedProtection("flow-concurrency-queued-protection");
    }

    @Test
    @LoadFlows(value = { "flows/valids/flow-concurrency-queue.yml" }, tenantId = "flow-concurrency-scheduled")
    void flowConcurrencyScheduled() throws QueueException {
        flowConcurrencyCaseTest.flowConcurrencyScheduled("flow-concurrency-scheduled");
    }
}
