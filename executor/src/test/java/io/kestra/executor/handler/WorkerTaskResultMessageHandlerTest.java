package io.kestra.executor.handler;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkerTaskResultMessageHandlerTest {
    private ExecutorTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = ExecutorTestHarness.create();
    }

    @Test
    void shouldReturnEmptyForNonExistingExecution() {
        var workerTaskResult = WorkerTaskResult.builder()
            .taskRun(
                TaskRun.builder()
                    .executionId("execution")
                    .id("taskrun")
                    .taskId("task")
                    .build()
            )
            .build();

        var maybeExecutor = harness.workerTaskResultMessageHandler().handle(workerTaskResult);

        assertThat(maybeExecutor).isEmpty();
    }

    @Test
    void shouldReturnAnExecutorForExistingExecution() {
        var flow = Fixtures.flow();
        harness.registerFlow(Flows.of(flow));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        var taskRun = TaskRun.builder()
            .executionId(execution.getId())
            .namespace(execution.getNamespace())
            .flowId(execution.getFlowId())
            .id("taskrun")
            .taskId(flow.getTasks().getFirst().getId())
            .state(new State().withState(State.Type.SUBMITTED))
            .build();
        harness.executionStateStore().save(execution.withTaskRunList(Collections.singletonList(taskRun)));
        var workerTaskResult = WorkerTaskResult.builder()
            .taskRun(taskRun.withState(State.Type.SUCCESS))
            .build();

        var maybeExecutor = harness.workerTaskResultMessageHandler().handle(workerTaskResult);

        assertThat(maybeExecutor).isPresent();
        assertThat(maybeExecutor.get().getExecution().getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        verify(harness.workerTaskResultListener()).onJoined(eq(workerTaskResult), any());
    }

    @Test
    void shouldNotNotifyListenersWhenNotJoined() {
        var workerTaskResult = WorkerTaskResult.builder()
            .taskRun(TaskRun.builder().executionId("execution").id("taskrun").taskId("task").build())
            .build();

        var maybeExecutor = harness.workerTaskResultMessageHandler().handle(workerTaskResult);

        assertThat(maybeExecutor).isEmpty();
        verify(harness.workerTaskResultListener(), never()).onJoined(any(), any());
    }

    @Test
    void shouldNotifyListenerOnlyOnceOnRedelivery() {
        var flow = Fixtures.flow();
        harness.registerFlow(Flows.of(flow));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        var taskRun = TaskRun.builder()
            .executionId(execution.getId())
            .namespace(execution.getNamespace())
            .flowId(execution.getFlowId())
            .id("taskrun")
            .taskId(flow.getTasks().getFirst().getId())
            .state(new State().withState(State.Type.SUBMITTED))
            .build();
        harness.executionStateStore().save(execution.withTaskRunList(Collections.singletonList(taskRun)));
        var workerTaskResult = WorkerTaskResult.builder()
            .taskRun(taskRun.withState(State.Type.SUCCESS))
            .build();

        harness.workerTaskResultMessageHandler().handle(workerTaskResult);
        var redelivered = harness.workerTaskResultMessageHandler().handle(workerTaskResult);

        // a redelivery of the already-joined result comes back empty and must not be billed again
        assertThat(redelivered).isEmpty();
        verify(harness.workerTaskResultListener(), times(1)).onJoined(eq(workerTaskResult), any());
    }

    @Test
    void shouldKeepProcessingWhenListenerThrows() {
        doThrow(new RuntimeException("boom")).when(harness.workerTaskResultListener()).onJoined(any(), any());
        var flow = Fixtures.flow();
        harness.registerFlow(Flows.of(flow));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        var taskRun = TaskRun.builder()
            .executionId(execution.getId())
            .namespace(execution.getNamespace())
            .flowId(execution.getFlowId())
            .id("taskrun")
            .taskId(flow.getTasks().getFirst().getId())
            .state(new State().withState(State.Type.SUBMITTED))
            .build();
        harness.executionStateStore().save(execution.withTaskRunList(Collections.singletonList(taskRun)));
        var workerTaskResult = WorkerTaskResult.builder()
            .taskRun(taskRun.withState(State.Type.SUCCESS))
            .build();

        var maybeExecutor = harness.workerTaskResultMessageHandler().handle(workerTaskResult);

        // a throwing listener must not fail execution processing
        assertThat(maybeExecutor).isPresent();
    }

    @Test
    void shouldFailTheExecutionForMissingTask() {
        var flow = Fixtures.flow();
        harness.registerFlow(Flows.of(flow));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        harness.executionStateStore().save(execution);
        var workerTaskResult = WorkerTaskResult.builder()
            .taskRun(
                TaskRun.builder()
                    .executionId(execution.getId())
                    .id("taskrun")
                    .taskId("task")
                    .build()
            )
            .build();

        var maybeExecutor = harness.workerTaskResultMessageHandler().handle(workerTaskResult);

        assertThat(maybeExecutor).isPresent();
        assertThat(maybeExecutor.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    void shouldNotApplyKillActionWhenKillSwitchPasses() {
        // PASS (harness default) → kill action never called
        var workerTaskResult = WorkerTaskResult.builder()
            .taskRun(TaskRun.builder().executionId("exec-1").id("taskrun-1").taskId("task-1").build())
            .build();

        harness.workerTaskResultMessageHandler().handle(workerTaskResult);

        verify(harness.killSwitchActionService(), never()).handle(any(), any(), any());
    }

    @Test
    void shouldReturnEmptyAndCallKillActionWhenKillSwitched() {
        var flow = Flows.of(Fixtures.flow());
        harness.registerFlow(flow);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        harness.executionStateStore().save(execution);
        var taskRun = TaskRun.builder().id("taskrun-1").executionId(execution.getId()).taskId("task-1").build();
        var workerTaskResult = WorkerTaskResult.builder().taskRun(taskRun).build();
        when(harness.killSwitchService().evaluate(any(TaskRun.class))).thenReturn(EvaluationType.IGNORE);

        Optional<ExecutorContext> result = harness.workerTaskResultMessageHandler().handle(workerTaskResult);

        assertThat(result).isEmpty();
        verify(harness.killSwitchActionService()).handle(EvaluationType.IGNORE, execution.getTenantId(), execution.getId());
        verify(harness.workerTaskResultListener(), never()).onJoined(any(), any());
    }

    @Test
    void shouldNotApplyKillActionWhenExecutionNotFound() {
        var taskRun = TaskRun.builder().id("taskrun-1").executionId("exec-missing").taskId("task-1").build();
        var workerTaskResult = WorkerTaskResult.builder().taskRun(taskRun).build();
        when(harness.killSwitchService().evaluate(any(TaskRun.class))).thenReturn(EvaluationType.IGNORE);

        harness.workerTaskResultMessageHandler().handle(workerTaskResult);

        verify(harness.killSwitchActionService(), never()).handle(any(), any(), any());
    }
}
