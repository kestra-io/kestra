package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.executor.command.RestartWorkingDirectory;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.WorkerQueueFallback;
import io.kestra.core.models.tasks.WorkerSelector;
import io.kestra.core.runners.ExecutableUtils;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerQueueRouting;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.plugin.core.flow.Parallel;
import io.kestra.plugin.core.flow.Sequential;
import io.kestra.plugin.core.flow.Subflow;
import io.kestra.plugin.core.flow.WorkingDirectory;
import io.kestra.plugin.core.flow.WorkingDirectoryOpen;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import reactor.core.publisher.Flux;

class WorkingDirectoryExecutorTest {

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
            return ranBy(task, "worker-1");
        });

        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        Assertions.assertThat(dispatched)
            .extracting(task -> task.getTaskRun().getTaskId())
            .containsExactly("working-directory_open", "child", "working-directory_close");
        Assertions.assertThat(dispatched).extracting(WorkerTask::getWorkingDirectoryId).containsOnly(dispatched.getFirst().getTaskRun().getParentTaskRunId());
        Assertions.assertThat(harness.workerJobEventQueue().emittedMessages())
            .extracting(event -> event.stickyRouting() == null ? null : event.stickyRouting().workerId())
            .containsExactly(null, "worker-1", "worker-1");
    }

    @Test
    void shouldRunTheTasksOfAFlowableInsideAWorkingDirectoryOnItsWorker() {
        FlowWithSource flow = Flows.of(
            WorkingDirectory.builder()
                .id("working-directory")
                .type(WorkingDirectory.class.getName())
                .tasks(List.of(
                    Sequential.builder()
                        .id("sequential")
                        .type(Sequential.class.getName())
                        .tasks(List.of(Flows.log("grandchild")))
                        .build()
                ))
                .build()
        );
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        List<WorkerTask> dispatched = new ArrayList<>();

        harness.run(created, task ->
        {
            dispatched.add(task);
            return ranBy(task, "worker-1");
        });

        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        Assertions.assertThat(dispatched)
            .extracting(task -> task.getTaskRun().getTaskId())
            .containsExactly("working-directory_open", "grandchild", "working-directory_close");
        Assertions.assertThat(dispatched).extracting(WorkerTask::getWorkingDirectoryId).containsOnly(dispatched.getFirst().getTaskRun().getParentTaskRunId());
        Assertions.assertThat(harness.workerJobEventQueue().emittedMessages())
            .extracting(event -> event.stickyRouting() == null ? null : event.stickyRouting().workerId())
            .containsExactly(null, "worker-1", "worker-1");
    }

    @Test
    void shouldRunAllItsTasksAgainWhenTheWorkerOfTheWorkingDirectoryIsLost() {
        // Given: worker-1 opens the WorkingDirectory and is lost while running its child
        FlowWithSource flow = Flows.of(workingDirectory());
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        AtomicReference<WorkerTask> runningChild = new AtomicReference<>();
        harness.run(created, task ->
        {
            if (task.getTask() instanceof WorkingDirectoryOpen) {
                return ranBy(task, "worker-1");
            }
            runningChild.set(task);
            return new WorkerTaskResult(task.getTaskRun().withState(State.Type.RUNNING));
        });

        // When: the loss of worker-1 is reported for the child, twice as both the controller and the liveness coordinator see it
        WorkerTask child = runningChild.get();
        List<WorkerTask> dispatched = new ArrayList<>();
        harness.run(List.of(
            RestartWorkingDirectory.from(child.getTaskRun(), child.getWorkingDirectoryId()),
            RestartWorkingDirectory.from(child.getTaskRun(), child.getWorkingDirectoryId())
        ), task ->
        {
            dispatched.add(task);
            return ranBy(task, "worker-2");
        });

        // Then
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        Assertions.assertThat(dispatched)
            .extracting(task -> task.getTaskRun().getTaskId())
            .containsExactly("working-directory_open", "child", "working-directory_close");
        Assertions.assertThat(dispatched.get(1).getTaskRun().getId()).isNotEqualTo(runningChild.get().getTaskRun().getId());
    }

    @Test
    void shouldKillTheSubflowStillRunningWhenTheWorkingDirectoryRunsAgain() {
        // Given: a subflow still runs next to the child when worker-1 is lost
        FlowWithSource subflow = Flows.of(Flows.log("in-subflow"));
        FlowWithSource flow = Flows.of(
            WorkingDirectory.builder()
                .id("working-directory")
                .type(WorkingDirectory.class.getName())
                .tasks(List.of(
                    Parallel.builder()
                        .id("parallel")
                        .type(Parallel.class.getName())
                        .tasks(List.of(
                            Subflow.builder().id("subflow").type(Subflow.class.getName()).namespace(subflow.getNamespace()).flowId(subflow.getId()).build(),
                            Flows.log("child")
                        ))
                        .build()
                ))
                .build()
        );
        harness.registerFlow(subflow).registerFlow(flow);
        Execution created = Executions.created(flow);
        AtomicReference<WorkerTask> runningChild = new AtomicReference<>();
        harness.run(created, task ->
        {
            if (task.getTask() instanceof WorkingDirectoryOpen) {
                return ranBy(task, "worker-1");
            }
            if (task.getTaskRun().getTaskId().equals("child")) {
                runningChild.set(task);
            }
            return new WorkerTaskResult(task.getTaskRun().withState(State.Type.RUNNING));
        });
        Execution subflowExecution = harness.executionQueue().emitted().stream()
            .filter(ExecutableUtils::isSubflow)
            .findFirst()
            .orElseThrow();
        when(harness.executionRepository().findAllByTriggerExecutionId(any(), any())).thenReturn(Flux.just(subflowExecution));
        doReturn(Flux.empty()).when(harness.executionService()).killSubflowExecutions(any(), any());
        doReturn(List.of()).when(harness.executionService()).killLoopSubExecutions(any(), any());

        // When
        WorkerTask child = runningChild.get();
        harness.run(List.of(RestartWorkingDirectory.from(child.getTaskRun(), child.getWorkingDirectoryId())), task -> ranBy(task, "worker-2"));

        // Then
        Assertions.assertThat(harness.kills())
            .filteredOn(kill -> kill.getState() == ExecutionKilled.State.REQUESTED)
            .extracting(kill -> ((ExecutionKilledExecution) kill).getExecutionId())
            .containsExactly(subflowExecution.getId());
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
        harness.run(created, task -> ranBy(task, "worker-1"));

        // Then
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        Assertions.assertThat(harness.workerJobEventQueue().emittedMessages())
            .extracting(WorkerJobEvent::workerQueueId)
            .hasSize(3)
            .containsOnly("gpu");
    }

    private static WorkerTaskResult ranBy(WorkerTask task, String workerId) {
        WorkerTaskResult success = Results.success(task, T0);
        List<TaskRunAttempt> attempts = success.getTaskRun().getAttempts().stream()
            .map(attempt -> TaskRunAttempt.builder().state(attempt.getState()).workerId(workerId).build())
            .toList();
        return new WorkerTaskResult(success.getTaskRun().withAttempts(attempts));
    }

    private static WorkingDirectory workingDirectory() {
        return WorkingDirectory.builder()
            .id("working-directory")
            .type(WorkingDirectory.class.getName())
            .tasks(List.of(Flows.log("child")))
            .build();
    }
}
