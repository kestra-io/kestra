package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.FollowExecutionEvent;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTask;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.plugin.core.flow.WorkingDirectory;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A CREATE_NEW_EXECUTION retry is counted on the execution: each attempt is a replacement execution whose task runs
 * start afresh, so once the execution's attempts are spent the failure is final and the flow's handlers must run.
 */
class RetryNewExecutionErrorsTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant AFTER_ANY_RETRY_DELAY = Instant.parse("2100-01-01T00:00:00Z");

    private static final String NEW_EXECUTION_RETRY = """
        retry:
          behavior: CREATE_NEW_EXECUTION
          type: constant
          interval: PT1S
          maxAttempts: 2
        """;
    private static final String FLOW_ERRORS = """
        errors:
          - id: handler
            type: io.kestra.plugin.core.log.Log
            message: handler
        """;

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final List<WorkerTask> dispatched = new ArrayList<>();

    @Test
    void shouldRunTheErrorsBranchInTheLastExecutionWhenATopLevelTaskRetriesAreExhausted() {
        FlowWithSource flow = Flows.yaml("""
            id: new-execution-retry-top-level
            namespace: io.kestra.tests
            tasks:
              - id: flaky
                type: io.kestra.plugin.core.log.Log
                message: flaky
            """ + NEW_EXECUTION_RETRY.indent(4) + FLOW_ERRORS);

        Execution created = runUntilQuiet(flow, failing("flaky"));

        assertThat(dispatchedTaskIds()).containsExactly("flaky", "flaky", "handler");
        assertThat(harness).hasExecutionInState(created, State.Type.RETRIED).hasExecutionInState(replacementOf(created), State.Type.FAILED);
    }

    @Test
    void shouldRunTheErrorsBranchInTheLastExecutionWhenASequentialChildRetriesAreExhausted() {
        FlowWithSource flow = Flows.yaml("""
            id: new-execution-retry-sequential
            namespace: io.kestra.tests
            tasks:
              - id: sequential
                type: io.kestra.plugin.core.flow.Sequential
                tasks:
                  - id: flaky
                    type: io.kestra.plugin.core.log.Log
                    message: flaky
            """ + NEW_EXECUTION_RETRY.indent(8) + FLOW_ERRORS);

        Execution created = runUntilQuiet(flow, failing("flaky"));

        assertThat(dispatchedTaskIds()).containsExactly("flaky", "flaky", "handler");
        assertThat(harness).hasExecutionInState(created, State.Type.RETRIED).hasExecutionInState(replacementOf(created), State.Type.FAILED);
    }

    @Test
    void shouldCountTheRetriesOnTheExecutionWhenAWorkingDirectoryChildRetriesInANewExecution() {
        FlowWithSource flow = Flows.yaml("""
            id: new-execution-retry-working-directory
            namespace: io.kestra.tests
            tasks:
              - id: working-directory
                type: io.kestra.plugin.core.flow.WorkingDirectory
                tasks:
                  - id: flaky
                    type: io.kestra.plugin.core.log.Log
                    message: flaky
            """ + NEW_EXECUTION_RETRY.indent(8) + FLOW_ERRORS);

        // the worker runs the WorkingDirectory and reports its failed child
        Execution created = runUntilQuiet(flow, task ->
        {
            dispatched.add(task);
            return task.getTask() instanceof WorkingDirectory ? Results.failed(childOf(task, "flaky"), T0) : Results.success(task, T0);
        });

        assertThat(dispatchedTaskIds()).containsExactly("working-directory", "working-directory", "handler");
        assertThat(harness).hasExecutionInState(created, State.Type.RETRIED).hasExecutionInState(replacementOf(created), State.Type.FAILED);
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

    /** maxAttempts 2 allows exactly one replacement execution. */
    private Execution replacementOf(Execution original) {
        List<String> replacementIds = harness.followExecutionEventQueue().emitted().stream()
            .map(FollowExecutionEvent::executionId)
            .filter(executionId -> !executionId.equals(original.getId()))
            .distinct()
            .toList();
        assertThat(replacementIds).as("the executions created by the retries").hasSize(1);
        return harness.executionStateStore().findByIdWithoutAcl(replacementIds.getFirst());
    }

    private ScriptedWorker failing(String taskId) {
        return task ->
        {
            dispatched.add(task);
            return taskId.equals(task.getTaskRun().getTaskId()) ? Results.failed(task, T0) : Results.success(task, T0);
        };
    }

    private List<String> dispatchedTaskIds() {
        return dispatched.stream().map(task -> task.getTaskRun().getTaskId()).toList();
    }

    private WorkerTask childOf(WorkerTask workingDirectory, String taskId) {
        Execution execution = harness.executionStateStore().findByIdWithoutAcl(workingDirectory.getTaskRun().getExecutionId());
        FlowWithSource flow = harness.flowMetaStore().findByExecutionForRuntime(execution).orElseThrow();
        WorkingDirectory task = (WorkingDirectory) workingDirectory.getTask();
        Task child = task.getTasks().stream().filter(t -> t.getId().equals(taskId)).findFirst().orElseThrow();
        RunContext runContext = harness.runContextFactory().of(flow, task, execution, workingDirectory.getTaskRun());
        return task.workerTask(workingDirectory.getTaskRun(), child, runContext);
    }
}
