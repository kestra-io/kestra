package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.plugin.core.flow.Sequential;
import io.kestra.plugin.core.log.Log;

import static io.kestra.executor.testkit.ExecutorContextAssert.assertThat;

/**
 * Layer-1 KILLING-cascade decision matrix: what the executor does with an execution already in
 * KILLING — kill CREATED taskruns ({@code ExecutorService#handleCreatedKilling}), close the
 * execution once every taskrun is terminal ({@code ExecutorService#handleKilling}), wait while a
 * worker still owns a RUNNING taskrun, and kill a KILLING flowable parent with a terminal attempt
 * once its started children are terminal (the {@code withStateAndAttempt} fix in
 * {@code ExecutorService#childWorkerTaskResult}).
 * Twins the decision logic behind AbstractRunnerTest#killedFlowableTaskRunShouldHaveTerminalAttempt
 * and the ExecutionControllerRunnerTest kill tests (which boot full runners and kill over queues).
 * No Micronaut, no database, no queues.
 */
class KillingDecisionTest {

    private static final Instant BASE = Instant.parse("2026-07-06T10:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    // --- handleCreatedKilling: CREATED taskruns are killed by the executor itself

    @Test
    void shouldKillCreatedTaskRunWhenExecutionIsKilling() {
        // Given: a KILLING execution whose only taskrun never reached a worker (still CREATED)
        FlowWithSource flow = Flows.of(logTask("t1"));
        harness.registerFlow(flow);
        Execution execution = killingExecution(flow);
        TaskRun created = taskRun(flow, execution, "t1", null, stateOf(State.Type.CREATED), null);
        execution = execution.withTaskRunList(List.of(created));

        // When
        ExecutorContext context = harness.process(flow, execution);

        // Then: handleCreatedKilling turns the CREATED taskrun into a KILLED WorkerTaskResult and
        // merges it through addWorkerTaskResults — the from-label recorded for the merge is
        // "addWorkerTaskResult" (handleCreatedKilling never calls withExecution under its own name).
        // With the taskrun now terminal, handleKilling closes the execution in the same cycle.
        assertThat(context)
            .hasTaskRunInState("t1", State.Type.KILLED)
            .executionInState(State.Type.KILLED)
            .updatedFrom("addWorkerTaskResult")
            .updatedFrom("handleKilling")
            .transitioned(State.Type.KILLING, State.Type.KILLED)
            .hasNoWorkerTasks()
            .hasNoNexts();
        Assertions.assertThat(harness.kills()).isEmpty();
    }

    // --- handleKilling: the execution closes once every taskrun is terminal

    @Test
    void shouldKillExecutionWhenAllTaskRunsAlreadyTerminal() {
        // Given: a two-task flow killed after t1 succeeded — t2 was never created (handleNext is
        // skipped while KILLING), so handleEnd cannot fire and handleKilling must close the execution
        FlowWithSource flow = Flows.of(logTask("t1"), logTask("t2"));
        harness.registerFlow(flow);
        Execution execution = killingExecution(flow);
        TaskRun succeeded = taskRun(
            flow, execution, "t1", null,
            stateOf(State.Type.CREATED, State.Type.RUNNING, State.Type.SUCCESS),
            List.of(attempt(State.Type.CREATED, State.Type.RUNNING, State.Type.SUCCESS))
        );
        execution = execution.withTaskRunList(List.of(succeeded));

        // When
        ExecutorContext context = harness.process(flow, execution);

        // Then: execution KILLED via handleKilling; t2 never got a taskrun
        assertThat(context)
            .executionInState(State.Type.KILLED)
            .updatedFrom("handleKilling")
            .transitioned(State.Type.KILLING, State.Type.KILLED)
            .hasNoWorkerTasks()
            .hasNoNexts();
        Assertions.assertThat(context.getExecution().getTaskRunList()).hasSize(1);
    }

    @Test
    void shouldStayKillingWhenATaskRunIsStillRunning() {
        // Given: a KILLING execution whose taskrun is still RUNNING on a worker
        FlowWithSource flow = Flows.of(logTask("t1"));
        harness.registerFlow(flow);
        Execution execution = killingExecution(flow);
        TaskRun running = taskRun(
            flow, execution, "t1", null,
            stateOf(State.Type.CREATED, State.Type.RUNNING),
            List.of(attempt(State.Type.CREATED, State.Type.RUNNING))
        );
        execution = execution.withTaskRunList(List.of(running));

        // When
        ExecutorContext context = harness.process(flow, execution);

        // Then: the executor waits for the worker — no KILLED transition, no new work, nothing changed
        assertThat(context)
            .executionInState(State.Type.KILLING)
            .hasTaskRunInState("t1", State.Type.RUNNING)
            .transitioned(State.Type.KILLING)
            .hasNoWorkerTasks()
            .hasNoNexts()
            .hasNoExecutionDelays();
        Assertions.assertThat(context.isExecutionUpdated()).isFalse();
        Assertions.assertThat(harness.kills()).isEmpty();
    }

    // --- childWorkerTaskResult KILLING branch: flowable parent killed with a terminal attempt

    @Test
    void shouldKillFlowableParentWithTerminalAttemptWhenStartedChildrenAreTerminal() {
        // Given: a KILLING Sequential parent with a live RUNNING attempt; its first child was
        // KILLED by the worker, the second child was never created — exactly the mid-flow kill
        // shape of AbstractRunnerTest#killedFlowableTaskRunShouldHaveTerminalAttempt
        FlowWithSource flow = Flows.of(
            Sequential.builder()
                .id("seq")
                .type(Sequential.class.getName())
                .tasks(List.of(logTask("c1"), logTask("c2")))
                .build()
        );
        harness.registerFlow(flow);
        Execution execution = killingExecution(flow);
        TaskRun seq = taskRun(
            flow, execution, "seq", null,
            stateOf(State.Type.CREATED, State.Type.RUNNING, State.Type.KILLING),
            List.of(attempt(State.Type.CREATED, State.Type.RUNNING))
        );
        TaskRun killedChild = taskRun(
            flow, execution, "c1", seq.getId(),
            stateOf(State.Type.CREATED, State.Type.RUNNING, State.Type.KILLED),
            List.of(attempt(State.Type.CREATED, State.Type.RUNNING, State.Type.KILLED))
        );
        execution = execution.withTaskRunList(List.of(seq, killedChild));

        // When: one production cycle, driven directly so the intermediate state is observable
        // (harness.process would keep cycling because KILLING executions keep processing)
        ExecutorContext cycle1 = harness.executorService().process(new ExecutorContext(execution, flow));

        // Then: the KILLING wait-branch of childWorkerTaskResult resolves the parent to KILLED via
        // withStateAndAttempt — the last attempt must be terminal too (the attempt-termination fix),
        // not just the taskrun state
        Assertions.assertThat(cycle1.getException()).isNull();
        assertThat(cycle1)
            .hasTaskRunInState("seq", State.Type.KILLED)
            .updatedFrom("addWorkerTaskResult")
            // the execution itself stays KILLING this cycle: handleKilling ran before the
            // flowable's KILLED result was merged
            .executionInState(State.Type.KILLING);
        TaskRun killedSeq = cycle1.getExecution().getTaskRunList().stream()
            .filter(t -> "seq".equals(t.getTaskId()))
            .findFirst()
            .orElseThrow();
        Assertions.assertThat(killedSeq.getAttempts()).isNotEmpty();
        Assertions.assertThat(killedSeq.getAttempts().getLast().getState().getCurrent()).isEqualTo(State.Type.KILLED);
        Assertions.assertThat(killedSeq.getAttempts().getLast().getState().isTerminated()).isTrue();
        Assertions.assertThat(killedSeq.getAttempts().getLast().getState().getEndDate()).isPresent();

        // When: the follow-up cycles run
        ExecutorContext cycle2 = harness.process(flow, cycle1.getExecution());

        // Then: with the only top-level task (seq) now terminal, handleEnd/onEnd computes the final
        // state — guessFinalState yields KILLED because a taskrun is KILLED
        assertThat(cycle2)
            .executionInState(State.Type.KILLED)
            .updatedFrom("onEnd");
    }

    // --- worker-reported KILLED result merging

    @Test
    void shouldKillExecutionWhenWorkerReportsKilledForRunningTaskRun() throws Exception {
        // Given: a two-task flow, KILLING, with t1 RUNNING on a worker (t2 never created)
        FlowWithSource flow = Flows.of(logTask("t1"), logTask("t2"));
        harness.registerFlow(flow);
        Execution execution = killingExecution(flow);
        TaskRun running = taskRun(
            flow, execution, "t1", null,
            stateOf(State.Type.CREATED, State.Type.RUNNING),
            List.of(attempt(State.Type.CREATED, State.Type.RUNNING))
        );
        execution = execution.withTaskRunList(List.of(running));

        // the worker answers the kill: same taskrun, one more state history and a terminal attempt
        WorkerTaskResult killedResult = new WorkerTaskResult(
            running
                .withAttempts(List.of(attempt(State.Type.CREATED, State.Type.RUNNING, State.Type.KILLED)))
                .withState(State.Type.KILLED)
        );

        // When: the result is merged (WorkerTaskResultMessageHandler semantics) then processed
        ExecutorContext context = harness.processResult(flow, new ExecutorContext(execution, flow), killedResult);

        // Then: the KILLED taskrun is merged and, all taskruns now terminal, handleKilling closes
        // the execution
        assertThat(context)
            .hasTaskRunInState("t1", State.Type.KILLED)
            .executionInState(State.Type.KILLED)
            .updatedFrom("handleKilling")
            .transitioned(State.Type.KILLING, State.Type.KILLED)
            .hasNoWorkerTasks()
            .hasNoNexts();
        Assertions.assertThat(context.getExecution().getTaskRunList()).hasSize(1);
        Assertions.assertThat(harness.kills()).isEmpty();
    }

    // --- fixtures (hand-rolled: taskruns in explicit states, following the Results pattern)

    /**
     * A KILLING execution with a legal CREATED → RUNNING → KILLING history and correct metadata
     * (newExecution avoids the Execution.builder() prebuild metadata trap).
     */
    private static Execution killingExecution(FlowWithSource flow) {
        return Execution.newExecution(flow, List.of())
            .withState(State.Type.RUNNING)
            .withState(State.Type.KILLING);
    }

    private static TaskRun taskRun(
        FlowWithSource flow,
        Execution execution,
        String taskId,
        String parentTaskRunId,
        State state,
        List<TaskRunAttempt> attempts) {
        return TaskRun.builder()
            .tenantId(flow.getTenantId())
            .id(IdUtils.create())
            .executionId(execution.getId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .taskId(taskId)
            .parentTaskRunId(parentTaskRunId)
            .state(state)
            .attempts(attempts)
            .build();
    }

    /** A state whose history walks the given types one second apart, anchored on a fixed instant. */
    private static State stateOf(State.Type... types) {
        List<State.History> histories = new ArrayList<>(types.length);
        for (int i = 0; i < types.length; i++) {
            histories.add(new State.History(types[i], BASE.plusSeconds(i)));
        }
        return new State(types[types.length - 1], histories);
    }

    private static TaskRunAttempt attempt(State.Type... types) {
        return TaskRunAttempt.builder()
            .state(stateOf(types))
            .build();
    }

    private static Log logTask(String id) {
        return Log.builder().id(id).type(Log.class.getName()).message("hello").build();
    }
}
