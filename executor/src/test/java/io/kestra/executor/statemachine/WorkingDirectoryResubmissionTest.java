package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.plugin.core.flow.WorkingDirectory;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A WorkingDirectory whose worker died is resubmitted and runs all its subtasks again: each subtask keeps its task
 * run and gets a new attempt, and the subtasks the new run did not reach must not keep the WorkingDirectory running.
 */
class WorkingDirectoryResubmissionTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final AtomicReference<WorkerTask> dispatched = new AtomicReference<>();
    private final FlowWithSource flow = Flows.yaml("""
        id: working-directory-resubmission
        namespace: io.kestra.tests
        concurrency:
          limit: 1
        tasks:
          - id: working-directory
            type: io.kestra.plugin.core.flow.WorkingDirectory
            tasks:
              - id: checkout
                type: io.kestra.plugin.core.log.Log
                message: checkout
              - id: build
                type: io.kestra.plugin.core.log.Log
                message: build
        """);

    @Test
    void shouldAddAnAttemptToEachSubtaskWhenTheResubmittedRunSucceeds() {
        // Given
        Execution created = startAndLoseWorkerDuringBuild();

        // When: the resubmitted WorkingDirectory runs both subtasks again
        WorkerTask resubmitted = resubmitted();
        harness.run(List.of(subtaskResult(resubmitted, "checkout", State.Type.SUCCESS), subtaskResult(resubmitted, "build", State.Type.SUCCESS)), ScriptedWorker.succeeding(T0));

        // Then
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS).hasRunning(flow, 0);
        assertThat(subtasks(created)).hasSize(2).allSatisfy(subtask ->
        {
            assertThat(subtask.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
            assertThat(subtask.getAttempts()).extracting(attempt -> attempt.getState().getCurrent()).containsExactly(State.Type.RESUBMITTED, State.Type.SUCCESS);
        });
    }

    @Test
    void shouldFailWhenTheResubmittedRunFailsBeforeReachingTheLostSubtask() {
        // Given
        Execution created = startAndLoseWorkerDuringBuild();

        // When: the resubmitted WorkingDirectory fails on its first subtask, so the lost subtask is never run again
        harness.run(List.of(subtaskResult(resubmitted(), "checkout", State.Type.FAILED)), ScriptedWorker.succeeding(T0));

        // Then
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED).hasRunning(flow, 0);
    }

    private Execution startAndLoseWorkerDuringBuild() {
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        harness.run(created, task ->
        {
            dispatched.set(task);
            return subtaskResult(task, "checkout", State.Type.SUCCESS);
        });
        harness.run(List.of(subtaskResult(dispatched.get(), "build", State.Type.RUNNING)), ScriptedWorker.succeeding(T0));
        assertThat(harness).hasExecutionInState(created, State.Type.RUNNING);
        return created;
    }

    private WorkerTask resubmitted() {
        return dispatched.get().withTaskRun(dispatched.get().getTaskRun().onRunningResend());
    }

    private WorkerTaskResult subtaskResult(WorkerTask workingDirectory, String taskId, State.Type state) {
        WorkingDirectory task = (WorkingDirectory) workingDirectory.getTask();
        RunContext runContext = mock(RunContext.class);
        when(runContext.getVariables()).thenReturn(Map.of());
        TaskRun subtask = task.workerTask(workingDirectory.getTaskRun(), task.getTasks().stream().filter(t -> t.getId().equals(taskId)).findFirst().orElseThrow(), runContext).getTaskRun();
        return new WorkerTaskResult(subtask.addAttempt(TaskRunAttempt.builder().state(new State().withState(state)).build()).withState(state));
    }

    private List<TaskRun> subtasks(Execution execution) {
        return harness.executionStateStore().findByIdWithoutAcl(execution.getId()).getTaskRunList().stream()
            .filter(taskRun -> dispatched.get().getTaskRun().getId().equals(taskRun.getParentTaskRunId()))
            .toList();
    }
}
