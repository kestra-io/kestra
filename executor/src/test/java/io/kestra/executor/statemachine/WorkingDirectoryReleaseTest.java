package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.plugin.core.flow.WorkingDirectory;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A {@link WorkingDirectory} is the only flowable dispatched to a worker, so its
 * {@code worker_job_running} entry can only be released by the executor — its terminal state is
 * decided there (resolved from its children, or forced to match a terminated parent), never
 * reported by the worker. If it is not released, a terminal execution keeps a live entry that gets
 * resubmitted once the holding worker dies.
 */
class WorkingDirectoryReleaseTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final String NAMESPACE = "io.kestra.tests";

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    @Test
    void releasesTheRunningEntryWhenTheWorkingDirectorySucceeds() {
        FlowWithSource flow = Flows.yaml("""
            id: wd
            namespace: io.kestra.tests
            tasks:
              - id: wd
                type: io.kestra.plugin.core.flow.WorkingDirectory
                tasks:
                  - id: child
                    type: io.kestra.plugin.core.log.Log
                    message: hello
            """);
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);

        // The worker runs a WorkingDirectory's children and reports each one; the WorkingDirectory
        // itself is never reported. Answer its dispatch with the child's terminal result.
        String[] workingDirectoryTaskRunId = new String[1];
        ScriptedWorker worker = task -> {
            workingDirectoryTaskRunId[0] = task.getTaskRun().getId();
            return childResult(task.getTaskRun(), "child", State.Type.SUCCESS);
        };

        harness.run(created, worker);

        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        assertThat(harness.workerJobRunningStateStore().deletedKeys())
            .containsExactly(workingDirectoryTaskRunId[0]);
    }

    @Test
    void releasesTheRunningEntryWhenAFailedParentForceTerminatesTheWorkingDirectory() {
        // A fail-fast parent (Parallel/Dag) terminates while a WorkingDirectory sibling is still
        // running; the executor force-terminates the WorkingDirectory to match the parent
        // (ExecutorService#handleFlowableTasks), a path that never goes through resolveState.
        FlowWithSource flow = Flows.yaml("""
            id: wd_force_terminated
            namespace: io.kestra.tests
            tasks:
              - id: par
                type: io.kestra.plugin.core.flow.Parallel
                tasks:
                  - id: wd
                    type: io.kestra.plugin.core.flow.WorkingDirectory
                    tasks:
                      - id: child
                        type: io.kestra.plugin.core.log.Log
                        message: hello
            """);
        harness.registerFlow(flow);

        String executionId = IdUtils.create();
        TaskRun parent = taskRun(executionId, "par", null).withState(State.Type.FAILED);
        TaskRun workingDirectory = taskRun(executionId, "wd", parent.getId());
        // an in-flight leaf child keeps the WorkingDirectory from resolving itself, so the only way
        // it terminates is the parent forcing it — the path under test.
        TaskRun child = taskRun(executionId, "child", workingDirectory.getId());

        Execution execution = Execution.builder()
            .id(executionId)
            .tenantId(Flows.TENANT)
            .namespace(NAMESPACE)
            .flowId("wd_force_terminated")
            .state(new State().withState(State.Type.RUNNING))
            .taskRunList(List.of(parent, workingDirectory, child))
            .build();

        harness.process(flow, execution);

        assertThat(harness.workerJobRunningStateStore().deletedKeys())
            .contains(workingDirectory.getId());
    }

    private static TaskRun taskRun(String executionId, String taskId, String parentTaskRunId) {
        return TaskRun.builder()
            .id(IdUtils.create())
            .tenantId(Flows.TENANT)
            .executionId(executionId)
            .namespace(NAMESPACE)
            .flowId("wd_force_terminated")
            .taskId(taskId)
            .parentTaskRunId(parentTaskRunId)
            .state(new State())
            .build()
            .withState(State.Type.RUNNING);
    }

    private static WorkerTaskResult childResult(TaskRun workingDirectory, String childTaskId, State.Type state) {
        TaskRunAttempt attempt = TaskRunAttempt.builder()
            .state(new State(state, List.of(
                new State.History(State.Type.CREATED, T0.minusSeconds(1)),
                new State.History(state, T0)
            )))
            .build();

        TaskRun child = TaskRun.builder()
            .id(IdUtils.create())
            .tenantId(workingDirectory.getTenantId())
            .executionId(workingDirectory.getExecutionId())
            .namespace(workingDirectory.getNamespace())
            .flowId(workingDirectory.getFlowId())
            .taskId(childTaskId)
            .parentTaskRunId(workingDirectory.getId())
            .state(new State())
            .build()
            .withAttempts(List.of(attempt))
            .withState(state);

        return new WorkerTaskResult(child);
    }
}
