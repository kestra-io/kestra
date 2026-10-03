package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.ScriptedWorker;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A WorkingDirectory whose worker died is resubmitted and runs all its children again, under new task runs,
 * on another worker: the children the dead worker left behind must not keep the WorkingDirectory running.
 */
class WorkingDirectoryResubmissionTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final String DEAD_WORKER = "dead-worker";
    private static final String LIVE_WORKER = "live-worker";

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final AtomicReference<WorkerTask> workingDirectory = new AtomicReference<>();
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
    void shouldEndWhenTheResubmittedRunSucceeds() {
        // Given
        Execution created = startAndLoseWorkerDuringBuild();

        // When: the resubmitted WorkingDirectory runs both children again on another worker
        harness.run(
            List.of(
                childResult("checkout", State.Type.SUCCESS, LIVE_WORKER),
                childResult("build", State.Type.SUCCESS, LIVE_WORKER)
            ),
            ScriptedWorker.succeeding(T0)
        );

        // Then
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS).hasRunning(flow, 0);
        assertThat(children(created)).allMatch(child -> child.getState().isTerminated());
    }

    @Test
    void shouldEndWhenTheResubmittedRunLandsOnTheSameWorker() {
        // Given
        Execution created = startAndLoseWorkerDuringBuild();

        // When: the WorkingDirectory runs again, on a worker reporting the same id as the previous run
        harness.run(
            List.of(
                childResult("checkout", State.Type.SUCCESS, DEAD_WORKER),
                childResult("build", State.Type.SUCCESS, DEAD_WORKER)
            ),
            ScriptedWorker.succeeding(T0)
        );

        // Then
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS).hasRunning(flow, 0);
    }

    @Test
    void shouldFailWhenTheResubmittedRunFailsBeforeReachingTheLostChild() {
        // Given
        Execution created = startAndLoseWorkerDuringBuild();

        // When: the resubmitted WorkingDirectory fails on its first child, so the lost child is never run again
        harness.run(List.of(childResult("checkout", State.Type.FAILED, LIVE_WORKER)), ScriptedWorker.succeeding(T0));

        // Then
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED).hasRunning(flow, 0);
    }

    private Execution startAndLoseWorkerDuringBuild() {
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        harness.run(created, task ->
        {
            workingDirectory.set(task);
            return childResult("checkout", State.Type.SUCCESS, DEAD_WORKER);
        });
        harness.run(List.of(childResult("build", State.Type.RUNNING, DEAD_WORKER)), ScriptedWorker.succeeding(T0));
        assertThat(harness).hasExecutionInState(created, State.Type.RUNNING);
        return created;
    }

    private WorkerTaskResult childResult(String taskId, State.Type state, String workerId) {
        TaskRun child = workingDirectory.get().getTaskRun().toBuilder()
            .id(IdUtils.create())
            .taskId(taskId)
            .parentTaskRunId(workingDirectory.get().getTaskRun().getId())
            .attempts(List.of(TaskRunAttempt.builder().workerId(workerId).state(new State().withState(state)).build()))
            .state(new State().withState(state))
            .build();
        return new WorkerTaskResult(child);
    }

    private List<TaskRun> children(Execution execution) {
        return harness.executionStateStore().findByIdWithoutAcl(execution.getId()).getTaskRunList().stream()
            .filter(taskRun -> workingDirectory.get().getTaskRun().getId().equals(taskRun.getParentTaskRunId()))
            .toList();
    }
}
