package io.kestra.executor.handler;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.IdUtils;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;

import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionKilledExecutionMessageHandlerTest {
    private ExecutorTestHarness harness;
    private ExecutionKilledExecutionMessageHandler handler;

    @BeforeEach
    void setUp() {
        harness = ExecutorTestHarness.create();
        handler = harness.executionKilledExecutionMessageHandler();
        // killSubflowExecutions/killLoopSubExecutions query the execution repository on the real
        // ExecutionService; stub them to "no child executions" — the old H2 tests had none either.
        doReturn(Flux.empty()).when(harness.executionService()).killSubflowExecutions(any(), any());
        doReturn(List.of()).when(harness.executionService()).killLoopSubExecutions(any(), any());
    }

    @Test
    void shouldReturnEmptyForNonExistingExecution() {
        var executionKilled = ExecutionKilledExecution.builder()
            .tenantId("tenant")
            .executionId("execution")
            .executionState(State.Type.FAILED)
            .build();

        var maybeExecutor = handler.handle(executionKilled);

        assertThat(maybeExecutor).isEmpty();
        // The EXECUTED kill event is broadcast to the workers regardless of whether the execution exists.
        assertThat(harness.kills()).hasSize(1);
        assertThat(harness.kills().getFirst().getState()).isEqualTo(ExecutionKilled.State.EXECUTED);
    }

    @Test
    void shouldReturnAnExecutorForExistingExecution() {
        var flow = Flows.of(Fixtures.flow());
        harness.registerFlow(flow);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        harness.executionStateStore().save(execution);
        var executionKilledExecution = ExecutionKilledExecution.builder()
            .tenantId(execution.getTenantId())
            .executionId(execution.getId())
            .executionState(State.Type.KILLED)
            .build();

        var maybeExecutor = handler.handle(executionKilledExecution);

        assertThat(maybeExecutor).isPresent();
        assertThat(maybeExecutor.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.KILLED);
        // Exactly one kill event: the EXECUTED broadcast to the workers (no cascade children).
        assertThat(harness.kills()).hasSize(1);
        assertThat(harness.kills().getFirst()).isInstanceOfSatisfying(ExecutionKilledExecution.class, killed ->
        {
            assertThat(killed.getExecutionId()).isEqualTo(execution.getId());
            assertThat(killed.getTenantId()).isEqualTo(execution.getTenantId());
            assertThat(killed.getState()).isEqualTo(ExecutionKilled.State.EXECUTED);
            assertThat(killed.getIsOnKillCascade()).isFalse();
        });
    }

    @Test
    void shouldEmitSucceededProcessedEventWhenCommandCarriesOperationId() {
        // Given: an existing flow + execution and a kill message carrying an operationId
        var flow = Flows.of(Fixtures.flow());
        harness.registerFlow(flow);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        harness.executionStateStore().save(execution);
        var operationId = IdUtils.create();
        var message = ExecutionKilledExecution.builder()
            .tenantId(execution.getTenantId())
            .executionId(execution.getId())
            .executionState(State.Type.KILLED)
            .operationId(operationId)
            .build();

        // When
        var maybeExecutor = handler.handle(message);

        // Then: handler succeeds and reports a SUCCEEDED processed outcome for the same operation
        // (the message carries the operationId, so the async-operation service emits the event).
        assertThat(maybeExecutor).isPresent();
        assertThat(message.getOperationId()).isEqualTo(operationId);
        verify(harness.asyncOperationService()).emitProcessedIfAsync(
            same(message),
            eq(execution.getTenantId()),
            eq(execution.getId()),
            eq(AsyncOperationProcessedEvent.Outcome.SUCCEEDED),
            isNull()
        );
    }

    @Test
    void shouldNotEmitProcessedEventWhenCommandHasNoOperationId() {
        // Given: an existing flow + execution and a kill message WITHOUT operationId
        var flow = Flows.of(Fixtures.flow());
        harness.registerFlow(flow);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        harness.executionStateStore().save(execution);
        var message = ExecutionKilledExecution.builder()
            .tenantId(execution.getTenantId())
            .executionId(execution.getId())
            .executionState(State.Type.KILLED)
            .build();

        // When
        var maybeExecutor = handler.handle(message);

        // Then: handler succeeds; the message handed to the async-operation service carries no
        // operationId, and emitProcessedIfAsync is a documented no-op in that case — so NO
        // processed event is emitted for this execution.
        assertThat(maybeExecutor).isPresent();
        assertThat(message.getOperationId()).isNull();
        verify(harness.asyncOperationService()).emitProcessedIfAsync(
            same(message),
            eq(execution.getTenantId()),
            eq(execution.getId()),
            eq(AsyncOperationProcessedEvent.Outcome.SUCCEEDED),
            isNull()
        );
    }

    @Test
    void shouldEmitFailedProcessedEventWhenHandlerThrows() {
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
        harness.executionStateStore().save(orphanExecution);
        var message = ExecutionKilledExecution.builder()
            .tenantId(tenantId)
            .executionId(executionId)
            .executionState(State.Type.KILLED)
            .operationId(operationId)
            .build();

        // When / Then: handler throws and still reports a FAILED processed outcome for the operation.
        assertThatThrownBy(() -> handler.handle(message))
            .isInstanceOf(RuntimeException.class);

        assertThat(message.getOperationId()).isEqualTo(operationId);
        verify(harness.asyncOperationService()).emitProcessedIfAsync(
            same(message),
            eq(tenantId),
            eq(executionId),
            eq(AsyncOperationProcessedEvent.Outcome.FAILED),
            any()
        );
    }

    @Test
    void shouldReturnEmptyWhenKillSwitchIsIgnore() {
        // Given — execution is ignored by the kill switch
        when(harness.killSwitchService().evaluate("exec-ignored")).thenReturn(EvaluationType.IGNORE);
        var message = ExecutionKilledExecution.builder()
            .tenantId("tenant").executionId("exec-ignored").isOnKillCascade(false).build();

        // When
        Optional<ExecutorContext> result = handler.handle(message);

        // Then
        assertThat(result).isEmpty();
        // The handler bails out before broadcasting any kill event.
        assertThat(harness.kills()).isEmpty();
    }
}
