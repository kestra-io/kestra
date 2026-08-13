package io.kestra.executor.handler;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.SubflowExecutionEnd;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.plugin.core.flow.Subflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubflowExecutionEndMessageHandlerTest {
    private ExecutorTestHarness harness;

    private Subflow subflowTask;
    private Execution parentExecution;
    private TaskRun parentTaskRun;
    private Execution childExecution;

    @BeforeEach
    void setUp() {
        harness = ExecutorTestHarness.create();
    }

    @Test
    void shouldEmitSubflowExecutionResultWhenChildExecutionEnds() {
        // Given — a parent execution whose Subflow task waits for a registered child execution
        var message = givenASubflowScenario(true);

        // The real Subflow.createSubflowExecutionResult unconditionally dereferences the Micronaut
        // ApplicationContext (runContext.services()), which the harness deliberately only stubs
        // partially — without this stub the swallowed NPE would degrade into a
        // synthetic FAILED result. Stub the task boundary and assert the handler's own work:
        // resolving the task and taskRun, applying the message state, and emitting on the queue.
        var subflowExecutionResult = SubflowExecutionResult.builder()
            .executionId(childExecution.getId())
            .state(State.Type.SUCCESS)
            .parentTaskRun(parentTaskRun.withState(State.Type.SUCCESS))
            .build();
        doReturn(Optional.of(subflowExecutionResult))
            .when(subflowTask).createSubflowExecutionResult(any(), any(), any(), any(), any());

        // When
        harness.subflowExecutionEndMessageHandler().handle(message);

        // Then — the result created from the child execution is emitted on the subflow execution result queue
        assertThat(harness.subflowExecutionResultQueue().emitted()).hasSize(1);
        assertThat(harness.subflowExecutionResultQueue().emitted().getFirst()).isSameAs(subflowExecutionResult);

        // and the handler passed the parent taskRun moved to the state carried by the message
        var taskRunCaptor = ArgumentCaptor.forClass(TaskRun.class);
        verify(subflowTask).createSubflowExecutionResult(any(), taskRunCaptor.capture(), any(), same(childExecution), isNull());
        assertThat(taskRunCaptor.getValue().getId()).isEqualTo(parentTaskRun.getId());
        assertThat(taskRunCaptor.getValue().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    void shouldNotEmitSubflowExecutionResultWhenTaskDoesNotWaitForExecution() {
        // Given — the same scenario but the Subflow task does not wait for the child execution
        var message = givenASubflowScenario(false);

        // When — handler resolves the task then no-ops
        harness.subflowExecutionEndMessageHandler().handle(message);

        // Then
        assertThat(harness.subflowExecutionResultQueue().emitted()).isEmpty();
    }

    @Test
    void shouldNotEmitSubflowExecutionResultWhenChildExecutionKillSwitched() {
        // Given — a scenario that would emit, but the child execution is kill-switched
        var message = givenASubflowScenario(true);
        when(harness.killSwitchService().evaluate(any(Execution.class))).thenReturn(EvaluationType.IGNORE);

        // When — handler returns early, no exception
        harness.subflowExecutionEndMessageHandler().handle(message);

        // Then — nothing is emitted and the parent kill switch is never even evaluated
        assertThat(harness.subflowExecutionResultQueue().emitted()).isEmpty();
        verify(harness.killSwitchService(), never()).evaluate(anyString());
    }

    @Test
    void shouldNotEmitSubflowExecutionResultWhenParentExecutionKillSwitched() {
        // Given — a scenario that would emit, but the child passes and the parent is kill-switched
        var message = givenASubflowScenario(true);
        when(harness.killSwitchService().evaluate(parentExecution.getId())).thenReturn(EvaluationType.IGNORE);

        // When — handler returns early, no exception
        harness.subflowExecutionEndMessageHandler().handle(message);

        // Then
        assertThat(harness.subflowExecutionResultQueue().emitted()).isEmpty();
    }

    /**
     * Seeds a complete "child execution ended" scenario: a parent flow holding a (spied) Subflow
     * task, a parent execution with the matching RUNNING taskRun, and a registered child flow with
     * its execution — so the only thing standing between the handler and an emission is the
     * behavior under test. Returns the SubflowExecutionEnd message reporting the child as SUCCESS.
     */
    private SubflowExecutionEnd givenASubflowScenario(boolean waitForExecution) {
        var childFlow = Fixtures.flow();
        harness.registerFlow(Flows.of(childFlow));
        childExecution = Execution.newExecution(childFlow, Collections.emptyList());

        subflowTask = spy(
            Subflow.builder()
                .id("subflow-task")
                .type(Subflow.class.getName())
                .namespace(childFlow.getNamespace())
                .flowId(childFlow.getId())
                .wait(waitForExecution)
                .build()
        );
        var parentFlow = Flows.of(subflowTask);
        harness.registerFlow(parentFlow);

        parentExecution = Execution.newExecution(parentFlow, Collections.emptyList());
        parentTaskRun = TaskRun.builder()
            .id("taskrun-1")
            .taskId(subflowTask.getId())
            .executionId(parentExecution.getId())
            .namespace(parentExecution.getNamespace())
            .flowId(parentExecution.getFlowId())
            .state(new State().withState(State.Type.RUNNING))
            .build();
        harness.executionStateStore().save(parentExecution.withTaskRunList(Collections.singletonList(parentTaskRun)));

        return new SubflowExecutionEnd(
            childExecution,
            parentExecution.getId(),
            parentTaskRun.getId(),
            subflowTask.getId(),
            State.Type.SUCCESS
        );
    }
}
