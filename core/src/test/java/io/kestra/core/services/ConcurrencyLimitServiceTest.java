package io.kestra.core.services;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.*;
import io.kestra.core.repositories.ConcurrencyLimitRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.*;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest(startRunner = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrencyLimitServiceTest {
    private static final String TESTS_FLOW_NS = "io.kestra.tests";
    private static final String CONCURRENCY_LIMIT_SERVICE_TEST_UNQUEUE_EXECUTION_TENANT = "concurrency_limit_service_test_unqueue_execution_tenant";

    @Inject
    private TestRunnerUtils runnerUtils;

    @Inject
    private ConcurrencyLimitService concurrencyLimitService;

    @Inject
    private BroadcastQueueInterface<FollowExecutionEvent> executionEventQueue;
    @Inject
    private FlowRepositoryInterface flowRepositoryInterface;

    @Inject
    private DispatchQueueInterface<Execution> executionQueue;

    @Inject
    private ConcurrencyLimitRepositoryInterface concurrencyLimitRepository;

    @Test
    @LoadFlows(value = "flows/valids/flow-concurrency-queue.yml", tenantId = CONCURRENCY_LIMIT_SERVICE_TEST_UNQUEUE_EXECUTION_TENANT)
    void unqueueExecution() throws QueueException, TimeoutException, InterruptedException {
        // await for the executions to be terminated
        CountDownLatch terminated = new CountDownLatch(2);
        Flux<FollowExecutionEvent> receive = TestsUtils.receive(executionEventQueue, (either) ->
        {
            if (either.getLeft().flowId().equals("flow-concurrency-queue") && either.getLeft().eventType() == ExecutionEventType.TERMINATED) {
                terminated.countDown();
            }
        });

        // run a first flow so the second is queued
        Execution first = runnerUtils.runOneUntilRunning(CONCURRENCY_LIMIT_SERVICE_TEST_UNQUEUE_EXECUTION_TENANT, TESTS_FLOW_NS, "flow-concurrency-queue");
        Execution result = runUntilQueued(CONCURRENCY_LIMIT_SERVICE_TEST_UNQUEUE_EXECUTION_TENANT, TESTS_FLOW_NS, "flow-concurrency-queue");
        assertThat(result.getState().isQueued()).isTrue();

        Execution unqueued = concurrencyLimitService.unqueue(result, State.Type.RUNNING);
        assertThat(unqueued.getState().isRunning()).isTrue();
        executionQueue.emit(unqueued);

        assertTrue(terminated.await(10, TimeUnit.SECONDS));
        receive.blockLast();
    }

    @Test
    @ExecuteFlow(value = "flows/valids/flow-concurrency-queue.yml", tenantId = "concurrency_limit_service_test_find_by_id_tenant")
    void findById(Execution execution) {
        Optional<ConcurrencyLimit> limit = concurrencyLimitRepository.findById(execution.getTenantId(), execution.getNamespace(), execution.getFlowId());

        assertThat(limit).isNotEmpty();
        assertThat(limit.get().getTenantId()).isEqualTo(execution.getTenantId());
        assertThat(limit.get().getNamespace()).isEqualTo(execution.getNamespace());
        assertThat(limit.get().getFlowId()).isEqualTo(execution.getFlowId());
    }

    @Test
    @ExecuteFlow(value = "flows/valids/flow-concurrency-queue.yml", tenantId = "concurrency_limit_service_test_update_tenant")
    void update(Execution execution) {
        Optional<ConcurrencyLimit> limit = concurrencyLimitRepository.findById(execution.getTenantId(), execution.getNamespace(), execution.getFlowId());

        assertThat(limit).isNotEmpty();
        ConcurrencyLimit updated = limit.get().withRunning(99);
        concurrencyLimitRepository.update(updated);

        limit = concurrencyLimitRepository.findById(execution.getTenantId(), execution.getNamespace(), execution.getFlowId());
        assertThat(limit).isNotEmpty();
        assertThat(limit.get().getRunning()).isEqualTo(99);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/flow-concurrency-queue.yml", tenantId = "concurrency_limit_service_test_list_tenant")
    void list(Execution execution) {
        List<ConcurrencyLimit> list = concurrencyLimitRepository.find(execution.getTenantId());

        assertThat(list).isNotEmpty();
        assertThat(list.getFirst().getTenantId()).isEqualTo(execution.getTenantId());
        assertThat(list.getFirst().getNamespace()).isEqualTo(execution.getNamespace());
        assertThat(list.getFirst().getFlowId()).isEqualTo(execution.getFlowId());
    }

    private Execution runUntilQueued(String tenantId, String namespace, String flowId) throws QueueException {
        return runUntilState(tenantId, namespace, flowId, State.Type.QUEUED);
    }

    private Execution runUntilState(String tenantId, String namespace, String flowId, State.Type state) throws QueueException {
        Execution execution = this.createExecution(tenantId, namespace, flowId);
        this.executionQueue.emit(execution);
        return runnerUtils.awaitExecution(
            it -> execution.getId().equals(it.getId()) && it.getState().getCurrent() == state,
            execution,
            Duration.ofSeconds(1)
        );
    }

    private Execution createExecution(String tenantId, String namespace, String flowId) {
        Flow flow = flowRepositoryInterface.findById(tenantId, namespace, flowId).orElseThrow();
        return Execution.newExecution(flow, null);
    }
}
