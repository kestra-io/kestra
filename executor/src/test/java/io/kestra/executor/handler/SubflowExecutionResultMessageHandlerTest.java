package io.kestra.executor.handler;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class SubflowExecutionResultMessageHandlerTest {
    private ExecutorTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = ExecutorTestHarness.create();
    }

    @Test
    void shouldReturnEmptyForNonExistingExecution() {
        var subflowExecutionResult = SubflowExecutionResult.builder()
            .executionId("execution")
            .state(State.Type.SUCCESS)
            .parentTaskRun(
                TaskRun.builder()
                    .id("parent")
                    .flowId("flow")
                    .namespace("namespace")
                    .executionId("execution")
                    .build()
            )
            .build();

        var maybeExecutor = harness.subflowExecutionResultMessageHandler().handle(subflowExecutionResult);

        assertThat(maybeExecutor).isEmpty();
    }

    @Test
    void shouldReturnAnExecutorForExistingExecution() {
        // Given — a running parent execution whose subflow parent taskrun is still RUNNING
        var flow = Fixtures.flow();
        harness.registerFlow(Flows.of(flow));
        var parentExecution = Execution.newExecution(flow, Collections.emptyList()).withState(State.Type.RUNNING);
        var parentTaskRun = TaskRun.builder()
            .id("parent")
            .tenantId(parentExecution.getTenantId())
            .namespace(parentExecution.getNamespace())
            .flowId(parentExecution.getFlowId())
            .taskId(flow.getTasks().getFirst().getId())
            .executionId(parentExecution.getId())
            .state(new State().withState(State.Type.RUNNING))
            .build();
        harness.executionStateStore().save(parentExecution.withTaskRunList(Collections.singletonList(parentTaskRun)));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        harness.executionStateStore().save(execution);
        var subflowExecutionResult = SubflowExecutionResult.builder()
            .executionId(execution.getId())
            .state(State.Type.SUCCESS)
            .parentTaskRun(parentTaskRun.withState(State.Type.SUCCESS))
            .outputs(Map.of("value", "hello"))
            .build();

        // When
        var maybeExecutor = harness.subflowExecutionResultMessageHandler().handle(subflowExecutionResult);

        // Then — the result is joined: parent taskrun goes SUCCESS, execution stays RUNNING, outputs are saved
        assertThat(maybeExecutor).isPresent();
        var executor = maybeExecutor.get();
        assertThat(executor.getFrom()).contains("joinSubflowExecutionResult");
        assertThat(executor.getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(executor.getExecution().getTaskRunList()).hasSize(1);
        assertThat(executor.getExecution().getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(harness.taskOutputRepository().findById(parentTaskRun.getTenantId(), parentTaskRun.getId())).isPresent();
    }

    @Test
    void shouldReturnEmptyWhenChildExecutionKillSwitched() {
        // Given — child execution is kill-switched
        var parentTaskRun = TaskRun.builder()
            .id("taskrun-1").executionId("parent-exec-1").taskId("task-1")
            .state(new State().withState(State.Type.RUNNING)).build();
        var message = SubflowExecutionResult.builder()
            .executionId("child-exec-1").parentTaskRun(parentTaskRun).build();
        when(harness.killSwitchService().evaluate("child-exec-1")).thenReturn(EvaluationType.IGNORE);

        // When
        Optional<ExecutorContext> result = harness.subflowExecutionResultMessageHandler().handle(message);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenParentTaskRunKillSwitched() {
        // Given — child passes but parent task run is kill-switched
        var parentTaskRun = TaskRun.builder()
            .id("taskrun-1").executionId("parent-exec-1").taskId("task-1")
            .state(new State().withState(State.Type.RUNNING)).build();
        var message = SubflowExecutionResult.builder()
            .executionId("child-exec-1").parentTaskRun(parentTaskRun).build();
        when(harness.killSwitchService().evaluate(any(TaskRun.class))).thenReturn(EvaluationType.IGNORE);

        // When
        Optional<ExecutorContext> result = harness.subflowExecutionResultMessageHandler().handle(message);

        // Then
        assertThat(result).isEmpty();
    }
}
