package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LoopExecutionEvent;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.executor.testkit.ScriptedWorker;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

class LoopRetryNewExecutionTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant AFTER_ANY_RETRY_DELAY = Instant.parse("2100-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final FlowWithSource flow = Flows.yaml("""
        id: loop-retry-new-execution
        namespace: io.kestra.tests
        tasks:
          - id: loop
            type: io.kestra.plugin.core.flow.Loop
            concurrencyLimit: 1
            values: ["value 1", "value 2"]
            tasks:
              - id: flaky
                type: io.kestra.plugin.core.log.Log
                message: "{{ item.value }}"
                retry:
                  behavior: CREATE_NEW_EXECUTION
                  type: constant
                  interval: PT1S
                  maxAttempts: 2
        """);

    @Test
    void shouldRetryAnIterationInANewExecutionAndCountItOnce() {
        // Given
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        AtomicInteger attempts = new AtomicInteger();
        ScriptedWorker failsEveryFirstAttempt = task -> attempts.incrementAndGet() % 2 == 1 ? Results.failed(task, T0) : Results.success(task, T0);

        // When
        harness.run(created, failsEveryFirstAttempt);
        for (int i = 0; i < 4; i++) {
            harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
            harness.run(List.of(), failsEveryFirstAttempt);
        }

        // Then
        assertThat(harness.loopEvents()).extracting(LoopExecutionEvent::state)
            .as("a RETRIED iteration is replaced, only its replacement reports to the loop")
            .containsExactly(State.Type.SUCCESS, State.Type.SUCCESS);
        assertThat(attempts).as("each iteration fails once then succeeds in its replacement").hasValue(4);
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
    }

    @Test
    void shouldReportTheLastAttemptOnceRetriesAreExhausted() {
        // Given
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        AtomicInteger attempts = new AtomicInteger();
        ScriptedWorker alwaysFails = task -> {
            attempts.incrementAndGet();
            return Results.failed(task, T0);
        };

        // When
        harness.run(created, alwaysFails);
        for (int i = 0; i < 4; i++) {
            harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
            harness.run(List.of(), alwaysFails);
        }

        // Then
        assertThat(harness.loopEvents()).extracting(LoopExecutionEvent::state)
            .as("the RETRIED first attempt stays silent, the failed replacement reports")
            .containsExactly(State.Type.FAILED);
        assertThat(attempts).as("maxAttempts: 2 allows one replacement").hasValue(2);
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED);
    }
}
