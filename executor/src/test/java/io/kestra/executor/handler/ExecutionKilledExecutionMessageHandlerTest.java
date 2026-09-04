package io.kestra.executor.handler;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.executor.ExecutorContext;

import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
class ExecutionKilledExecutionMessageHandlerTest {
    @Inject
    private ExecutionKilledExecutionMessageHandler executionKilledExecutionMessageHandler;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private BroadcastQueueInterface<AsyncOperationProcessedEvent> asyncOperationProcessedEventQueue;

    @Inject
    private BroadcastQueueInterface<ExecutionKilled> killQueue;

    @Inject
    KillSwitchService killSwitchService;

    @MockBean(KillSwitchService.class)
    KillSwitchService killSwitchService() {
        return mock(KillSwitchService.class);
    }

    @BeforeEach
    void setUp() {
        when(killSwitchService.evaluate(anyString())).thenReturn(EvaluationType.PASS);
    }

    @Test
    void shouldReturnEmptyForNonExistingExecution() {
        var executionKilled = ExecutionKilledExecution.builder()
            .tenantId("tenant")
            .executionId("execution")
            .executionState(State.Type.FAILED)
            .build();

        var maybeExecutor = executionKilledExecutionMessageHandler.handle(executionKilled);

        assertTrue(maybeExecutor.isEmpty());
    }

    @Test
    void shouldReturnAnExecutorForExistingExecution() {
        var flow = flowRepository.create(GenericFlow.of(Fixtures.flow()));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var executionKilledExecution = ExecutionKilledExecution.builder()
            .tenantId(execution.getTenantId())
            .executionId(execution.getId())
            .executionState(State.Type.KILLED)
            .build();

        var maybeExecutor = executionKilledExecutionMessageHandler.handle(executionKilledExecution);

        assertThat(maybeExecutor).isPresent();
        assertThat(maybeExecutor.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.KILLED);
    }

    @Test
    void shouldEmitSucceededProcessedEventWhenCommandCarriesOperationId() throws Exception {
        // Given: an existing flow + execution and a kill message carrying an operationId
        var flow = flowRepository.create(GenericFlow.of(Fixtures.flow()));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var operationId = IdUtils.create();
        var message = ExecutionKilledExecution.builder()
            .tenantId(execution.getTenantId())
            .executionId(execution.getId())
            .executionState(State.Type.KILLED)
            .operationId(operationId)
            .build();
        var future = subscribeForOperation(operationId);

        // When
        var maybeExecutor = executionKilledExecutionMessageHandler.handle(message);

        // Then: handler succeeds and emits a SUCCEEDED processed event for the same operation.
        assertThat(maybeExecutor).isPresent();
        AsyncOperationProcessedEvent event = future.get(5, TimeUnit.SECONDS);
        assertThat(event.operationId()).isEqualTo(operationId);
        assertThat(event.tenantId()).isEqualTo(execution.getTenantId());
        assertThat(event.itemId()).isEqualTo(execution.getId());
        assertThat(event.outcome()).isEqualTo(AsyncOperationProcessedEvent.Outcome.SUCCEEDED);
        assertThat(event.error()).isNull();
    }

    @Test
    void shouldNotEmitProcessedEventWhenCommandHasNoOperationId() throws Exception {
        // Given: an existing flow + execution and a kill message WITHOUT operationId
        var flow = flowRepository.create(GenericFlow.of(Fixtures.flow()));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var message = ExecutionKilledExecution.builder()
            .tenantId(execution.getTenantId())
            .executionId(execution.getId())
            .executionState(State.Type.KILLED)
            .build();
        CompletableFuture<AsyncOperationProcessedEvent> future = new CompletableFuture<>();
        QueueSubscriber<AsyncOperationProcessedEvent> subscriber = asyncOperationProcessedEventQueue.subscriber().subscribe(either ->
        {
            if (either.isLeft() && execution.getId().equals(either.getLeft().itemId())) {
                future.complete(either.getLeft());
            }
        });
        try {
            // When
            var maybeExecutor = executionKilledExecutionMessageHandler.handle(message);

            // Then: handler succeeds and NO processed event is emitted for this execution.
            assertThat(maybeExecutor).isPresent();
            assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                .isInstanceOf(java.util.concurrent.TimeoutException.class);
        } finally {
            subscriber.close();
        }
    }

