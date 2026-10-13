package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A WorkingDirectory whose worker died is resubmitted with the task runs of its subtasks, and runs all its subtasks
 * again: each subtask keeps its task run and gets a new attempt, and the subtasks the new run did not reach must not
 * keep the WorkingDirectory running.
 */
class WorkingDirectoryResubmissionTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant AFTER_ANY_RETRY_DELAY = Instant.parse("2100-01-01T00:00:00Z");
    private static final String LOST_WORKER = "lost-worker";
    private static final String NEW_WORKER = "new-worker";

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
    private final FlowWithSource retriedFlow = Flows.yaml("""
        id: working-directory-retried-resubmission
        namespace: io.kestra.tests
        concurrency:
          limit: 1
        retry:
          type: constant
          interval: PT1S
          maxAttempts: 2
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
    void shouldAddAnAttemptToTheTaskRunOfEachSubtaskWhenTheResubmittedRunSucceeds() {
        // Given
        Execution created = startAndLoseWorkerDuringBuild();
        List<String> lostRunIds = subtasks(created).stream().map(TaskRun::getId).toList();

        // When: the resubmitted WorkingDirectory runs both subtasks again
        WorkerTask resubmitted = resubmitted();
        harness.run(List.of(subtaskResult(resubmitted, "checkout", State.Type.SUCCESS), subtaskResult(resubmitted, "build", State.Type.SUCCESS)), ScriptedWorker.succeeding(T0));

        // Then: each subtask kept its task run, and the attempt of the lost run is kept with its worker
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS).hasRunning(flow, 0);
        assertThat(subtasks(created)).extracting(TaskRun::getId).containsExactlyElementsOf(lostRunIds);
        assertThat(subtask(created, "checkout").getAttempts())
            .extracting(attempt -> attempt.getState().getCurrent(), TaskRunAttempt::getWorkerId)
            .containsExactly(tuple(State.Type.SUCCESS, LOST_WORKER), tuple(State.Type.SUCCESS, NEW_WORKER));
        assertThat(subtask(created, "build").getAttempts())
            .extracting(attempt -> attempt.getState().getCurrent(), TaskRunAttempt::getWorkerId)
            .containsExactly(tuple(State.Type.RESUBMITTED, LOST_WORKER), tuple(State.Type.SUCCESS, NEW_WORKER));
        assertThat(subtasks(created)).allSatisfy(subtask -> assertThat(subtask.getState().getCurrent()).isEqualTo(State.Type.SUCCESS));
    }

    @Test
    void shouldFailWhenTheResubmittedRunFailsBeforeReachingTheLostSubtask() {
        // Given
        Execution created = startAndLoseWorkerDuringBuild();

        // When: the resubmitted WorkingDirectory fails on its first subtask, so the lost subtask is never run again
        WorkerTask resubmitted = resubmitted();
        harness.run(ranUntilFailure(resubmitted, subtaskResult(resubmitted, "checkout", State.Type.FAILED)), ScriptedWorker.succeeding(T0));

        // Then
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED).hasRunning(flow, 0);
        assertThat(subtask(created, "build").getState().getCurrent()).isEqualTo(State.Type.RESUBMITTED);
    }

    @Test
    void shouldSucceedWhenARetriedRunIsResubmitted() {
        // Given
        Execution created = retryThenLoseWorkerDuringBuild();

        // When: the second run is resubmitted and runs both subtasks again
        WorkerTask resubmitted = resubmitted();
        harness.run(List.of(subtaskResult(resubmitted, "checkout", State.Type.SUCCESS), subtaskResult(resubmitted, "build", State.Type.SUCCESS)), ScriptedWorker.succeeding(T0));

        // Then: the retry ran the subtasks again under new task runs, and the resubmitted run added an attempt to them
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS).hasRunning(retriedFlow, 0);
        assertThat(subtasks(created)).hasSize(2).allSatisfy(subtask -> assertThat(subtask.getState().getCurrent()).isEqualTo(State.Type.SUCCESS));
        assertThat(subtask(created, "checkout").getAttempts())
            .extracting(attempt -> attempt.getState().getCurrent())
            .containsExactly(State.Type.SUCCESS, State.Type.SUCCESS);
        assertThat(subtask(created, "build").getAttempts())
            .extracting(attempt -> attempt.getState().getCurrent())
            .containsExactly(State.Type.RESUBMITTED, State.Type.SUCCESS);
    }

    @Test
    void shouldFailWhenARetriedRunIsResubmittedAndFailsBeforeReachingTheLostSubtask() {
        // Given
        Execution created = retryThenLoseWorkerDuringBuild();

        // When: the resubmitted run fails on its first subtask, so the lost subtask is never run again
        WorkerTask resubmitted = resubmitted();
        harness.run(ranUntilFailure(resubmitted, subtaskResult(resubmitted, "checkout", State.Type.FAILED)), ScriptedWorker.succeeding(T0));

        // Then: the flow retry is exhausted, so the execution ends instead of waiting for the lost subtask
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED).hasRunning(retriedFlow, 0);
    }

    // the first run fails on its first subtask and is retried, and the worker of the second run dies during build
    private Execution retryThenLoseWorkerDuringBuild() {
        harness.registerFlow(retriedFlow);
        Execution created = Executions.created(retriedFlow);
        harness.run(created, task ->
        {
            dispatched.set(task);
            return subtaskResult(task, "checkout", State.Type.FAILED);
        });
        harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
        harness.run(List.of(), task ->
        {
            dispatched.set(task);
            return subtaskResult(task, "checkout", State.Type.SUCCESS);
        });
        harness.run(List.of(subtaskResult(dispatched.get(), "build", State.Type.RUNNING)), ScriptedWorker.succeeding(T0));
        assertThat(harness).hasExecutionInState(created, State.Type.RUNNING);
        return created;
    }

    private Execution startAndLoseWorkerDuringBuild() {
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        harness.run(created, task ->
        {
            dispatched.set(task);
            return subtaskResult(task, "checkout", State.Type.SUCCESS, LOST_WORKER);
        });
        harness.run(List.of(subtaskResult(dispatched.get(), "build", State.Type.RUNNING, LOST_WORKER)), ScriptedWorker.succeeding(T0));
        assertThat(harness).hasExecutionInState(created, State.Type.RUNNING);
        return created;
    }

    // what the liveness coordinator sends again when the worker running the WorkingDirectory is lost
    private WorkerTask resubmitted() {
        Execution execution = harness.executionStateStore().findByIdWithoutAcl(dispatched.get().getTaskRun().getExecutionId());
        TaskRun workingDirectory = dispatched.get().getTaskRun();
        return dispatched.get()
            .withTaskRun(workingDirectory.onRunningResend())
            .withSubtaskRuns(WorkingDirectory.subtaskRunsToResume(execution, workingDirectory));
    }

    // what the worker reports when the WorkingDirectory stops on a failed subtask
    private List<WorkerTaskResult> ranUntilFailure(WorkerTask workingDirectory, WorkerTaskResult failed) {
        List<WorkerTaskResult> results = new ArrayList<>(List.of(failed));
        ((WorkingDirectory) workingDirectory.getTask()).unreachedSubtaskRuns(workingDirectory, Set.of(failed.getTaskRun().getTaskId()))
            .forEach(taskRun -> results.add(new WorkerTaskResult(taskRun)));
        return results;
    }

    private WorkerTaskResult subtaskResult(WorkerTask workingDirectory, String taskId, State.Type state) {
        return subtaskResult(workingDirectory, taskId, state, NEW_WORKER);
    }

    private WorkerTaskResult subtaskResult(WorkerTask workingDirectory, String taskId, State.Type state, String workerId) {
        WorkingDirectory task = (WorkingDirectory) workingDirectory.getTask();
        RunContext runContext = mock(RunContext.class);
        when(runContext.getVariables()).thenReturn(Map.of());
        TaskRun subtask = task.workerTask(workingDirectory, task.getTasks().stream().filter(t -> t.getId().equals(taskId)).findFirst().orElseThrow(), runContext).getTaskRun();
        return new WorkerTaskResult(subtask.addAttempt(TaskRunAttempt.builder().workerId(workerId).state(new State().withState(state)).build()).withState(state));
    }

    private TaskRun subtask(Execution execution, String taskId) {
        return subtasks(execution).stream().filter(taskRun -> taskRun.getTaskId().equals(taskId)).findFirst().orElseThrow();
    }

    private List<TaskRun> subtasks(Execution execution) {
        return harness.executionStateStore().findByIdWithoutAcl(execution.getId()).getTaskRunList().stream()
            .filter(taskRun -> dispatched.get().getTaskRun().getId().equals(taskRun.getParentTaskRunId()))
            .toList();
    }
}
