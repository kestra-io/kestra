package io.kestra.core.runners;

import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@KestraTest(startRunner = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractRunnerConcurrencyTest {
    public static final String TENANT_1 = "tenant1";

    @Inject
    protected FlowConcurrencyCaseTest flowConcurrencyCaseTest;

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-cancel.yml"})
    void concurrencyCancel() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyCancel();
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-fail.yml"})
    void concurrencyFail() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyFail();
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-queue.yml"})
    void concurrencyQueue() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyQueue();
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-queue-pause.yml"})
    protected void concurrencyQueuePause() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyQueuePause();
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-cancel-pause.yml"})
    protected void concurrencyCancelPause() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyCancelPause();
    }

    @Test
    @LoadFlows(value = {"flows/valids/flow-concurrency-for-each-item.yaml", "flows/valids/flow-concurrency-queue.yml"}, tenantId = TENANT_1)
    protected void flowConcurrencyWithForEachItem() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyWithForEachItem(TENANT_1);
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-queue-fail.yml"})
    protected void concurrencyQueueRestarted() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyQueueRestarted();
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-queue-after-execution.yml"})
    void concurrencyQueueAfterExecution() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyQueueAfterExecution();
    }

    @Test
    @LoadFlows(value = {"flows/valids/flow-concurrency-subflow.yml", "flows/valids/flow-concurrency-cancel.yml"}, tenantId = TENANT_1)
    void flowConcurrencySubflow() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencySubflow(TENANT_1);
    }

    @Test
    @FlakyTest(description = "Only flaky in CI")
    @LoadFlows({"flows/valids/flow-concurrency-parallel-subflow-kill.yaml", "flows/valids/flow-concurrency-parallel-subflow-kill-child.yaml", "flows/valids/flow-concurrency-parallel-subflow-kill-grandchild.yaml"})
    protected void flowConcurrencyParallelSubflowKill() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyParallelSubflowKill();
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-queue-killed.yml"})
    void flowConcurrencyKilled() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyKilled();
    }

    @Test
    @FlakyTest(description = "Only flaky in CI")
    @LoadFlows({"flows/valids/flow-concurrency-queue-killed.yml"})
    void flowConcurrencyQueueKilled() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyQueueKilled();
    }
}
