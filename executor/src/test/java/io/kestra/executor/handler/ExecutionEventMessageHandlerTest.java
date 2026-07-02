package io.kestra.executor.handler;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import io.micronaut.test.annotation.MockBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.quota.Quota;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.services.QuotaService;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.KillSwitchActionService;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@KestraTest
class ExecutionEventMessageHandlerTest {
    @Inject
    private ExecutionEventMessageHandler executionEventMessageHandler;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    KillSwitchService killSwitchService;

    @Inject
    KillSwitchActionService killSwitchActionService;

    @Inject
    QuotaService quotaService;

    @MockBean(KillSwitchService.class)
    KillSwitchService killSwitchService() {
        return mock(KillSwitchService.class);
    }

    @MockBean(KillSwitchActionService.class)
    KillSwitchActionService killSwitchActionService() {
        return mock(KillSwitchActionService.class);
    }

    @MockBean(QuotaService.class)
    QuotaService quotaService() {
        return mock(QuotaService.class);
    }

    @BeforeEach
    void setUp() {
        when(killSwitchService.evaluate(any(ExecutionEvent.class))).thenReturn(EvaluationType.PASS);
    }

    @Test
    void shouldReturnEmptyForNonExistingExecution() {
        var executionEvent = new ExecutionEvent("tenant", "namespace", "flow", "execution", Instant.now(), ExecutionEventType.CREATED);

        var maybeExecutor = executionEventMessageHandler.handle(executionEvent);

        assertThat(maybeExecutor).isEmpty();
    }

    @Test
    void shouldReturnAnExecutorForExistingExecution() {
        var flow = flowRepository.create(GenericFlow.of(Fixtures.flow()));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var executionEvent = new ExecutionEvent(execution, ExecutionEventType.CREATED);

        var maybeExecutor = executionEventMessageHandler.handle(executionEvent);

        assertThat(maybeExecutor).isPresent();
        assertThat(maybeExecutor.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(maybeExecutor.get().getExecution().getTaskRunList()).hasSize(1);
    }

    @Test
    void shouldNotApplyKillActionWhenKillSwitchPasses() {
        // PASS (default from setUp) → kill action never called
        var executionEvent = new ExecutionEvent("tenant", "namespace", "flow", "exec-nonexistent", null, ExecutionEventType.UPDATED);

        executionEventMessageHandler.handle(executionEvent);

        verify(killSwitchActionService, never()).handle(any(), any(), any());
    }

    @Test
    void shouldReturnEmptyAndCallKillActionWhenKillSwitched() {
        // Save an execution so findById returns it
        var flow = flowRepository.create(GenericFlow.of(Fixtures.flow()));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var executionEvent = new ExecutionEvent(execution, ExecutionEventType.UPDATED);
        when(killSwitchService.evaluate(executionEvent)).thenReturn(EvaluationType.IGNORE);

        Optional<ExecutorContext> result = executionEventMessageHandler.handle(executionEvent);

        assertThat(result).isEmpty();
        verify(killSwitchActionService).handle(EvaluationType.IGNORE, execution.getTenantId(), execution.getId());
    }

    @Test
    void shouldNotApplyKillActionWhenFindByIdReturnsNull() {
        // Non-existent execution → findById returns null → guard skips kill action
        var executionEvent = new ExecutionEvent("tenant", "namespace", "flow", "exec-missing", null, ExecutionEventType.UPDATED);
        when(killSwitchService.evaluate(executionEvent)).thenReturn(EvaluationType.IGNORE);

        executionEventMessageHandler.handle(executionEvent);

        verify(killSwitchActionService, never()).handle(any(), any(), any());
    }

    @Test
    void shouldNotApplyKillActionWhenExecutionIsAlreadyKilling() {
        // KILL evaluation but execution is already KILLING → isKillSwitched returns false
        var flow = flowRepository.create(GenericFlow.of(Fixtures.flow()));
        var execution = Execution.newExecution(flow, Collections.emptyList()).withState(State.Type.KILLING);
        executionRepository.save(execution);
        var executionEvent = new ExecutionEvent(execution, ExecutionEventType.UPDATED);
        when(killSwitchService.evaluate(executionEvent)).thenReturn(EvaluationType.KILL);

        executionEventMessageHandler.handle(executionEvent);

        verify(killSwitchActionService, never()).handle(any(), any(), any());
    }

    @Test
    void shouldFailExecutionWhenQuotaExceededWithFailBehavior() {
        // Given
        var quota = Quota.builder()
            .duration(Duration.ofHours(1))
            .limit(10L)
            .behavior(Quota.Behavior.FAIL)
            .build();
        var flow = flowRepository.create(GenericFlow.of(Fixtures.flowWithQuotas(quota)));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var executionEvent = new ExecutionEvent(execution, ExecutionEventType.CREATED);
        when(quotaService.checkAndIncrement(any())).thenReturn(Optional.of(quota));

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
        var flow = flowRepository.create(GenericFlow.of(Fixtures.flowWithQuotas(quota)));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var executionEvent = new ExecutionEvent(execution, ExecutionEventType.CREATED);
        when(quotaService.checkAndIncrement(any())).thenReturn(Optional.of(quota));

        // When
        var maybeExecutor = executionEventMessageHandler.handle(executionEvent);

        // Then
        assertThat(maybeExecutor).isPresent();
        assertThat(maybeExecutor.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.CANCELLED);
    }
}
