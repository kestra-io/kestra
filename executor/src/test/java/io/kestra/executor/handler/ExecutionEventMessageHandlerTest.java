package io.kestra.executor.handler;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.quota.Quota;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionEventMessageHandlerTest {
    private ExecutorTestHarness harness;
    private ExecutionEventMessageHandler executionEventMessageHandler;

    @BeforeEach
    void setUp() {
        harness = ExecutorTestHarness.create();
        executionEventMessageHandler = harness.executionEventMessageHandler();
    }

    @Test
    void shouldReturnEmptyForNonExistingExecution() {
        var executionEvent = new ExecutionEvent("tenant", "namespace", "flow", "execution", Instant.now(), ExecutionEventType.CREATED);

        var maybeExecutor = executionEventMessageHandler.handle(executionEvent);

        assertThat(maybeExecutor).isEmpty();
    }

    @Test
    void shouldReturnAnExecutorForExistingExecution() {
        var flow = Flows.of(Fixtures.flow());
        harness.registerFlow(flow);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        harness.executionStateStore().save(execution);
        var executionEvent = new ExecutionEvent(execution, ExecutionEventType.CREATED);

        var maybeExecutor = executionEventMessageHandler.handle(executionEvent);

        assertThat(maybeExecutor).isPresent();
        assertThat(maybeExecutor.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(maybeExecutor.get().getExecution().getTaskRunList()).hasSize(1);
    }

    @Test
    void shouldStampTheClaimedConcurrencyScopesOnAdmission() {
        // Given: a flow with a concurrency limit
        var flow = Flows.of(
            Fixtures.flowWithConcurrency(
                Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(2).build()
            )
        );
        harness.registerFlow(flow);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        harness.executionStateStore().save(execution);

        // When
        var maybeExecutor = executionEventMessageHandler.handle(new ExecutionEvent(execution, ExecutionEventType.CREATED));

        // Then: the execution remembers the scopes it claimed a slot in, so the release
        // decrements exactly these even if the limit definition changes while it runs
        assertThat(maybeExecutor).isPresent();
        assertThat(maybeExecutor.get().getExecution().getMetadata().getConcurrencyScopes())
            .containsExactly("tenant|namespace|" + flow.getId());
    }

    @Test
    void shouldNotStampConcurrencyScopesWhenNoLimitApplies() {
        // Given: a flow without any concurrency limit
        var flow = Flows.of(Fixtures.flow());
        harness.registerFlow(flow);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        harness.executionStateStore().save(execution);

        // When
        var maybeExecutor = executionEventMessageHandler.handle(new ExecutionEvent(execution, ExecutionEventType.CREATED));

        // Then
        assertThat(maybeExecutor).isPresent();
        assertThat(maybeExecutor.get().getExecution().getMetadata().getConcurrencyScopes()).isNull();
    }

    @Test
    void shouldNotApplyKillActionWhenKillSwitchPasses() {
        // PASS (harness default) → kill action never called
        var executionEvent = new ExecutionEvent("tenant", "namespace", "flow", "exec-nonexistent", null, ExecutionEventType.UPDATED);

        executionEventMessageHandler.handle(executionEvent);

        verify(harness.killSwitchActionService(), never()).handle(any(), any(), any());
    }

    @Test
    void shouldReturnEmptyAndCallKillActionWhenKillSwitched() {
        // Save an execution so findById returns it
        var flow = Flows.of(Fixtures.flow());
        harness.registerFlow(flow);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        harness.executionStateStore().save(execution);
        var executionEvent = new ExecutionEvent(execution, ExecutionEventType.UPDATED);
        when(harness.killSwitchService().evaluate(executionEvent)).thenReturn(EvaluationType.IGNORE);

        Optional<ExecutorContext> result = executionEventMessageHandler.handle(executionEvent);

        assertThat(result).isEmpty();
        verify(harness.killSwitchActionService()).handle(EvaluationType.IGNORE, execution.getTenantId(), execution.getId());
    }

    @Test
    void shouldNotApplyKillActionWhenFindByIdReturnsNull() {
        // Non-existent execution → findById returns null → guard skips kill action
        var executionEvent = new ExecutionEvent("tenant", "namespace", "flow", "exec-missing", null, ExecutionEventType.UPDATED);
        when(harness.killSwitchService().evaluate(executionEvent)).thenReturn(EvaluationType.IGNORE);

        executionEventMessageHandler.handle(executionEvent);

        verify(harness.killSwitchActionService(), never()).handle(any(), any(), any());
    }

    @Test
    void shouldNotApplyKillActionWhenExecutionIsAlreadyKilling() {
        // KILL evaluation but execution is already KILLING → isKillSwitched returns false
        var flow = Flows.of(Fixtures.flow());
        harness.registerFlow(flow);
        var execution = Execution.newExecution(flow, Collections.emptyList()).withState(State.Type.KILLING);
        harness.executionStateStore().save(execution);
        var executionEvent = new ExecutionEvent(execution, ExecutionEventType.UPDATED);
        when(harness.killSwitchService().evaluate(executionEvent)).thenReturn(EvaluationType.KILL);

        executionEventMessageHandler.handle(executionEvent);

        verify(harness.killSwitchActionService(), never()).handle(any(), any(), any());
    }

    @Test
    void shouldFailExecutionWhenQuotaExceededWithFailBehavior() {
        // Given
        var quota = Quota.builder()
            .duration(Duration.ofHours(1))
            .limit(10L)
            .behavior(Quota.Behavior.FAIL)
            .build();
        var flow = Flows.of(Fixtures.flowWithQuotas(quota));
        harness.registerFlow(flow);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        harness.executionStateStore().save(execution);
        var executionEvent = new ExecutionEvent(execution, ExecutionEventType.CREATED);
        when(harness.quotaService().checkAndIncrement(any())).thenReturn(Optional.of(quota));

        // When
        var maybeExecutor = executionEventMessageHandler.handle(executionEvent);

        // Then
        assertThat(maybeExecutor).isPresent();
        assertThat(maybeExecutor.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    void shouldCancelExecutionWhenQuotaExceededWithCancelBehavior() {
        // Given
        var quota = Quota.builder()
            .duration(Duration.ofHours(1))
            .limit(10L)
            .behavior(Quota.Behavior.CANCEL)
            .build();
        var flow = Flows.of(Fixtures.flowWithQuotas(quota));
        harness.registerFlow(flow);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        harness.executionStateStore().save(execution);
        var executionEvent = new ExecutionEvent(execution, ExecutionEventType.CREATED);
        when(harness.quotaService().checkAndIncrement(any())).thenReturn(Optional.of(quota));

        // When
        var maybeExecutor = executionEventMessageHandler.handle(executionEvent);

        // Then
        assertThat(maybeExecutor).isPresent();
        assertThat(maybeExecutor.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.CANCELLED);
    }
}
