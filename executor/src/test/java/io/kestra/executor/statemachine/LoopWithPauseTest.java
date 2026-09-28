package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.executor.command.Resume;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.LoopExecutionEvent;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.executor.testkit.Trace;
import io.kestra.plugin.core.flow.Pause;

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

    private static final FlowWithSource CONCURRENT_LOOP_WITH_PAUSE = Flows.yaml("""
        id: concurrent-loop-with-pause
        namespace: io.kestra.tests
        tasks:
          - id: loop
            type: io.kestra.plugin.core.flow.Loop
            values: [1, 2]
            concurrencyLimit: 2
            tasks:
              - id: pause
                type: io.kestra.plugin.core.flow.Pause
                pauseDuration: "PT{{ item.value }}S"
        """);

    private static final FlowWithSource CONCURRENT_LOOP_WITH_MANUAL_PAUSE = Flows.yaml("""
        id: concurrent-loop-with-manual-pause
        namespace: io.kestra.tests
        tasks:
          - id: loop
            type: io.kestra.plugin.core.flow.Loop
            values: [1, 2]
            concurrencyLimit: 2
            tasks:
              - id: pause
                type: io.kestra.plugin.core.flow.Pause
        """);

    private static final FlowWithSource LOOP_PAUSING_ITS_FIRST_ITERATION = Flows.yaml("""
        id: loop-pausing-its-first-iteration
        namespace: io.kestra.tests
        tasks:
          - id: loop
            type: io.kestra.plugin.core.flow.Loop
            values: [1, 2, 3]
            concurrencyLimit: 2
            tasks:
              - id: first
                type: io.kestra.plugin.core.flow.If
                condition: "{{ item.value == '1' }}"
                then:
                  - id: pause
                    type: io.kestra.plugin.core.flow.Pause
        """);

    private static final FlowWithSource LOOP_WITH_TWO_PAUSES = Flows.yaml("""
        id: loop-with-two-pauses
        namespace: io.kestra.tests
        tasks:
          - id: loop
            type: io.kestra.plugin.core.flow.Loop
            values: [1]
            tasks:
              - id: first
                type: io.kestra.plugin.core.flow.Pause
                pauseDuration: PT1S
              - id: second
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

        List<Trace.Emission> onFirstExpiry = harness.tickExecutionDelays(nextDelayDate().plusSeconds(1));

        assertThat(onFirstExpiry.stream().filter(e -> e.queue().equals("loopExecutionEvent")).map(e -> e.as(LoopExecutionEvent.class).state()))
            .as("the resumed iteration tells the parent it restarted")
            .containsExactly(State.Type.RESTARTED);

        Trace afterFirstExpiry = harness.run(List.of(), ScriptedWorker.succeeding(T0));
        assertThat(harness.stateOf(parent)).as("the second iteration paused in turn").isEqualTo(State.Type.PAUSED);
        assertThat(harness).hasPendingDelays(1);

        harness.tickExecutionDelays(nextDelayDate().plusSeconds(1));
        harness.run(List.of(), ScriptedWorker.succeeding(T0));

        assertThat(harness).hasExecutionInState(parent, State.Type.SUCCESS);
        assertThat(harness).hasPendingDelays(0);
        assertThat(parentHistory(parent))
            .as("the parent resumed from each pause before moving on")
            .containsSubsequence(State.Type.PAUSED, State.Type.RUNNING, State.Type.PAUSED, State.Type.RUNNING, State.Type.SUCCESS);

        List<String> iterations = loopIterationIds(firstIteration, afterFirstExpiry);
        assertThat(iterations).as("one sub-execution per loop value").hasSize(2);
        iterations.forEach(
            id -> assertThat(harness.executionStateStore().findByIdWithoutAcl(id).getState().getCurrent())
                .as("iteration <%s> ran its pause to the end", id)
                .isEqualTo(State.Type.SUCCESS)
        );
    }

    @Test
    void theParentOfConcurrentPausedIterationsStaysPausedUntilTheLastOneResumes() {
        harness.registerFlow(CONCURRENT_LOOP_WITH_PAUSE);
        Execution parent = Executions.created(CONCURRENT_LOOP_WITH_PAUSE);

        harness.run(parent, ScriptedWorker.succeeding(T0));

        assertThat(harness.stateOf(parent)).isEqualTo(State.Type.PAUSED);
        assertThat(harness).hasPendingDelays(2);

        harness.tickExecutionDelays(nextDelayDate().plusMillis(1));
        harness.run(List.of(), ScriptedWorker.succeeding(T0));

        assertThat(harness).hasPendingDelays(1);
        assertThat(harness.stateOf(parent)).as("the second iteration is still paused").isEqualTo(State.Type.PAUSED);

        harness.tickExecutionDelays(nextDelayDate().plusMillis(1));
        harness.run(List.of(), ScriptedWorker.succeeding(T0));

        assertThat(harness).hasExecutionInState(parent, State.Type.SUCCESS);
    }

    @Test
    void concurrentPausedIterationsExpiringTogetherLetTheParentSucceed() {
        harness.registerFlow(CONCURRENT_LOOP_WITH_PAUSE);
        Execution parent = Executions.created(CONCURRENT_LOOP_WITH_PAUSE);

        harness.run(parent, ScriptedWorker.succeeding(T0));
        harness.tickExecutionDelays(lastDelayDate().plusSeconds(1));
        harness.run(List.of(), ScriptedWorker.succeeding(T0));

        assertThat(harness).hasExecutionInState(parent, State.Type.SUCCESS);
        assertThat(parentHistory(parent)).doesNotContain(State.Type.FAILED);
    }

    @Test
    void resumingTheParentResumesEveryPausedIteration() {
        harness.registerFlow(CONCURRENT_LOOP_WITH_MANUAL_PAUSE);
        Execution parent = Executions.created(CONCURRENT_LOOP_WITH_MANUAL_PAUSE);

        harness.run(parent, ScriptedWorker.succeeding(T0));
        assertThat(harness.stateOf(parent)).isEqualTo(State.Type.PAUSED);

        harness.run(List.of(resume(parent.getId())), ScriptedWorker.succeeding(T0));

        assertThat(harness).hasExecutionInState(parent, State.Type.SUCCESS);
        pausedIterationIds().forEach(
            id -> assertThat(harness.executionStateStore().findByIdWithoutAcl(id).getState().getCurrent())
                .as("iteration <%s> was resumed with its parent", id)
                .isEqualTo(State.Type.SUCCESS)
        );
    }

    @Test
    void resumingAPausedIterationResumesItsParent() {
        harness.registerFlow(CONCURRENT_LOOP_WITH_MANUAL_PAUSE);
        Execution parent = Executions.created(CONCURRENT_LOOP_WITH_MANUAL_PAUSE);

        harness.run(parent, ScriptedWorker.succeeding(T0));
        List<String> paused = pausedIterationIds();
        assertThat(paused).hasSize(2);

        harness.run(List.of(resume(paused.get(0))), ScriptedWorker.succeeding(T0));
        assertThat(harness.stateOf(parent)).as("the other iteration is still paused").isEqualTo(State.Type.PAUSED);

        harness.run(List.of(resume(paused.get(1))), ScriptedWorker.succeeding(T0));
        assertThat(harness).hasExecutionInState(parent, State.Type.SUCCESS);
    }

    @Test
    void anIterationStartedWhileTheParentIsPausedStillRuns() {
        harness.registerFlow(LOOP_PAUSING_ITS_FIRST_ITERATION);
        Execution parent = Executions.created(LOOP_PAUSING_ITS_FIRST_ITERATION);

        Trace untilPaused = harness.run(parent, ScriptedWorker.succeeding(T0));

        assertThat(harness.stateOf(parent)).isEqualTo(State.Type.PAUSED);
        List<String> iterations = loopIterationIds(untilPaused);
        assertThat(iterations).as("the second iteration ended, so the third one started").hasSize(3);
        assertThat(harness.executionStateStore().findByIdWithoutAcl(iterations.get(2)).getState().getCurrent())
            .as("the third iteration did not inherit the paused state of its parent")
            .isEqualTo(State.Type.SUCCESS);

        harness.run(List.of(resume(pausedIterationIds().getFirst())), ScriptedWorker.succeeding(T0));

        assertThat(harness).hasExecutionInState(parent, State.Type.SUCCESS);
    }

    @Test
    void anIterationPausingAgainRightAfterItsResumeKeepsTheParentPaused() {
        harness.registerFlow(LOOP_WITH_TWO_PAUSES);
        Execution parent = Executions.created(LOOP_WITH_TWO_PAUSES);

        harness.run(parent, ScriptedWorker.succeeding(T0));
        harness.tickExecutionDelays(nextDelayDate().plusSeconds(1));
        harness.run(List.of(), ScriptedWorker.succeeding(T0));

        assertThat(harness).hasPendingDelays(1);
        assertThat(harness.stateOf(parent)).as("the iteration paused on its second pause").isEqualTo(State.Type.PAUSED);

        harness.tickExecutionDelays(nextDelayDate().plusSeconds(1));
        harness.run(List.of(), ScriptedWorker.succeeding(T0));

        assertThat(harness).hasExecutionInState(parent, State.Type.SUCCESS);
        assertThat(harness.loopEvents().stream().map(LoopExecutionEvent::state))
            .as("each resume is announced once, so it cannot be mistaken for the resume of a later pause")
            .containsExactly(State.Type.PAUSED, State.Type.RESTARTED, State.Type.PAUSED, State.Type.RESTARTED, State.Type.SUCCESS);
    }

    private Resume resume(String executionId) {
        return Resume.from(harness.executionStateStore().findByIdWithoutAcl(executionId), Pause.Resumed.now());
    }

    private List<String> pausedIterationIds() {
        return harness.loopEvents().stream()
            .filter(event -> event.state() == State.Type.PAUSED)
            .map(LoopExecutionEvent::executionId)
            .distinct()
            .toList();
    }

    private List<State.Type> parentHistory(Execution parent) {
        return harness.executionStateStore().findByIdWithoutAcl(parent.getId()).getState().getHistories().stream()
            .map(State.History::getState)
            .toList();
    }

    private State.Type loopTaskRunState(Execution parent) {
        return harness.executionStateStore().findByIdWithoutAcl(parent.getId()).getTaskRunList().stream()
            .filter(taskRun -> taskRun.getTaskId().equals("loop"))
            .findFirst()
            .orElseThrow()
            .getState()
            .getCurrent();
    }

    private Instant nextDelayDate() {
        return harness.executionDelayStateStore().pending().stream().map(ExecutionDelay::getDate).min(Comparator.naturalOrder()).orElseThrow();
    }

    private Instant lastDelayDate() {
        return harness.executionDelayStateStore().pending().stream().map(ExecutionDelay::getDate).max(Comparator.naturalOrder()).orElseThrow();
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
