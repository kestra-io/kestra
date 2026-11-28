package io.kestra.executor.statemachine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTaskResult;
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
    private static final Instant AFTER_ANY_RETRY_DELAY = Instant.parse("2100-01-01T00:00:00Z");

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

    @Test
    void shouldDeleteThePreviousAttemptOutputsWhenARetriedWorkingDirectoryEndsWithNone() {
        // Given
        FlowWithSource flow = Flows.yaml("""
            id: working-directory-retry-outputs
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
            """);

        // When
        Execution created = runWithAFirstAttemptThatFailsAfterSavingOutputs(flow);

        // Then
        TaskRun workingDirectory = taskRun(created, "working-directory");
        assertThat(workingDirectory.getAttempts()).hasSize(2);
        assertThat(harness.taskOutputRepository().findById(workingDirectory.getTenantId(), workingDirectory.getId())).isEmpty();
    }

    @Test
    void shouldKeepThePreviousAttemptOutputsWhenAnotherRetriedFlowableEndsWithNone() {
        // Given: a resumed Pause keeps the outputs of its resume this way
        FlowWithSource flow = Flows.yaml("""
            id: flowable-retry-outputs
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
            """);

        // When
        Execution created = runWithAFirstAttemptThatFailsAfterSavingOutputs(flow);

        // Then
        TaskRun flowable = taskRun(created, "flowable");
        assertThat(flowable.getAttempts()).hasSize(2);
        assertThat(harness.taskOutputRepository().findById(flowable.getTenantId(), flowable.getId())).isPresent();
    }

    private Execution runWithAFirstAttemptThatFailsAfterSavingOutputs(FlowWithSource flow) {
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        AtomicBoolean childFailed = new AtomicBoolean();
        ScriptedWorker worker = task ->
        {
            if (!"child".equals(task.getTaskRun().getTaskId()) || childFailed.getAndSet(true)) {
                return onWorker(Results.success(task, T0));
            }
            TaskRun child = task.getTaskRun();
            harness.taskOutputRepository().save(new TaskOutput(child.getParentTaskRunId(), child.getTenantId(), child.getExecutionId(), "{}".getBytes(), null));
            return onWorker(Results.failed(task, T0));
        };

        harness.run(created, worker);
        for (int i = 0; i < 4; i++) {
            harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
            harness.run(List.of(), worker);
        }

        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        return created;
    }

    private static WorkerTaskResult onWorker(WorkerTaskResult result) {
        List<TaskRunAttempt> attempts = result.getTaskRun().getAttempts().stream()
            .map(attempt -> TaskRunAttempt.builder().state(attempt.getState()).workerId("worker").build())
            .toList();
        return new WorkerTaskResult(result.getTaskRun().withAttempts(attempts));
    }

    private TaskRun taskRun(Execution execution, String taskId) {
        return harness.executionStateStore().findByIdWithoutAcl(execution.getId()).findTaskRunsByTaskId(taskId).getFirst();
    }

    private Map<String, Object> executionOutputs(Execution execution) throws Exception {
        return harness.executionOutputService().getOutputs(harness.executionStateStore().findByIdWithoutAcl(execution.getId()));
    }
}
