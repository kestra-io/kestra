package io.kestra.core.services;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.utils.Rethrow.throwRunnable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest(startRunner = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrencyLimitServiceTest {
    private static final String TESTS_FLOW_NS = "io.kestra.tests";
    private static final String TENANT_ID = "main";
    private static final String CONCURRENCY_LIMIT_SERVICE_TEST_UNQUEUE_EXECUTION_TENANT = "concurrency_limit_service_test_unqueue_execution_tenant";

    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    private FlowRepositoryInterface flowRepositoryInterface;

    @Inject
    private ConcurrencyLimitService concurrencyLimitService;

    @AfterEach
    void tearDown() {
        concurrencyLimitService.find(TENANT_ID)
            .forEach(limit -> concurrencyLimitService.update(limit.withRunning(0)));
    }

    @Test
    @LoadFlows(value = "flows/valids/flow-concurrency-queue.yml", tenantId = CONCURRENCY_LIMIT_SERVICE_TEST_UNQUEUE_EXECUTION_TENANT)
    void unqueueExecution() throws QueueException, TimeoutException, InterruptedException {
        // run a first flow so the second is queued
        Execution first = runnerUtils.runOneUntilRunning(CONCURRENCY_LIMIT_SERVICE_TEST_UNQUEUE_EXECUTION_TENANT, TESTS_FLOW_NS, "flow-concurrency-queue");
        Execution result = runUntilQueued(CONCURRENCY_LIMIT_SERVICE_TEST_UNQUEUE_EXECUTION_TENANT, TESTS_FLOW_NS, "flow-concurrency-queue");
        assertThat(result.getState().isQueued()).isTrue();

        // await for the execution to be terminated
        CountDownLatch terminated = new CountDownLatch(2);
        Flux<Execution> receive = TestsUtils.receive(executionQueue, (either) -> {
            if (either.getLeft().getId().equals(first.getId()) && either.getLeft().getState().isTerminated()) {
                terminated.countDown();
            }
            if (either.getLeft().getId().equals(result.getId()) && either.getLeft().getState().isTerminated()) {
                terminated.countDown();
            }
        });

        Execution unqueued = concurrencyLimitService.unqueue(result, State.Type.RUNNING);
        assertThat(unqueued.getState().isRunning()).isTrue();
        executionQueue.emit(unqueued);

        assertTrue(terminated.await(10, TimeUnit.SECONDS));
        receive.blockLast();
    }

    @Test
    @ExecuteFlow(value = "flows/valids/flow-concurrency-queue.yml", tenantId = "concurrency_limit_service_test_find_by_id_tenant")
    void findById(Execution execution) {
        Optional<ConcurrencyLimit> limit = concurrencyLimitService.findById(execution.getTenantId(), execution.getNamespace(), execution.getFlowId());

        assertThat(limit).isNotEmpty();
        assertThat(limit.get().getTenantId()).isEqualTo(execution.getTenantId());
        assertThat(limit.get().getNamespace()).isEqualTo(execution.getNamespace());
        assertThat(limit.get().getFlowId()).isEqualTo(execution.getFlowId());
    }

    @Test
    @ExecuteFlow(value = "flows/valids/flow-concurrency-queue.yml", tenantId = "concurrency_limit_service_test_update_tenant")
    void update(Execution execution) {
        Optional<ConcurrencyLimit> limit = concurrencyLimitService.findById(execution.getTenantId(), execution.getNamespace(), execution.getFlowId());

        assertThat(limit).isNotEmpty();
        ConcurrencyLimit updated =  limit.get().withRunning(99);
        concurrencyLimitService.update(updated);


        limit = concurrencyLimitService.findById(execution.getTenantId(), execution.getNamespace(), execution.getFlowId());
        assertThat(limit).isNotEmpty();
        assertThat(limit.get().getRunning()).isEqualTo(99);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/flow-concurrency-queue.yml", tenantId = "concurrency_limit_service_test_list_tenant")
    void list(Execution execution) {
        List<ConcurrencyLimit> list = concurrencyLimitService.find(execution.getTenantId());

        assertThat(list).isNotEmpty();
        assertThat(list.getFirst().getTenantId()).isEqualTo(execution.getTenantId());
        assertThat(list.getFirst().getNamespace()).isEqualTo(execution.getNamespace());
        assertThat(list.getFirst().getFlowId()).isEqualTo(execution.getFlowId());
    }

    private Execution runUntilQueued(String namespace, String flowId) throws TimeoutException, QueueException {
        return runUntilQueued(TENANT_ID, namespace, flowId);
    }

    private Execution runUntilQueued(String tenantId, String namespace, String flowId) throws TimeoutException, QueueException {
        return runUntilState(tenantId, namespace, flowId, State.Type.QUEUED);
    }

    private Execution runUntilState(String tenantId, String namespace, String flowId, State.Type state) throws TimeoutException, QueueException {
        Execution execution = this.createExecution(tenantId, namespace, flowId);
        return runnerUtils.awaitExecution(
            it -> execution.getId().equals(it.getId()) && it.getState().getCurrent() == state,
            throwRunnable(() -> this.executionQueue.emit(execution)),
            Duration.ofSeconds(1));
    }

    private Execution createExecution(String tenantId, String namespace, String flowId) {
        Flow flow = flowRepositoryInterface.findById(tenantId, namespace, flowId).orElseThrow();
        return Execution.newExecution(flow, null);
    }
}
