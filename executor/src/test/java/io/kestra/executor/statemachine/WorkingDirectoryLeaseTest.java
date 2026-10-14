package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.WorkerQueueFallback;
import io.kestra.core.models.tasks.WorkerSelector;
import io.kestra.core.runners.NoTransactionContext;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerQueueRouting;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.ListUtils;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.plugin.core.flow.WorkingDirectory;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A WorkingDirectory holds its worker job lease from the job that opens its folder to the one that closes it, so the
 * executor has to release that lease whatever path ended it without closing it.
 */
class WorkingDirectoryLeaseTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    @Test
    void shouldOpenRunAndCloseTheWorkingDirectoryOnTheSameWorker() {
        FlowWithSource flow = Flows.of(workingDirectory());
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        List<WorkerTask> dispatched = new ArrayList<>();

        harness.run(created, task ->
        {
            dispatched.add(task);
            return workingDirectoryWorker(task);
        });

        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        Assertions.assertThat(dispatched).extracting(task -> task.getTaskRun().getTaskId()).containsExactly("working-directory", "child", "working-directory");
        Assertions.assertThat(dispatched.get(1).getWorkingDirectoryId()).isEqualTo(dispatched.getFirst().getTaskRun().getId());
    }

    @Test
    void shouldRunAllItsTasksAgainWhenAnotherWorkerOpensTheWorkingDirectory() {
        // Given: worker-1 opens the WorkingDirectory and dies while running its child
        FlowWithSource flow = Flows.of(workingDirectory());
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        AtomicReference<WorkerTask> openJob = new AtomicReference<>();
        List<String> childTaskRunIds = new ArrayList<>();
        harness.run(created, task ->
        {
            if (task.getTask() instanceof WorkingDirectory) {
                openJob.set(task);
                return openedBy(task, "worker-1");
            }
            childTaskRunIds.add(task.getTaskRun().getId());
            return new WorkerTaskResult(task.getTaskRun().withState(State.Type.RUNNING));
        });

        // When: the orphan sweep resends the open job and worker-2 opens the WorkingDirectory
        WorkerTask resent = openJob.get().withTaskRun(openJob.get().getTaskRun().onRunningResend());
        harness.run(List.of(openedBy(resent, "worker-2")), task ->
        {
            if (!(task.getTask() instanceof WorkingDirectory)) {
                childTaskRunIds.add(task.getTaskRun().getId());
            }
            return workingDirectoryWorker(task);
        });

        // Then
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        Assertions.assertThat(childTaskRunIds).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    void shouldRouteTheTasksOfAWorkingDirectoryToItsWorkerQueue() throws Exception {
        // Given: the WorkingDirectory selects the 'gpu' queue, while the flow default queue has no worker
        doAnswer(invocation -> {
            WorkerTask job = invocation.getArgument(1);
            return Optional.of(job.getTask().getWorkerSelector() != null
                ? new WorkerQueueRouting("gpu", List.of("gpu"), WorkerQueueFallback.FAIL, WorkerQueueRouting.Disposition.DISPATCH)
                : new WorkerQueueRouting("flow-default", List.of("cpu"), WorkerQueueFallback.FAIL, WorkerQueueRouting.Disposition.FAIL));
        }).when(harness.workerQueueService()).resolveWorkerQueueForJob(any(), any());
        FlowWithSource flow = Flows.of(
            WorkingDirectory.builder()
                .id("working-directory")
                .type(WorkingDirectory.class.getName())
                .workerSelector(new WorkerSelector(List.of("gpu"), WorkerQueueFallback.FAIL))
                .tasks(List.of(Flows.log("child")))
                .build()
        );
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);

        // When
        harness.run(created, WorkingDirectoryLeaseTest::workingDirectoryWorker);

        // Then
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        Assertions.assertThat(harness.workerJobEventQueue().emittedMessages())
            .extracting(WorkerJobEvent::workerQueueId)
            .hasSize(3)
            .containsOnly("gpu");
    }

    @Test
    void shouldReleaseWorkingDirectoryLeaseWhenItsExecutionIsKilled() {
        // Given: the WorkingDirectory is opened by a worker, then its execution is killed
        FlowWithSource flow = Flows.of(workingDirectory());
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        AtomicReference<String> workingDirectoryTaskRunId = new AtomicReference<>();
        AtomicReference<WorkerTask> runningChild = new AtomicReference<>();
        harness.run(created, task ->
        {
            if (task.getTask() instanceof WorkingDirectory) {
                workingDirectoryTaskRunId.set(task.getTaskRun().getId());
                return workingDirectoryWorker(task);
            }
            runningChild.set(task);
            return new WorkerTaskResult(task.getTaskRun().withState(State.Type.RUNNING));
        });

        // When
        harness.step(
            ExecutionKilledExecution.builder()
                .tenantId(created.getTenantId())
                .executionId(created.getId())
                .state(ExecutionKilled.State.REQUESTED)
                .isOnKillCascade(false)
                .build()
        );
        harness.run(List.of(Results.killed(runningChild.get(), T0)), ScriptedWorker.succeeding(T0));

        // Then
        assertThat(harness).hasExecutionInState(created, State.Type.KILLED);
        verify(harness.workerJobRunningStateStore()).deleteByKey(NoTransactionContext.INSTANCE, workingDirectoryTaskRunId.get());
    }

    @Test
    void shouldNotReleaseAnyLeaseWhenTheFlowHasNoWorkingDirectory() {
        // Given
        FlowWithSource flow = Flows.of(Flows.log("log"));
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);

        // When
        harness.run(created, ScriptedWorker.succeeding(T0));

        // Then
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        verify(harness.workerJobRunningStateStore(), never()).deleteByKey(any(), any());
    }

    /**
     * Opens the folder of a WorkingDirectory, closes it with the state the executor resolved, and runs any other task.
     */
    private static WorkerTaskResult workingDirectoryWorker(WorkerTask task) {
        if (!(task.getTask() instanceof WorkingDirectory)) {
            return Results.success(task, T0);
        }
        if (task.getTaskRun().getState().isTerminated()) {
            return new WorkerTaskResult(task.getTaskRun());
        }
        return openedBy(task, "worker-1");
    }

    private static WorkerTaskResult openedBy(WorkerTask workingDirectory, String workerId) {
        List<TaskRunAttempt> attempts = new ArrayList<>(ListUtils.emptyOnNull(workingDirectory.getTaskRun().getAttempts()));
        attempts.add(TaskRunAttempt.builder().workerId(workerId).state(new State().withState(State.Type.RUNNING)).build());
        return new WorkerTaskResult(workingDirectory.getTaskRun().withAttempts(attempts).withState(State.Type.RUNNING));
    }

    private static WorkingDirectory workingDirectory() {
        return WorkingDirectory.builder()
            .id("working-directory")
            .type(WorkingDirectory.class.getName())
            .tasks(List.of(Flows.log("child")))
            .build();
    }
}
