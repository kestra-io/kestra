package io.kestra.core.services;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;

import io.micronaut.context.event.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionServicePauseForceRunTest {
    @Mock
    private ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher;

    @Mock
    private ConcurrencyLimitService concurrencyLimitService;

    @InjectMocks
    private ExecutionService executionService;

    private final Flow flow = Flow.builder().id("flow").namespace("io.kestra.tests").build();

    private static Execution execution(State.Type state) {
        return Execution.builder()
            .id("execution")
            .namespace("io.kestra.tests")
            .flowId("flow")
            .state(new State())
            .build()
            .withState(state);
    }

    @Test
    void shouldPauseWhenRunning() throws Exception {
        Execution paused = executionService.pause(execution(State.Type.RUNNING));

        assertThat(paused.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void shouldThrowWhenPausingNonRunningExecution() {
        assertThatThrownBy(() -> executionService.pause(execution(State.Type.CREATED)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("The execution is not running");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldThrowWhenForceRunningTerminatedExecution() {
        assertThatThrownBy(() -> executionService.forceRun(execution(State.Type.SUCCESS), flow))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Only non terminated executions can be forced run.");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldMoveToRunningWhenForceRunningCreatedExecution() throws Exception {
        Execution forced = executionService.forceRun(execution(State.Type.CREATED), flow);

        assertThat(forced.getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void shouldUnqueueWhenForceRunningQueuedExecution() throws Exception {
        Execution queued = execution(State.Type.QUEUED);
        Execution unqueued = queued.withState(State.Type.RUNNING);
        when(concurrencyLimitService.unqueue(queued, State.Type.RUNNING)).thenReturn(unqueued);

        Execution forced = executionService.forceRun(queued, flow);

        assertThat(forced).isSameAs(unqueued);
        verify(concurrencyLimitService).unqueue(queued, State.Type.RUNNING);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void shouldResumeWhenForceRunningPausedExecution() throws Exception {
        Execution forced = executionService.forceRun(execution(State.Type.PAUSED), flow);

        assertThat(forced.getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void shouldReprocessWhenForceRunningOtherNonTerminalExecution() throws Exception {
        Execution running = execution(State.Type.RUNNING);

        Execution forced = executionService.forceRun(running, flow);

        assertThat(forced).isSameAs(running);
        verify(concurrencyLimitService, never()).unqueue(any(), any());
        verify(eventPublisher).publishEvent(any());
    }
}
