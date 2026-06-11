package io.kestra.executor.handler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LoopExecutionEvent;
import io.kestra.core.models.executions.LoopRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.utils.IdUtils;
import io.kestra.executor.ExecutorContext;
import io.kestra.plugin.core.flow.Loop;
import io.kestra.plugin.core.log.Log;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@MicronautTest
class LoopExecutionEventMessageHandlerTest {
    @Inject
    private LoopExecutionEventMessageHandler handler;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private TaskOutputService taskOutputService;

    @Inject
    KillSwitchService killSwitchService;

    @MockBean(KillSwitchService.class)
    KillSwitchService killSwitchService() {
        return mock(KillSwitchService.class);
    }

    @BeforeEach
    void setUp() {
        when(killSwitchService.evaluate(anyString())).thenReturn(EvaluationType.PASS);
    }

    @Test
    void shouldReturnEmptyForNonExistingExecution() {
        // Given
        var execution = Execution.newExecution(loopFlow(), Collections.emptyList());
        var loopRun = new LoopRun(execution, "loop", "taskrun", 0, null, "a", null);
        var message = new LoopExecutionEvent(loopRun, "nonExistingExecution", State.Type.SUCCESS, null);

        // When
        var maybeExecutor = handler.handle(message);

        // Then
        assertThat(maybeExecutor).isEmpty();
    }

    @Test
    void shouldTerminateLoopWithSuccessOnLastIteration() throws InternalException {
        // Given
        var flow = flowRepository.create(GenericFlow.of(loopFlow()));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        String loopTaskRunId = IdUtils.create();
        var loopTaskRun = loopTaskRun(loopTaskRunId, execution);
        executionRepository.save(execution.withTaskRunList(List.of(loopTaskRun)));
        // terminatedIteration + 1 = 3 == iterationCount = 3 → terminates with SUCCESS
        taskOutputService.saveOutputs(loopTaskRun, Map.of(
            Loop.ITERATION_COUNT_OUTPUT, 3,
            Loop.RUNNING_ITERATIONS_OUTPUT, 1,
            Loop.TERMINATED_ITERATIONS_OUTPUT, 2
        ));

        // When
        var loopRun = new LoopRun(execution, "loop", loopTaskRunId,  2, null, "c", null);
        var message = new LoopExecutionEvent(loopRun, execution.getId(), State.Type.SUCCESS, null);
        var maybeExecutor = handler.handle(message);

        // Then
        assertThat(maybeExecutor).isPresent();
        var taskRun = maybeExecutor.get().getExecution().findTaskRunByTaskRunId(loopTaskRunId);
        assertThat(taskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    void shouldTerminateLoopImmediatelyWhenTransmitFailedIsEnabled() throws InternalException {
        // Given — transmitFailed defaults to true in Loop
        var flow = flowRepository.create(GenericFlow.of(loopFlow()));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        String loopTaskRunId = IdUtils.create();
        var loopTaskRun = loopTaskRun(loopTaskRunId, execution);
        executionRepository.save(execution.withTaskRunList(List.of(loopTaskRun)));

        // When — one iteration fails, loop should terminate immediately
        var loopRun = new LoopRun(execution, "loop", loopTaskRunId, 0, null, "a", null);
        var message = new LoopExecutionEvent(loopRun, execution.getId(), State.Type.FAILED, null);
        var maybeExecutor = handler.handle(message);

        // Then
        assertThat(maybeExecutor).isPresent();
        var taskRun = maybeExecutor.get().getExecution().findTaskRunByTaskRunId(loopTaskRunId);
        assertThat(taskRun.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    void shouldEmitNextIterationWhenMoreIterationsRemain() throws InternalException {
        // Given
        var flow = flowRepository.create(GenericFlow.of(loopFlow()));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        String loopTaskRunId = IdUtils.create();
        var loopTaskRun = loopTaskRun(loopTaskRunId, execution);
        executionRepository.save(execution.withTaskRunList(List.of(loopTaskRun)));
        // terminatedIteration + 1 = 1 < iterationCount = 3 → emit next, handler returns null
        taskOutputService.saveOutputs(loopTaskRun, Map.of(
            Loop.ITERATION_COUNT_OUTPUT, 3,
            Loop.RUNNING_ITERATIONS_OUTPUT, 1,
            Loop.TERMINATED_ITERATIONS_OUTPUT, 0
        ));

        // When
        var loopRun = new LoopRun(execution, "loop", loopTaskRunId, 0, null, "a", null);
        var message = new LoopExecutionEvent(loopRun, execution.getId(), State.Type.SUCCESS, null);
        var maybeExecutor = handler.handle(message);

        // Then — handler emits next loop execution and returns empty (null from inner lambda)
        assertThat(maybeExecutor).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenSubExecutionKillSwitched() {
        // Given — sub execution is kill-switched
        var execution = Execution.newExecution(loopFlow(), Collections.emptyList());
        var loopRun = new LoopRun(execution, "loop", "taskrun", 0, null, "a", null);
        var message = new LoopExecutionEvent(loopRun, "sub-exec-1", State.Type.SUCCESS, null);
        when(killSwitchService.evaluate("sub-exec-1")).thenReturn(EvaluationType.IGNORE);

        // When
        Optional<ExecutorContext> result = handler.handle(message);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenParentExecutionKillSwitched() {
        // Given — sub execution passes but parent is kill-switched
        var execution = Execution.newExecution(loopFlow(), Collections.emptyList());
        var loopRun = new LoopRun(execution, "loop", "taskrun", 0, null, "a", null);
        var message = new LoopExecutionEvent(loopRun, "sub-exec-1", State.Type.SUCCESS, null);
        when(killSwitchService.evaluate(execution.getId())).thenReturn(EvaluationType.IGNORE);

        // When
        Optional<ExecutorContext> result = handler.handle(message);

        // Then
        assertThat(result).isEmpty();
    }

    private Flow loopFlow() {
        var logTask = Log.builder().id("log").type(Log.class.getName()).message("Hello").build();
        var loopTask = Loop.builder()
            .id("loop")
            .type(Loop.class.getName())
            .values(List.of("a", "b", "c"))
            .tasks(List.of(logTask))
            .build();
        return Flow.builder()
            .tenantId(TestsUtils.randomTenant(this.getClass().getSimpleName()))
            .namespace("namespace")
            .id(IdUtils.create())
            .tasks(List.of(loopTask))
            .build();
    }

    private TaskRun loopTaskRun(String id, Execution execution) {
        return TaskRun.builder()
            .id(id)
            .tenantId(execution.getTenantId())
            .executionId(execution.getId())
            .namespace(execution.getNamespace())
            .flowId(execution.getFlowId())
            .taskId("loop")
            .state(new State().withState(State.Type.RUNNING))
            .attempts(List.of(
                TaskRunAttempt.builder()
                    .state(new State().withState(State.Type.RUNNING))
                    .build()
            ))
            .build();
    }
}
