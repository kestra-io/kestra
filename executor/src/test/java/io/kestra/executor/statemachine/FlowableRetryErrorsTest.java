package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
import io.kestra.executor.testkit.ScriptedWorker;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A flowable's retry is spent on its failed children: once they are exhausted, the flowable fails for good and the
 * flow's errors branch must run.
 */
class FlowableRetryErrorsTest {

    private static final Instant ATTEMPT_END = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant AFTER_ANY_RETRY_DELAY = Instant.parse("2100-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final List<WorkerTask> dispatched = new ArrayList<>();
    private final FlowWithSource flow = Flows.yaml("""
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

    @Test
    void shouldRunTheErrorsBranchWhenTheFlowableRetriesAreExhausted() {
        // Given: the child fails on its first attempt
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        harness.run(created, recordingWorker());

        // When: every retry fires and the child keeps failing
        for (int i = 0; i < 3; i++) {
            harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
            harness.run(List.of(), recordingWorker());
        }

        // Then
        assertThat(dispatched).extracting(task -> task.getTaskRun().getTaskId()).containsExactly("child", "child", "handler");
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED);
    }

    // like the real worker, each run appends its attempt to the ones the task run already has
    private ScriptedWorker recordingWorker() {
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
