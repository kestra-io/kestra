package io.kestra.executor.handler;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.DefaultFlowMetaStore;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.runners.FlowMetaStoreInterface;

import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
class ExecutionEventMessageHandlerFlowWithExceptionTest {
    @Inject
    private ExecutionEventMessageHandler executionEventMessageHandler;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowMetaStoreInterface flowMetaStore;

    @Inject
    KillSwitchService killSwitchService;

    @MockBean(DefaultFlowMetaStore.class)
    FlowMetaStoreInterface flowMetaStore() {
        return mock(FlowMetaStoreInterface.class);
    }

    @MockBean(KillSwitchService.class)
    KillSwitchService killSwitchService() {
        return mock(KillSwitchService.class);
    }

    @BeforeEach
    void setUp() {
        when(killSwitchService.evaluate(any(ExecutionEvent.class))).thenReturn(EvaluationType.PASS);
    }

    @Test
    void shouldFailExecutionWhenFlowResolvesWithException() {
        // Given — the metastore surfaces the flow as a FlowWithException (unparsable, or blocked at pre-flight)
        var flow = Fixtures.flow();
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        when(flowMetaStore.findByExecutionThenInjectDefaults(any()))
            .thenReturn(Optional.of(FlowWithException.from(flow, new IllegalStateException("blocked by governance policy"))));

        // When
        var maybeExecutor = executionEventMessageHandler.handle(new ExecutionEvent(execution, ExecutionEventType.CREATED));

        // Then — the execution fails fast instead of staying stuck (or crashing the executor)
        assertThat(maybeExecutor).isPresent();
        assertThat(maybeExecutor.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    void shouldLeaveTerminatedExecutionUntouchedWhenFlowResolvesWithException() {
        // Given — an already terminated execution whose flow later resolves as a FlowWithException
        var flow = Fixtures.flow();
        var execution = Execution.newExecution(flow, Collections.emptyList()).withState(State.Type.SUCCESS);
        executionRepository.save(execution);
        when(flowMetaStore.findByExecutionThenInjectDefaults(any()))
            .thenReturn(Optional.of(FlowWithException.from(flow, new IllegalStateException("blocked by governance policy"))));

        // When
        var maybeExecutor = executionEventMessageHandler.handle(new ExecutionEvent(execution, ExecutionEventType.CREATED));

        // Then — its terminal state is never rewritten
        assertThat(maybeExecutor).isEmpty();
    }
}
