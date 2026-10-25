package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.executor.testkit.Trace;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

class SubflowCancelTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final List<String> executedTasks = new ArrayList<>();
    private final FlowWithSource child = Flows.yaml("""
        id: child
        namespace: io.kestra.tests
        concurrency:
          limit: 1
          behavior: CANCEL
        tasks:
          - id: work
            type: io.kestra.plugin.core.log.Log
            message: work
        """);

    @Test
    void shouldFailTheParentWithoutRunningNextTasksWhenTheSubflowExecutionIsCancelled() {
        // Given
        FlowWithSource parent = registerFlows("transmitFailed: true");
        harness.run(Executions.created(parent), runningChildWorker());

        // When
        Execution created = Executions.created(parent);
        Trace trace = harness.run(created, recordingWorker());

        // Then
        Execution cancelledChild = trace.emitted("execution")
            .map(emission -> emission.as(Execution.class))
            .filter(execution -> "child".equals(execution.getFlowId()))
            .findFirst()
            .orElseThrow();
        assertThat(executedTasks).containsExactly("on_error");
        assertThat(harness)
            .hasExecutionInState(cancelledChild, State.Type.CANCELLED)
            .hasExecutionInState(created, State.Type.FAILED);
    }

    @Test
    void shouldContinueWhenTheSubflowExecutionIsCancelledAndTheSubflowAllowsFailure() {
        // Given
        FlowWithSource parent = registerFlows("transmitFailed: true\nallowFailure: true");
        harness.run(Executions.created(parent), runningChildWorker());

        // When
        Execution created = Executions.created(parent);
        harness.run(created, recordingWorker());

        // Then
        assertThat(executedTasks).containsExactly("after");
        assertThat(harness).hasExecutionInState(created, State.Type.WARNING);
    }

    private FlowWithSource registerFlows(String subflowProperty) {
        FlowWithSource parent = Flows.yaml("""
            id: parent
            namespace: io.kestra.tests
            tasks:
              - id: subflow_call
                type: io.kestra.plugin.core.flow.Subflow
                namespace: io.kestra.tests
                flowId: child
                wait: true
            """ + subflowProperty.indent(4) + """
              - id: after
                type: io.kestra.plugin.core.log.Log
                message: after
            errors:
              - id: on_error
                type: io.kestra.plugin.core.log.Log
                message: error
            """);
        harness.registerFlow(child);
        harness.registerFlow(parent);
        return parent;
    }

    private static ScriptedWorker runningChildWorker() {
        return task -> new WorkerTaskResult(task.getTaskRun().withState(State.Type.RUNNING));
    }

    private ScriptedWorker recordingWorker() {
        return task ->
        {
            executedTasks.add(task.getTask().getId());
            return Results.success(task, T0);
        };
    }
}
