package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
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
 * A Subflow task fails when the outputs of its subflow cannot be rendered, and that failure must neither retry it
 * forever nor survive a kill of its execution.
 */
class SubflowRetryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant AFTER_ANY_RETRY_DELAY = Instant.parse("2100-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final List<WorkerTask> childTasks = new ArrayList<>();
    private final FlowWithSource child = Flows.yaml("""
        id: child
        namespace: io.kestra.tests
        outputs:
          - id: verdict
            type: STRING
            value: "{{ outputs.work.value }}"
        tasks:
          - id: work
            type: io.kestra.plugin.core.log.Log
            message: work
        """);
    private final FlowWithSource parent = Flows.yaml("""
        id: parent
        namespace: io.kestra.tests
        tasks:
          - id: subflow_call
            type: io.kestra.plugin.core.flow.Subflow
            namespace: io.kestra.tests
            flowId: child
            wait: true
            transmitFailed: true
            retry:
              type: constant
              interval: PT1S
              maxAttempts: 2
        """);

    @Test
    void shouldStopRetryingWhenTheSubflowOutputsCannotBeRendered() {
        // Given
        harness.registerFlow(child);
        harness.registerFlow(parent);
        Execution created = Executions.created(parent);

        // When: every run of the subflow ends with outputs that cannot be rendered
        harness.run(created, recordingWorker(task -> Results.success(task, T0)));
        for (int i = 0; i < 3; i++) {
            harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
            harness.run(List.of(), recordingWorker(task -> Results.success(task, T0)));
        }

        // Then
        assertThat(childTasks).hasSize(2);
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED);
    }

    @Test
    void shouldStayKilledWhenTheKilledSubflowOutputsCannotBeRendered() {
        // Given: the subflow is running
        harness.registerFlow(child);
        harness.registerFlow(parent);
        Execution created = Executions.created(parent);
        AtomicReference<WorkerTask> running = new AtomicReference<>();
        harness.run(created, recordingWorker(task ->
        {
            running.set(task);
            return new WorkerTaskResult(task.getTaskRun().withState(State.Type.RUNNING));
        }));

        // When: the execution is killed, then its subflow, as a kill cascade does, and the running task of the subflow is killed
        harness.run(List.of(killRequest(created.getId(), created.getTenantId())), recordingWorker(task -> Results.killed(task, T0)));
        harness.run(List.of(killRequest(running.get().getTaskRun().getExecutionId(), created.getTenantId())), recordingWorker(task -> Results.killed(task, T0)));
        harness.run(List.of(Results.killed(running.get(), T0)), recordingWorker(task -> Results.killed(task, T0)));
        harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
        harness.run(List.of(), recordingWorker(task -> Results.success(task, T0)));

        // Then
        assertThat(childTasks).hasSize(1);
        assertThat(harness).hasExecutionInState(created, State.Type.KILLED);
    }

    private ScriptedWorker recordingWorker(ScriptedWorker worker) {
        return task ->
        {
            childTasks.add(task);
            return worker.run(task);
        };
    }

    private static ExecutionKilledExecution killRequest(String executionId, String tenantId) {
        return ExecutionKilledExecution.builder()
            .state(ExecutionKilled.State.REQUESTED)
            .executionId(executionId)
            .isOnKillCascade(false)
            .tenantId(tenantId)
            .build();
    }
}
