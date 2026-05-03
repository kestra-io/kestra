package io.kestra.executor.handler;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LoopRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.executions.TerminatedLoopExecution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.flow.Loop;
import io.kestra.plugin.core.log.Log;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class TerminatedLoopExecutionMessageHandlerTest {
    @Inject
    private TerminatedLoopExecutionMessageHandler handler;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private TaskOutputService taskOutputService;

    @Test
    void shouldReturnEmptyForNonExistingExecution() {
        // Given
        var execution = Execution.newExecution(loopFlow(), Collections.emptyList());
        var loopRun = new LoopRun(execution, "loop", "taskrun", 0, null, "a", null);
        var message = new TerminatedLoopExecution(loopRun, "nonExistingExecution", State.Type.SUCCESS, null);

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
        var message = new TerminatedLoopExecution(loopRun, execution.getId(), State.Type.SUCCESS, null);
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
        var message = new TerminatedLoopExecution(loopRun, execution.getId(), State.Type.FAILED, null);
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
        var message = new TerminatedLoopExecution(loopRun, execution.getId(), State.Type.SUCCESS, null);
        var maybeExecutor = handler.handle(message);

        // Then — handler emits next loop execution and returns empty (null from inner lambda)
        assertThat(maybeExecutor).isEmpty();
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
