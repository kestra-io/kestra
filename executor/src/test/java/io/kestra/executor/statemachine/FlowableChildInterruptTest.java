package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilledTaskRuns;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.executor.testkit.ScriptedWorker;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a flowable does to the rest of its children when one of them stops: a killed child kills its flowable parents,
 * and a failed child of a Parallel interrupts its running siblings according to {@code onChildFailure}.
 */
class FlowableChildInterruptTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final List<String> dispatched = new ArrayList<>();

    @Test
    void shouldKillFlowableParentsAndSkipTheirNextTasksWhenAChildIsKilled() {
        // Given
        FlowWithSource flow = Flows.yaml("""
            id: killed-child
            namespace: io.kestra.tests
            tasks:
              - id: outer
                type: io.kestra.plugin.core.flow.Sequential
                tasks:
                  - id: inner
                    type: io.kestra.plugin.core.flow.Sequential
                    tasks:
                      - id: a
                        type: io.kestra.plugin.core.log.Log
                        message: a
                      - id: b
                        type: io.kestra.plugin.core.log.Log
                        message: b
            """);
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);

        // When: the worker reports the first child KILLED
        harness.run(created, recording(task -> "a".equals(task.getTaskRun().getTaskId()) ? Results.killed(task, T0) : Results.success(task, T0)));

        // Then
        assertThat(dispatched).as("the next task of the killed flowable never runs").containsExactly("a");
        assertThat(taskRunState(created, "inner")).isEqualTo(State.Type.KILLED);
        assertThat(taskRunState(created, "outer")).isEqualTo(State.Type.KILLED);
        assertThat(harness).hasExecutionInState(created, State.Type.KILLED);
    }

    @Test
    void shouldFailRunningSiblingsWhenAParallelChildFailsByDefault() {
        // Given
        Execution created = runParallelWhereAFailsWhileTheOthersRun("");

        // Then
        ExecutionKilledTaskRuns interrupt = singleInterrupt();
        assertThat(interrupt.getExecutionId()).isEqualTo(created.getId());
        assertThat(interrupt.getTaskRunIds()).as("every running leaf, nested ones included").containsExactlyInAnyOrder(taskRun(created, "b").getId(), taskRun(created, "c").getId());
        assertThat(interrupt.getTaskRunState()).as("onChildFailure defaults to FAIL").isEqualTo(State.Type.FAILED);
        assertThat(taskRunState(created, "seq")).isEqualTo(State.Type.FAILED);
    }

    @Test
    void shouldNameTheFailedSiblingWhenLoggingAnInterruptedSibling() {
        // Given
        Execution created = runParallelWhereAFailsWhileTheOthersRun("");

        // Then
        assertThat(harness.logs())
            .filteredOn(log -> taskRun(created, "b").getId().equals(log.getTaskRunId()) && log.getLevel() == Level.WARN)
            .extracting(LogEntry::getMessage)
            .containsExactly("Task terminated because sibling task 'a' failed");
    }

    @Test
    void shouldCancelRunningSiblingsWhenAParallelChildFailsWithOnChildFailureCancel() {
        // Given
        Execution created = runParallelWhereAFailsWhileTheOthersRun("onChildFailure: CANCEL");

        // Then
        ExecutionKilledTaskRuns interrupt = singleInterrupt();
        assertThat(interrupt.getTaskRunIds()).containsExactlyInAnyOrder(taskRun(created, "b").getId(), taskRun(created, "c").getId());
        assertThat(interrupt.getTaskRunState()).isEqualTo(State.Type.CANCELLED);
        assertThat(taskRunState(created, "seq")).as("a flowable has no worker job to interrupt: it is resolved directly").isEqualTo(State.Type.CANCELLED);
    }

    @Test
    void shouldLetSiblingsRunWhenAParallelChildFailsWithOnChildFailureContinue() {
        // Given
        Execution created = runParallelWhereAFailsWhileTheOthersRun("onChildFailure: CONTINUE");

        // Then
        assertThat(harness.kills()).as("no sibling is interrupted").isEmpty();
        assertThat(taskRunState(created, "b")).isEqualTo(State.Type.RUNNING);
    }

    /** a fails while b, and c inside the sequential seq, are still running on a worker. */
    private Execution runParallelWhereAFailsWhileTheOthersRun(String onChildFailure) {
        FlowWithSource flow = Flows.yaml("""
            id: parallel-child-failure
            namespace: io.kestra.tests
            tasks:
              - id: parallel
                type: io.kestra.plugin.core.flow.Parallel
            %s
                tasks:
                  - id: a
                    type: io.kestra.plugin.core.log.Log
                    message: a
                  - id: b
                    type: io.kestra.plugin.core.log.Log
                    message: b
                  - id: seq
                    type: io.kestra.plugin.core.flow.Sequential
                    tasks:
                      - id: c
                        type: io.kestra.plugin.core.log.Log
                        message: c
            """.formatted(onChildFailure.isEmpty() ? "" : "    " + onChildFailure));
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        harness.run(
            created, recording(
                task -> "a".equals(task.getTaskRun().getTaskId())
                    ? Results.failed(task, T0)
                    : new WorkerTaskResult(task.getTaskRun().withState(State.Type.RUNNING))
            )
        );
        assertThat(dispatched).as("given: every leaf reached a worker").containsExactlyInAnyOrder("a", "b", "c");
        return created;
    }

    private ExecutionKilledTaskRuns singleInterrupt() {
        assertThat(harness.kills()).hasSize(1).first().isInstanceOf(ExecutionKilledTaskRuns.class);
        return (ExecutionKilledTaskRuns) harness.kills().getFirst();
    }

    private ScriptedWorker recording(ScriptedWorker worker) {
        return task ->
        {
            dispatched.add(task.getTaskRun().getTaskId());
            return worker.run(task);
        };
    }

    private TaskRun taskRun(Execution execution, String taskId) {
        return harness.executionStateStore().findByIdWithoutAcl(execution.getId()).findTaskRunsByTaskId(taskId).getFirst();
    }

    private State.Type taskRunState(Execution execution, String taskId) {
        return taskRun(execution, taskId).getState().getCurrent();
    }
}