    @Test
    void shouldEmitFailedProcessedEventWhenHandlerThrows() throws Exception {
        // Given: an execution whose flow is NOT registered in the flow meta store.
        // `killingOrAfterKillState` will call `flowMetaStore.findByExecution(...).orElseThrow()`
        // which throws `NoSuchElementException` — a RuntimeException.
        var operationId = IdUtils.create();
        String tenantId = "tenant-missing-flow";
        String executionId = IdUtils.create();
        // Create and persist an execution whose flow is NOT known to the flow meta store.
        var orphanExecution = Execution.builder()
            .tenantId(tenantId)
            .namespace("io.kestra.tests.missing")
            .flowId("missing-flow-" + IdUtils.create())
            .id(executionId)
            .state(new State().withState(State.Type.RUNNING))
            .build();
        executionRepository.save(orphanExecution);
        var message = ExecutionKilledExecution.builder()
            .tenantId(tenantId)
            .executionId(executionId)
            .executionState(State.Type.KILLED)
            .operationId(operationId)
            .build();
        var future = subscribeForOperation(operationId);

        // When / Then: handler throws and still emits a FAILED processed event.
        assertThatThrownBy(() -> executionKilledExecutionMessageHandler.handle(message))
            .isInstanceOf(RuntimeException.class);

        AsyncOperationProcessedEvent event = future.get(5, TimeUnit.SECONDS);
        assertThat(event.operationId()).isEqualTo(operationId);
        assertThat(event.itemId()).isEqualTo(executionId);
        assertThat(event.outcome()).isEqualTo(AsyncOperationProcessedEvent.Outcome.FAILED);
    }

    @Test
    void shouldReturnEmptyWhenKillSwitchIsIgnore() {
        // Given — execution is ignored by the kill switch
        when(killSwitchService.evaluate("exec-ignored")).thenReturn(EvaluationType.IGNORE);
        var message = ExecutionKilledExecution.builder()
            .tenantId("tenant").executionId("exec-ignored").isOnKillCascade(false).build();

        // When
        Optional<ExecutorContext> result = executionKilledExecutionMessageHandler.handle(message);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldEmitKillEventForDescendantsWhenParentTaskRunKilled() throws Exception {
        // Given: a flow + execution with parent and child task runs.
        var flow = flowRepository.create(GenericFlow.of(Fixtures.flow()));
        var execution = Execution.newExecution(flow, Collections.emptyList());

        var parentTaskRun = io.kestra.core.models.executions.TaskRun.builder()
            .id("parent-taskrun-id")
            .executionId(execution.getId())
            .namespace(execution.getNamespace())
            .flowId(execution.getFlowId())
            .taskId("parent-task-id")
            .state(new State().withState(State.Type.RUNNING))
            .build();

        var childTaskRun = io.kestra.core.models.executions.TaskRun.builder()
            .id("child-taskrun-id")
            .executionId(execution.getId())
            .namespace(execution.getNamespace())
            .flowId(execution.getFlowId())
            .taskId("child-task-id")
            .parentTaskRunId("parent-taskrun-id")
            .state(new State().withState(State.Type.RUNNING))
            .build();

        var updatedExecution = execution.toBuilder()
            .taskRunList(java.util.List.of(parentTaskRun, childTaskRun))
            .build();

        executionRepository.save(updatedExecution);

        java.util.List<ExecutionKilledExecution> receivedKills = new java.util.concurrent.CopyOnWriteArrayList<>();
        var subscriber = killQueue.subscriber().subscribe(either -> {
            if (either.isLeft() && either.getLeft() instanceof ExecutionKilledExecution eke) {
                if (eke.getState() == ExecutionKilled.State.EXECUTED) {
                    receivedKills.add(eke);
                }
            }
        });

        try {
            var message = ExecutionKilledExecution.builder()
                .tenantId(execution.getTenantId())
                .executionId(execution.getId())
                .taskRunId("parent-taskrun-id")
                .executionState(State.Type.FAILED)
                .build();

            // When
            executionKilledExecutionMessageHandler.handle(message);

            // Then
            Thread.sleep(200);
            assertThat(receivedKills).hasSize(2);
            assertThat(receivedKills).extracting(ExecutionKilledExecution::getTaskRunId)
                .containsExactlyInAnyOrder("parent-taskrun-id", "child-taskrun-id");

            ExecutionKilledExecution parentKill = receivedKills.stream()
                .filter(e -> "parent-taskrun-id".equals(e.getTaskRunId()))
                .findFirst().orElseThrow();
            assertThat(parentKill.getExecutionState()).isEqualTo(State.Type.FAILED);

            ExecutionKilledExecution childKill = receivedKills.stream()
                .filter(e -> "child-taskrun-id".equals(e.getTaskRunId()))
                .findFirst().orElseThrow();
            assertThat(childKill.getExecutionState()).isEqualTo(State.Type.CANCELLED);
        } finally {
            subscriber.close();
        }
    }

    private CompletableFuture<AsyncOperationProcessedEvent> subscribeForOperation(String operationId) {
        CompletableFuture<AsyncOperationProcessedEvent> future = new CompletableFuture<>();
        QueueSubscriber<AsyncOperationProcessedEvent> subscriber = asyncOperationProcessedEventQueue.subscriber().subscribe(either ->
        {
            if (either.isLeft() && operationId.equals(either.getLeft().operationId())) {
                future.complete(either.getLeft());
            }
        });
        future.whenComplete((e, t) -> subscriber.close());
        return future.orTimeout(10, TimeUnit.SECONDS);
    }
}
