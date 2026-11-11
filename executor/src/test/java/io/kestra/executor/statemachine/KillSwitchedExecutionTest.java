package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.executor.testkit.Trace;

import reactor.core.publisher.Flux;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class KillSwitchedExecutionTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    @BeforeEach
    void noChildExecutionsToCascadeTheKillTo() {
        doReturn(Flux.empty()).when(harness.executionService()).killSubflowExecutions(any(), any());
        doReturn(List.of()).when(harness.executionService()).killLoopSubExecutions(any(), any());
    }

    @Test
    void shouldEndKilledWhenANewExecutionIsKillSwitchedOnTheExecutionQueue() {
        FlowWithSource flow = registeredFlow();
        Execution execution = Executions.created(flow);
        killSwitchActive(EvaluationType.KILL);

        Trace trace = harness.run(List.of(execution), ScriptedWorker.succeeding(T0));

        assertThat(harness).as("a kill-switched execution that never started must not stay KILLING").hasExecutionInState(execution, State.Type.KILLED);
        assertThat(terminatedEvents(trace, execution)).as("the execution went through its terminal processing").isEqualTo(1);
    }

    @Test
    void shouldRunTheTerminalProcessingWhenANewExecutionIsCancelledByTheKillSwitchOnTheExecutionQueue() {
        FlowWithSource flow = registeredFlow();
        Execution execution = Executions.created(flow);
        killSwitchActive(EvaluationType.CANCEL);

        Trace trace = harness.run(List.of(execution), ScriptedWorker.succeeding(T0));

        assertThat(harness).hasExecutionInState(execution, State.Type.CANCELLED);
        assertThat(terminatedEvents(trace, execution)).as("the execution went through its terminal processing").isEqualTo(1);
    }

    private void killSwitchActive(EvaluationType evaluationType) {
        when(harness.killSwitchService().evaluate(any(Execution.class))).thenReturn(evaluationType);
        when(harness.killSwitchService().evaluate(any(ExecutionEvent.class))).thenReturn(evaluationType);
        when(harness.killSwitchService().evaluate(anyString())).thenReturn(evaluationType);
    }

    private static long terminatedEvents(Trace trace, Execution execution) {
        return trace.emitted("executionEvent")
            .map(e -> e.as(ExecutionEvent.class))
            .filter(e -> e.executionId().equals(execution.getId()) && e.eventType() == ExecutionEventType.TERMINATED)
            .count();
    }

    private FlowWithSource registeredFlow() {
        FlowWithSource flow = Flows.of(Flows.log("a"));
        harness.registerFlow(flow);
        return flow;
    }
}
