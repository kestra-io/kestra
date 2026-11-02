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

class SubflowKillTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final List<String> executedTasks = new ArrayList<>();
    private final AtomicReference<WorkerTask> childTask = new AtomicReference<>();
    private final FlowWithSource child = Flows.yaml("""
        id: child
        namespace: io.kestra.tests
        tasks:
          - id: work
            type: io.kestra.plugin.core.log.Log
            message: work
        """);

    @Test
    void shouldEndKilledWhenKilledWithoutCascadeWhileWaitingOnTheLastSubflow() {
        // Given
        harness.registerFlow(child);
        FlowWithSource parent = parent("transmitFailed: true", false);
        harness.registerFlow(parent);
        Execution created = Executions.created(parent);
        harness.run(created, runningChildWorker());

        // When
        harness.run(List.of(killRequest(created.getId(), created.getTenantId())), runningChildWorker());
        harness.run(List.of(Results.success(childTask.get(), T0)), runningChildWorker());

        // Then
        assertThat(harness).hasExecutionInState(created, State.Type.KILLED);
    }

    @Test
    void shouldFailTheParentWithoutRunningNextTasksWhenTheSubflowExecutionIsKilled() {
        // Given
        harness.registerFlow(child);
        FlowWithSource parent = parent("transmitFailed: true", true);
        harness.registerFlow(parent);
        Execution created = Executions.created(parent);
        harness.run(created, runningChildWorker());

        // When
        killChild(created);

        // Then
        assertThat(executedTasks).containsExactly("on_error");
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED);
    }

    @Test
    void shouldContinueWhenTheSubflowExecutionIsKilledAndTheSubflowAllowsFailure() {
        // Given
        harness.registerFlow(child);
        FlowWithSource parent = parent("transmitFailed: true\nallowFailure: true", true);
        harness.registerFlow(parent);
        Execution created = Executions.created(parent);
        harness.run(created, runningChildWorker());

        // When
        killChild(created);

        // Then
        assertThat(executedTasks).containsExactly("after");
        assertThat(harness).hasExecutionInState(created, State.Type.WARNING);
    }

    @Test
    void shouldContinueWhenTheSubflowExecutionIsKilledAndFailuresAreNotTransmitted() {
        // Given
        harness.registerFlow(child);
        FlowWithSource parent = parent("transmitFailed: false", true);
        harness.registerFlow(parent);
        Execution created = Executions.created(parent);
        harness.run(created, runningChildWorker());

        // When
        killChild(created);

        // Then
        assertThat(executedTasks).containsExactly("after");
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
    }

    @Test
    void shouldEndKilledWhenTheParentIsKilledAndTheSubflowExecutionIsKilledWithIt() {
        // Given
        harness.registerFlow(child);
        FlowWithSource parent = parent("transmitFailed: true", true);
        harness.registerFlow(parent);
        Execution created = Executions.created(parent);
        harness.run(created, runningChildWorker());

        // When
        harness.run(List.of(killRequest(created.getId(), created.getTenantId())), recordingWorker());
        killChild(created);

        // Then
        assertThat(executedTasks).isEmpty();
        assertThat(harness).hasExecutionInState(created, State.Type.KILLED);
    }

    private void killChild(Execution parent) {
        harness.run(List.of(killRequest(childTask.get().getTaskRun().getExecutionId(), parent.getTenantId())), recordingWorker());
        harness.run(List.of(Results.killed(childTask.get(), T0)), recordingWorker());
    }

    private FlowWithSource parent(String subflowProperty, boolean withNextAndErrors) {
        String subflow = """
            id: parent
            namespace: io.kestra.tests
            tasks:
              - id: subflow_call
                type: io.kestra.plugin.core.flow.Subflow
                namespace: io.kestra.tests
                flowId: child
                wait: true
            """ + subflowProperty.indent(4);
        String nextAndErrors = withNextAndErrors ? """
              - id: after
                type: io.kestra.plugin.core.log.Log
                message: after
            errors:
              - id: on_error
                type: io.kestra.plugin.core.log.Log
                message: error
            """ : "";
        return Flows.yaml(subflow + nextAndErrors);
    }

    private ScriptedWorker runningChildWorker() {
        return task ->
        {
            childTask.set(task);
            return new WorkerTaskResult(task.getTaskRun().withState(State.Type.RUNNING));
        };
    }

    private ScriptedWorker recordingWorker() {
        return task ->
        {
            executedTasks.add(task.getTask().getId());
            return Results.success(task, T0);
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
