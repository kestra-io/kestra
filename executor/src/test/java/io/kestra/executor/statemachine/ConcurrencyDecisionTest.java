package io.kestra.executor.statemachine;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionRunning;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layer-1 state-machine tests of the concurrency-limit decision
 * ({@code ExecutorService#processExecutionRunning}): a pure switch over
 * (flow concurrency config, running count) — no Micronaut, no database, no queues.
 */
class ConcurrencyDecisionTest {

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    static Stream<Arguments> behaviorsAtLimit() {
        return Stream.of(
            Arguments.of(Concurrency.Behavior.QUEUE, State.Type.QUEUED, ExecutionRunning.ConcurrencyState.QUEUED),
            Arguments.of(Concurrency.Behavior.CANCEL, State.Type.CANCELLED, ExecutionRunning.ConcurrencyState.CANCELLED),
            Arguments.of(Concurrency.Behavior.FAIL, State.Type.FAILED, ExecutionRunning.ConcurrencyState.FAILED)
        );
    }

    @ParameterizedTest
    @MethodSource("behaviorsAtLimit")
    void shouldApplyBehaviorWhenRunningCountReachesLimit(Concurrency.Behavior behavior, State.Type expectedState, ExecutionRunning.ConcurrencyState expectedConcurrencyState) {
        // Given a flow limited to 2 concurrent executions, already running 2
        FlowWithSource flow = flowWithConcurrency(behavior, 2);
        ExecutionRunning executionRunning = executionRunning(flow);

        // When
        ExecutionRunning decided = harness.executorService().processExecutionRunning(flow, 2, executionRunning);

        // Then
        assertThat(decided.getExecution().getState().getCurrent()).isEqualTo(expectedState);
        assertThat(decided.getConcurrencyState()).isEqualTo(expectedConcurrencyState);
    }

    @ParameterizedTest
    @MethodSource("behaviorsAtLimit")
    void shouldApplyBehaviorWhenRunningCountExceedsLimit(Concurrency.Behavior behavior, State.Type expectedState, ExecutionRunning.ConcurrencyState expectedConcurrencyState) {
        // Given a flow limited to 2 concurrent executions, already running 5 (over-limit race aftermath)
        FlowWithSource flow = flowWithConcurrency(behavior, 2);
        ExecutionRunning executionRunning = executionRunning(flow);

        // When
        ExecutionRunning decided = harness.executorService().processExecutionRunning(flow, 5, executionRunning);

        // Then
        assertThat(decided.getExecution().getState().getCurrent()).isEqualTo(expectedState);
        assertThat(decided.getConcurrencyState()).isEqualTo(expectedConcurrencyState);
    }

    @Test
    void shouldRunWhenRunningCountIsUnderLimit() {
        // Given a flow limited to 2 concurrent executions, running only 1
        FlowWithSource flow = flowWithConcurrency(Concurrency.Behavior.QUEUE, 2);
        ExecutionRunning executionRunning = executionRunning(flow);

        // When
        ExecutionRunning decided = harness.executorService().processExecutionRunning(flow, 1, executionRunning);

        // Then
        assertThat(decided.getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(decided.getConcurrencyState()).isEqualTo(ExecutionRunning.ConcurrencyState.RUNNING);
    }

    @Test
    void shouldRunWhenFlowHasNoConcurrencyLimit() {
        // Given a flow without any concurrency configuration (removed while executions were queued)
        FlowWithSource flow = Flows.of(logTask());
        ExecutionRunning executionRunning = executionRunning(flow);

        // When
        ExecutionRunning decided = harness.executorService().processExecutionRunning(flow, 100, executionRunning);

        // Then
        assertThat(decided.getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(decided.getConcurrencyState()).isEqualTo(ExecutionRunning.ConcurrencyState.RUNNING);
    }

    private static FlowWithSource flowWithConcurrency(Concurrency.Behavior behavior, int limit) {
        return Flows.withConcurrency(
            Concurrency.builder().behavior(behavior).limit(limit).build(),
            logTask()
        );
    }

    private static ExecutionRunning executionRunning(FlowWithSource flow) {
        Execution execution = Executions.created(flow);
        return ExecutionRunning.builder()
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .execution(execution)
            .concurrencyState(ExecutionRunning.ConcurrencyState.CREATED)
            .build();
    }

    private static Log logTask() {
        return Log.builder().id("log").type(Log.class.getName()).message("hello").build();
    }
}
