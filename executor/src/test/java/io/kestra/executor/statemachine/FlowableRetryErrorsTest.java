package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
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
    void shouldRunTheErrorsBranchWhenARetryFlowableRetriesAreExhausted() {
        FlowWithSource flow = Flows.yaml("""
            id: retry-flowable-errors
            namespace: io.kestra.tests
            tasks:
              - id: flowable
                type: io.kestra.plugin.core.flow.Sequential
                retry:
                  type: constant
                  behavior: RETRY_FLOWABLE
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

        Execution created = runUntilQuiet(flow, task -> {
            dispatched.add(task);
            WorkerTaskResult result = "child".equals(task.getTaskRun().getTaskId())
                ? Results.failed(task, ATTEMPT_END)
                : Results.success(task, ATTEMPT_END);
            List<TaskRunAttempt> attempts = result.getTaskRun().getAttempts().stream()
                .map(attempt -> TaskRunAttempt.builder().state(attempt.getState()).workerId("worker").build())
                .toList();
            return new WorkerTaskResult(result.getTaskRun().withAttempts(attempts));
        });

        assertThat(dispatchedTaskIds()).containsExactly(
            "working-directory_open", "child", "working-directory_close",
            "working-directory_open", "child", "working-directory_close",
            "handler"
        );
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED);
    }

    @Test
    void shouldRunTheWholeWorkingDirectoryAgainForTheRetryOfItsParent() {
        FlowWithSource flow = Flows.yaml("""
            id: working-directory-parent-retry
            namespace: io.kestra.tests
            tasks:
              - id: sequential
                type: io.kestra.plugin.core.flow.Sequential
                retry:
                  type: constant
                  interval: PT1S
                  maxAttempts: 2
                tasks:
                  - id: working-directory
                    type: io.kestra.plugin.core.flow.WorkingDirectory
                    tasks:
                      - id: child
                        type: io.kestra.plugin.core.log.Log
                        message: child
            """);

        Execution created = runUntilQuiet(flow, task -> {
            dispatched.add(task);
            return ranOnOneWorker(task);
        });

        assertThat(dispatchedTaskIds()).containsExactly(
            "working-directory_open", "child", "working-directory_close",
            "working-directory_open", "child", "working-directory_close"
        );
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED);
    }

    @Test
    void shouldRetryAWorkingDirectoryInANewExecutionWhenItsRetryAsksForIt() {
        FlowWithSource flow = Flows.yaml("""
            id: working-directory-retry-new-execution
            namespace: io.kestra.tests
            tasks:
              - id: working-directory
                type: io.kestra.plugin.core.flow.WorkingDirectory
                retry:
                  type: constant
                  interval: PT1S
                  maxAttempts: 2
                  behavior: CREATE_NEW_EXECUTION
                tasks:
                  - id: child
                    type: io.kestra.plugin.core.log.Log
                    message: child
            """);

        Execution created = runUntilQuiet(flow, task -> {
            dispatched.add(task);
            return ranOnOneWorker(task);
        });

        assertThat(dispatched)
            .filteredOn(task -> task.getTaskRun().getExecutionId().equals(created.getId()))
            .extracting(task -> task.getTaskRun().getTaskId())
            .containsExactly("working-directory_open", "child", "working-directory_close");
        assertThat(harness).hasExecutionInState(created, State.Type.RETRIED);
    }

    @Test
    void shouldRunTheWholeWorkingDirectoryAgainWhenItsCloseFailsUnderAFlowRetry() {
        FlowWithSource flow = Flows.yaml("""
            id: working-directory-close-flow-retry
            namespace: io.kestra.tests
            retry:
              type: constant
              interval: PT1S
              maxAttempts: 2
            tasks:
              - id: working-directory
                type: io.kestra.plugin.core.flow.WorkingDirectory
                tasks:
                  - id: child
                    type: io.kestra.plugin.core.log.Log
                    message: child
            """);
        AtomicBoolean closeFailed = new AtomicBoolean();

        Execution created = runUntilQuiet(flow, task -> {
            dispatched.add(task);
            boolean fail = "working-directory_close".equals(task.getTaskRun().getTaskId()) && !closeFailed.getAndSet(true);
            return onWorker(fail ? Results.failed(task, ATTEMPT_END) : Results.success(task, ATTEMPT_END));
        });

        assertThat(dispatchedTaskIds()).containsExactly(
            "working-directory_open", "child", "working-directory_close",
            "working-directory_open", "child", "working-directory_close"
        );
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
    }

    private static WorkerTaskResult ranOnOneWorker(WorkerTask task) {
        return onWorker("child".equals(task.getTaskRun().getTaskId())
            ? Results.failed(task, ATTEMPT_END)
            : Results.success(task, ATTEMPT_END));
    }

    private static WorkerTaskResult onWorker(WorkerTaskResult result) {
        List<TaskRunAttempt> attempts = result.getTaskRun().getAttempts().stream()
            .map(attempt -> TaskRunAttempt.builder().state(attempt.getState()).workerId("worker").build())
            .toList();
        return new WorkerTaskResult(result.getTaskRun().withAttempts(attempts));
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

}
