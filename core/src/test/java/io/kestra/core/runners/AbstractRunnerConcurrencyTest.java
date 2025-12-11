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
    @Inject
    protected FlowConcurrencyCaseTest flowConcurrencyCaseTest;

    @Test
    @LoadFlows(value = {"flows/valids/flow-concurrency-cancel.yml"}, tenantId = "concurrency-cancel")
    void concurrencyCancel() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyCancel("concurrency-cancel");
    }

    @Test
    @LoadFlows(value = {"flows/valids/flow-concurrency-fail.yml"}, tenantId = "concurrency-fail")
    void concurrencyFail() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyFail("concurrency-fail");
    }

    @Test
    @LoadFlows(value = {"flows/valids/flow-concurrency-queue.yml"}, tenantId = "concurrency-queue")
    void concurrencyQueue() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyQueue("concurrency-queue");
    }

    @Test
    @LoadFlows(value = {"flows/valids/flow-concurrency-for-each-item.yaml", "flows/valids/flow-concurrency-queue.yml"}, tenantId = "flow-concurrency-with-for-each-item")
    protected void flowConcurrencyWithForEachItem() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyWithForEachItem("flow-concurrency-with-for-each-item");
    }

    @Test
    @LoadFlows(value = {"flows/valids/flow-concurrency-queue-fail.yml"}, tenantId = "concurrency-queue-fail")
    protected void concurrencyQueueRestarted() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyQueueRestarted("concurrency-queue-fail");
    }

    @Test
    @LoadFlows(value = {"flows/valids/flow-concurrency-queue-after-execution.yml"}, tenantId = "concurrency-queue-after-execution")
    void concurrencyQueueAfterExecution() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyQueueAfterExecution("concurrency-queue-after-execution");
    }

    @Test
    @LoadFlows(value = {"flows/valids/flow-concurrency-subflow.yml", "flows/valids/flow-concurrency-cancel.yml"}, tenantId = "flow-concurrency-subflow")
    void flowConcurrencySubflow() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencySubflow("flow-concurrency-subflow");
    }
}
