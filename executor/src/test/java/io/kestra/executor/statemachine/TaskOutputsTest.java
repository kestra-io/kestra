package io.kestra.executor.statemachine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.executor.testkit.ScriptedWorker;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outputs a task produces are saved by the executor, so that what runs after it, down to the flow outputs, can read
 * them back.
 */
class TaskOutputsTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    @Test
    void shouldExposeWorkerTaskOutputsToTheFlowOutputs() throws Exception {
        // Given
        FlowWithSource flow = Flows.yaml("""
            id: worker-outputs
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
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);

        // When
        harness.run(created, task -> Results.success(task, T0, Map.of("value", "hello")));

        // Then
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        assertThat(executionOutputs(created)).isEqualTo(Map.of("verdict", "hello"));
    }

    @Test
    void shouldExposeFlowableOutputsToItsChildrenAndTheFlowOutputs() throws Exception {
        // Given: an If evaluated by the executor while its Switch parent is still running
        FlowWithSource flow = Flows.yaml("""
            id: flowable-outputs
            namespace: io.kestra.tests
            outputs:
              - id: chosen
                type: STRING
                value: "{{ outputs.switch.value }}"
            tasks:
              - id: switch
                type: io.kestra.plugin.core.flow.Switch
                value: "b"
                cases:
                  b:
                    - id: if
                      type: io.kestra.plugin.core.flow.If
                      condition: "{{ outputs.switch.value == 'b' }}"
                      then:
                        - id: then
                          type: io.kestra.plugin.core.log.Log
                          message: then
                      else:
                        - id: else
                          type: io.kestra.plugin.core.log.Log
                          message: else
            """);
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        List<String> dispatched = new ArrayList<>();

        // When
        harness.run(created, task ->
        {
            dispatched.add(task.getTaskRun().getTaskId());
            return Results.success(task, T0);
        });

        // Then
        assertThat(dispatched).as("the child read the running Switch's output").containsExactly("then");
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        assertThat(executionOutputs(created)).isEqualTo(Map.of("chosen", "b"));
    }

    @Test
    void shouldStopLoopUntilWhenItsIterationCountOutputMeetsTheCondition() throws Exception {
        // Given: a loop whose exit condition reads its own iteration count
        FlowWithSource flow = Flows.yaml("""
            id: loop-until-outputs
            namespace: io.kestra.tests
            outputs:
              - id: iterations
                type: STRING
                value: "{{ outputs.loop.iterationCount }}"
            tasks:
              - id: loop
                type: io.kestra.plugin.core.flow.LoopUntil
                condition: "{{ outputs.loop.iterationCount >= 3 }}"
                checkFrequency:
                  interval: PT1S
                  maxIterations: 10
                tasks:
                  - id: work
                    type: io.kestra.plugin.core.log.Log
                    message: work
            """);
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        List<String> dispatched = new ArrayList<>();
        ScriptedWorker recording = task ->
        {
            dispatched.add(task.getTaskRun().getTaskId());
            return Results.success(task, T0);
        };

        // When: every next iteration is due (LoopUntil dates them on the wall clock)
        harness.run(created, recording);
        for (int i = 0; i < 12; i++) {
            harness.tickExecutionDelays(Instant.now().plus(Duration.ofHours(1)));
            harness.run(List.of(), recording);
        }

        // Then
        assertThat(dispatched).as("the count reached 3 on the third iteration").hasSize(3);
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        assertThat(executionOutputs(created)).isEqualTo(Map.of("iterations", "3"));
    }

    @Test
    void shouldExposeSubflowOutputsToItsParent() throws Exception {
        // Given
        harness.registerFlow(Flows.yaml("""
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
            """));
        FlowWithSource parent = Flows.yaml("""
            id: parent
            namespace: io.kestra.tests
            outputs:
              - id: fromChild
                type: STRING
                value: "{{ outputs.subflow_call.outputs.verdict }}"
            tasks:
              - id: subflow_call
                type: io.kestra.plugin.core.flow.Subflow
                namespace: io.kestra.tests
                flowId: child
                wait: true
            """);
        harness.registerFlow(parent);
        Execution created = Executions.created(parent);

        // When
        harness.run(created, task -> Results.success(task, T0, Map.of("value", "from the child")));

        // Then
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        assertThat(executionOutputs(created)).isEqualTo(Map.of("fromChild", "from the child"));
    }

    private Map<String, Object> executionOutputs(Execution execution) throws Exception {
        return harness.executionOutputService().getOutputs(harness.executionStateStore().findByIdWithoutAcl(execution.getId()));
    }
}
