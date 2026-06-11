package io.kestra.executor.handler;

import java.util.Collections;
import java.util.Optional;

import io.micronaut.test.annotation.MockBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.KillSwitchActionService;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@KestraTest
class WorkerTaskResultMessageHandlerTest {
    @Inject
    private WorkerTaskResultMessageHandler workerTaskResultMessageHandler;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    KillSwitchService killSwitchService;

    @Inject
    KillSwitchActionService killSwitchActionService;

    @MockBean(KillSwitchService.class)
    KillSwitchService killSwitchService() {
        return mock(KillSwitchService.class);
    }

    @MockBean(KillSwitchActionService.class)
    KillSwitchActionService killSwitchActionService() {
        return mock(KillSwitchActionService.class);
    }

    @BeforeEach
    void setUp() {
        when(killSwitchService.evaluate(any(TaskRun.class))).thenReturn(EvaluationType.PASS);
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

        var maybeExecutor = workerTaskResultMessageHandler.handle(workerTaskResult);

        assertThat(maybeExecutor).isEmpty();
    }

    @Test
    void shouldReturnAnExecutorForExistingExecution() {
        var flow = Fixtures.flow();
        flowRepository.create(GenericFlow.of(flow));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        var taskRun = TaskRun.builder()
            .executionId(execution.getId())
            .namespace(execution.getNamespace())
            .flowId(execution.getFlowId())
            .id("taskrun")
            .taskId(flow.getTasks().getFirst().getId())
            .state(new State().withState(State.Type.SUBMITTED))
            .build();
        executionRepository.save(execution.withTaskRunList(Collections.singletonList(taskRun)));
        var workerTaskResult = WorkerTaskResult.builder()
            .taskRun(taskRun.withState(State.Type.SUCCESS))
            .build();

        var maybeExecutor = workerTaskResultMessageHandler.handle(workerTaskResult);

        assertThat(maybeExecutor).isPresent();
        assertThat(maybeExecutor.get().getExecution().getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    void shouldFailTheExecutionForMissingTask() {
        var flow = Fixtures.flow();
        flowRepository.create(GenericFlow.of(flow));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var workerTaskResult = WorkerTaskResult.builder()
            .taskRun(
                TaskRun.builder()
                    .executionId(execution.getId())
                    .id("taskrun")
                    .taskId("task")
                    .build()
            )
            .build();

        var maybeExecutor = workerTaskResultMessageHandler.handle(workerTaskResult);

        assertThat(maybeExecutor).isPresent();
        assertThat(maybeExecutor.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    void shouldNotApplyKillActionWhenKillSwitchPasses() {
        // PASS (default from setUp) → kill action never called
        var workerTaskResult = WorkerTaskResult.builder()
            .taskRun(TaskRun.builder().executionId("exec-1").id("taskrun-1").taskId("task-1").build())
            .build();

        workerTaskResultMessageHandler.handle(workerTaskResult);

        verify(killSwitchActionService, never()).handle(any(), any(), any());
    }

    @Test
    void shouldReturnEmptyAndCallKillActionWhenKillSwitched() {
        var flow = flowRepository.create(GenericFlow.of(Fixtures.flow()));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var taskRun = TaskRun.builder().id("taskrun-1").executionId(execution.getId()).taskId("task-1").build();
        var workerTaskResult = WorkerTaskResult.builder().taskRun(taskRun).build();
        when(killSwitchService.evaluate(any(TaskRun.class))).thenReturn(EvaluationType.IGNORE);

        Optional<ExecutorContext> result = workerTaskResultMessageHandler.handle(workerTaskResult);

        assertThat(result).isEmpty();
        verify(killSwitchActionService).handle(EvaluationType.IGNORE, execution.getTenantId(), execution.getId());
    }

    @Test
    void shouldNotApplyKillActionWhenExecutionNotFound() {
        var taskRun = TaskRun.builder().id("taskrun-1").executionId("exec-missing").taskId("task-1").build();
        var workerTaskResult = WorkerTaskResult.builder().taskRun(taskRun).build();
        when(killSwitchService.evaluate(any(TaskRun.class))).thenReturn(EvaluationType.IGNORE);

        workerTaskResultMessageHandler.handle(workerTaskResult);

        verify(killSwitchActionService, never()).handle(any(), any(), any());
    }
}
