package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.LoopExecutionEvent;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.executor.testkit.Trace;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

class LoopWithPauseTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static final FlowWithSource LOOP_WITH_PAUSE = Flows.yaml("""
        id: loop-with-pause
        namespace: io.kestra.tests
        tasks:
          - id: loop
            type: io.kestra.plugin.core.flow.Loop
            values: [1, 2]
            tasks:
              - id: pause
                type: io.kestra.plugin.core.flow.Pause
                pauseDuration: PT1S
        """);

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    @Test
    void aPausedIterationPausesTheParentAndItsExpiryResumesItUntilEveryIterationSucceeds() {
        harness.registerFlow(LOOP_WITH_PAUSE);
        Execution parent = Executions.created(LOOP_WITH_PAUSE);

        Trace firstIteration = harness.run(parent, ScriptedWorker.succeeding(T0));

        assertThat(harness.stateOf(parent)).as("the first iteration paused, so the parent follows it").isEqualTo(State.Type.PAUSED);
        assertThat(loopTaskRunState(parent)).as("the loop task run of the parent is paused too").isEqualTo(State.Type.PAUSED);
        assertThat(harness).hasPendingDelays(1);

        List<Trace.Emission> onFirstExpiry = harness.tickExecutionDelays(pendingDelayDate().plusSeconds(1));

        assertThat(onFirstExpiry.stream().filter(e -> e.queue().equals("loopExecutionEvent")).map(e -> e.as(LoopExecutionEvent.class).state()))
            .as("the expired pause tells the parent its iteration restarted")
            .containsExactly(State.Type.RESTARTED);

        Trace secondIteration = harness.run(List.of(), ScriptedWorker.succeeding(T0));

        assertThat(harness.stateOf(parent)).as("the second iteration paused in turn").isEqualTo(State.Type.PAUSED);
        assertThat(harness).hasPendingDelays(1);

        harness.tickExecutionDelays(pendingDelayDate().plusSeconds(1));
        harness.run(List.of(), ScriptedWorker.succeeding(T0));

        assertThat(harness).hasExecutionInState(parent, State.Type.SUCCESS);
        assertThat(harness).hasPendingDelays(0);
        assertThat(harness.executionStateStore().findByIdWithoutAcl(parent.getId()).getState().getHistories())
            .extracting(State.History::getState)
            .as("the parent resumed from each pause before moving on")
            .containsSubsequence(State.Type.PAUSED, State.Type.RUNNING, State.Type.PAUSED, State.Type.RUNNING, State.Type.SUCCESS);

        List<String> iterations = loopIterationIds(firstIteration, secondIteration);
        assertThat(iterations).as("one sub-execution per loop value").hasSize(2);
        iterations.forEach(id -> assertThat(harness.executionStateStore().findByIdWithoutAcl(id).getState().getCurrent())
            .as("iteration <%s> ran its pause to the end", id)
            .isEqualTo(State.Type.SUCCESS));
    }

    private State.Type loopTaskRunState(Execution parent) {
        return harness.executionStateStore().findByIdWithoutAcl(parent.getId()).getTaskRunList().stream()
            .filter(taskRun -> taskRun.getTaskId().equals("loop"))
            .findFirst()
            .orElseThrow()
            .getState()
            .getCurrent();
    }

    private Instant pendingDelayDate() {
        return harness.executionDelayStateStore().pending().getFirst().getDate();
    }

    private static List<String> loopIterationIds(Trace... traces) {
        return Arrays.stream(traces)
            .flatMap(trace -> trace.emitted("execution"))
            .map(emission -> emission.as(Execution.class))
            .filter(execution -> execution.getKind() == ExecutionKind.LOOP)
            .map(Execution::getId)
            .distinct()
            .toList();
    }
}
