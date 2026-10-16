package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
import io.kestra.executor.testkit.Results;
import io.kestra.executor.testkit.ScriptedWorker;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A flowable's retry is spent on its failed children: once they are exhausted, the flowable fails for good and the
 * flow's errors and finally branches must run.
 */
class FlowableRetryErrorsTest {

    private static final Instant ATTEMPT_END = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant AFTER_ANY_RETRY_DELAY = Instant.parse("2100-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final List<WorkerTask> dispatched = new ArrayList<>();

    @Test
    void shouldRunTheErrorsBranchWhenTheFlowableRetriesAreExhausted() {
        FlowWithSource flow = Flows.yaml("""
            id: flowable-retry-errors
            namespace: io.kestra.tests
            tasks:
              - id: flowable
                type: io.kestra.plugin.core.flow.Sequential
                retry:
                  type: constant
                  interval: PT1S
                  maxAttempts: 2
                tasks:
                  - id: child
                    type: io.kestra.plugin.core.log.Log
                    message: child
            errors:
              - id: handler
                type: io.kestra.plugin.core.log.Log
                message: handler
            """);

        Execution created = runUntilQuiet(flow, failingChildWorker());

        assertThat(dispatchedTaskIds()).containsExactly("child", "child", "handler");
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED);
    }

    @Test
    void shouldRunTheFinallyBranchWhenTheFlowableRetriesAreExhausted() {
        FlowWithSource flow = Flows.yaml("""
            id: flowable-retry-finally
            namespace: io.kestra.tests
            tasks:
              - id: flowable
                type: io.kestra.plugin.core.flow.Sequential
                retry:
                  type: constant
                  interval: PT1S
                  maxAttempts: 2
                tasks:
                  - id: child
                    type: io.kestra.plugin.core.log.Log
                    message: child
            finally:
              - id: cleanup
                type: io.kestra.plugin.core.log.Log
                message: cleanup
            """);

        Execution created = runUntilQuiet(flow, failingChildWorker());

        assertThat(dispatchedTaskIds()).containsExactly("child", "child", "cleanup");
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED);
    }

    @Test
    void shouldRunTheErrorsBranchWhenANestedFlowableRetriesAreExhausted() {
        FlowWithSource flow = Flows.yaml("""
            id: nested-flowable-retry-errors
            namespace: io.kestra.tests
            tasks:
              - id: outer
                type: io.kestra.plugin.core.flow.Sequential
                retry:
                  type: constant
                  interval: PT1S
                  maxAttempts: 2
                tasks:
                  - id: inner
                    type: io.kestra.plugin.core.flow.Sequential
                    tasks:
                      - id: child
                        type: io.kestra.plugin.core.log.Log
                        message: child
            errors:
              - id: handler
                type: io.kestra.plugin.core.log.Log
                message: handler
            """);

        Execution created = runUntilQuiet(flow, failingChildWorker());

        assertThat(dispatchedTaskIds()).containsExactly("child", "child", "handler");
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED);
    }

    @Test
    void shouldRunTheErrorsBranchWhenTheWorkingDirectoryRetriesAreExhausted() {
        FlowWithSource flow = Flows.yaml("""
            id: working-directory-retry-errors
            namespace: io.kestra.tests
            tasks:
              - id: working-directory
                type: io.kestra.plugin.core.flow.WorkingDirectory
                retry:
                  type: constant
                  interval: PT1S
                  maxAttempts: 2
                tasks:
                  - id: child
                    type: io.kestra.plugin.core.log.Log
                    message: child
            errors:
              - id: handler
                type: io.kestra.plugin.core.log.Log
                message: handler
            """);

        // the WorkingDirectory runs on the worker, which reports its failed child
        Execution created = runUntilQuiet(flow, task -> {
            dispatched.add(task);
            return "working-directory".equals(task.getTaskRun().getTaskId())
                ? failedChildOf(task, "child")
                : Results.success(task, ATTEMPT_END);
        });

        assertThat(dispatchedTaskIds()).containsExactly("working-directory", "working-directory", "handler");
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED);
    }

    private Execution runUntilQuiet(FlowWithSource flow, ScriptedWorker worker) {
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        harness.run(created, worker);
        // fire every retry; more ticks than maxAttempts so an extra retry would show in the dispatched tasks
        for (int i = 0; i < 4; i++) {
            harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
            harness.run(List.of(), worker);
        }
        return created;
    }

    private List<String> dispatchedTaskIds() {
        return dispatched.stream().map(task -> task.getTaskRun().getTaskId()).toList();
    }

    // like the real worker, each run appends its attempt to the ones the task run already has
    private ScriptedWorker failingChildWorker() {
        ScriptedWorker failingChild = ScriptedWorker.failing("child", ATTEMPT_END);
        return task ->
        {
            dispatched.add(task);
            WorkerTaskResult result = failingChild.run(task);
            List<TaskRunAttempt> attempts = new ArrayList<>(task.getTaskRun().getAttempts() == null ? List.of() : task.getTaskRun().getAttempts());
            attempts.addAll(result.getTaskRun().getAttempts());
            return new WorkerTaskResult(result.getTaskRun().withAttempts(attempts));
        };
    }

    private static WorkerTaskResult failedChildOf(WorkerTask workingDirectory, String taskId) {
        TaskRun child = workingDirectory.getTaskRun().toBuilder()
            .id(IdUtils.create())
            .taskId(taskId)
            .parentTaskRunId(workingDirectory.getTaskRun().getId())
            .attempts(List.of(TaskRunAttempt.builder().workerId("worker").state(new State().withState(State.Type.FAILED)).build()))
            .state(new State().withState(State.Type.FAILED))
            .build();
        return new WorkerTaskResult(child);
    }
}
