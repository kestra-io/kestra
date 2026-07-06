package io.kestra.executor.handler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.LoopExecutionEvent;
import io.kestra.core.models.executions.LoopRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.namespace.NamespaceFileMetadataStateStore;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.services.configuration.TaskOutputConfiguration;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.plugin.core.flow.Loop;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoopExecutionEventMessageHandlerTest {
    private ExecutorTestHarness harness;
    private TaskOutputService taskOutputService;

    @BeforeEach
    void setUp() {
        harness = ExecutorTestHarness.create();
        // backed by the harness's task output repository so seeded outputs are visible to the handler
        taskOutputService = new TaskOutputService(
            harness.taskOutputRepository(),
            mock(StorageInterface.class),
            new NamespaceFactory(mock(NamespaceFileMetadataStateStore.class)),
            new TaskOutputConfiguration(-1)
        );
    }

    @Test
    void shouldReturnEmptyForNonExistingExecution() {
        // Given
        var execution = Execution.newExecution(loopFlow(), Collections.emptyList());
        var loopRun = new LoopRun(execution, "loop", "taskrun", 0, null, "a", null);
        var message = new LoopExecutionEvent(loopRun, "nonExistingExecution", State.Type.SUCCESS, null);

        // When
        var maybeExecutor = harness.loopExecutionEventMessageHandler().handle(message);

        // Then
        assertThat(maybeExecutor).isEmpty();
    }

    @Test
    void shouldTerminateLoopWithSuccessOnLastIteration() throws InternalException {
        // Given
        var flow = Flows.of(loopFlow());
        harness.registerFlow(flow);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        String loopTaskRunId = IdUtils.create();
        var loopTaskRun = loopTaskRun(loopTaskRunId, execution);
        harness.executionStateStore().save(execution.withTaskRunList(List.of(loopTaskRun)));
        // terminatedIteration + 1 = 3 == iterationCount = 3 → terminates with SUCCESS
        taskOutputService.saveOutputs(
            loopTaskRun, Map.of(
                Loop.ITERATION_COUNT_OUTPUT, 3,
                Loop.RUNNING_ITERATIONS_OUTPUT, 1,
                Loop.TERMINATED_ITERATIONS_OUTPUT, Map.of("SUCCESS", 2)
            )
        );

        // When
        var loopRun = new LoopRun(execution, "loop", loopTaskRunId, 2, null, "c", null);
        var message = new LoopExecutionEvent(loopRun, execution.getId(), State.Type.SUCCESS, null);
        var maybeExecutor = harness.loopExecutionEventMessageHandler().handle(message);

        // Then
        assertThat(maybeExecutor).isPresent();
        var taskRun = maybeExecutor.get().getExecution().findTaskRunByTaskRunId(loopTaskRunId);
        assertThat(taskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, Map.of("SUCCESS", 3));
        // no next iteration is started
        assertThat(harness.executionQueue().emitted()).isEmpty();
    }

    @Test
    void shouldTerminateLoopImmediatelyWhenTransmitFailedIsEnabled() throws InternalException {
        // Given — transmitFailed defaults to true in Loop
        var flow = Flows.of(loopFlow());
        harness.registerFlow(flow);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        String loopTaskRunId = IdUtils.create();
        var loopTaskRun = loopTaskRun(loopTaskRunId, execution);
        harness.executionStateStore().save(execution.withTaskRunList(List.of(loopTaskRun)));

        // When — one iteration fails, loop should terminate immediately
        var loopRun = new LoopRun(execution, "loop", loopTaskRunId, 0, null, "a", null);
        var message = new LoopExecutionEvent(loopRun, "sub-execution-id", State.Type.FAILED, null);
        var maybeExecutor = harness.loopExecutionEventMessageHandler().handle(message);

        // Then
        assertThat(maybeExecutor).isPresent();
        var taskRun = maybeExecutor.get().getExecution().findTaskRunByTaskRunId(loopTaskRunId);
        assertThat(taskRun.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        // no next iteration is started
        assertThat(harness.executionQueue().emitted()).isEmpty();

        // the iteration failure is surfaced as an ERROR log on the parent execution
        var errorLog = harness.logs().stream()
            .filter(log -> Level.ERROR.equals(log.getLevel()))
            .findFirst()
            .orElseThrow();
        assertThat(errorLog.getExecutionId()).isEqualTo(execution.getId());
        assertThat(errorLog.getTaskRunId()).isEqualTo(loopTaskRunId);
        assertThat(errorLog.getMessage()).contains("sub-execution-id").contains("FAILED");
    }

    @Test
    void shouldEmitNextIterationWhenMoreIterationsRemain() throws InternalException {
        // Given
        var flow = Flows.of(loopFlow());
        harness.registerFlow(flow);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        String loopTaskRunId = IdUtils.create();
        var loopTaskRun = loopTaskRun(loopTaskRunId, execution);
        harness.executionStateStore().save(execution.withTaskRunList(List.of(loopTaskRun)));
        // terminatedIteration + 1 = 1 < iterationCount = 3 → emit next, handler returns null
        taskOutputService.saveOutputs(
            loopTaskRun, Map.of(
                Loop.ITERATION_COUNT_OUTPUT, 3,
                Loop.RUNNING_ITERATIONS_OUTPUT, 1,
                Loop.TERMINATED_ITERATIONS_OUTPUT, Collections.emptyMap()
            )
        );

        // When
        var loopRun = new LoopRun(execution, "loop", loopTaskRunId, 0, null, "a", null);
        var message = new LoopExecutionEvent(loopRun, execution.getId(), State.Type.SUCCESS, null);
        var maybeExecutor = harness.loopExecutionEventMessageHandler().handle(message);

        // Then — handler emits the next loop iteration on the execution queue and returns empty (null from inner lambda)
        assertThat(maybeExecutor).isEmpty();
        assertThat(harness.executionQueue().emitted()).hasSize(1);
        var nextIteration = harness.executionQueue().emitted().getFirst();
        assertThat(nextIteration.getKind()).isEqualTo(ExecutionKind.LOOP);
        assertThat(nextIteration.getLoopRun().parent().getId()).isEqualTo(execution.getId());
        assertThat(nextIteration.getLoopRun().taskRunId()).isEqualTo(loopTaskRunId);
        assertThat(nextIteration.getLoopRun().index()).isEqualTo(1);
        assertThat(nextIteration.getLoopRun().value()).isEqualTo("b");
        // the loop is still running: the UI is refreshed through a follow execution event
        assertThat(harness.followExecutionEventQueue().emitted()).hasSize(1);
    }

    @Test
    void shouldAccumulateTerminatedIterationsPerStateWhenTransmitFailedIsDisabled() throws InternalException {
        // Given — transmitFailed disabled: a failing iteration is counted per-state instead of
        // immediately terminating the loop
        var flow = Flows.of(loopFlow(false));
        harness.registerFlow(flow);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        String loopTaskRunId = IdUtils.create();
        var loopTaskRun = loopTaskRun(loopTaskRunId, execution);
        harness.executionStateStore().save(execution.withTaskRunList(List.of(loopTaskRun)));
        // one iteration already succeeded
        taskOutputService.saveOutputs(
            loopTaskRun, Map.of(
                Loop.ITERATION_COUNT_OUTPUT, 3,
                Loop.RUNNING_ITERATIONS_OUTPUT, 1,
                Loop.TERMINATED_ITERATIONS_OUTPUT, Map.of("SUCCESS", 1)
            )
        );

        // When — the second iteration fails
        var loopRun = new LoopRun(execution, "loop", loopTaskRunId, 1, null, "b", null);
        var message = new LoopExecutionEvent(loopRun, execution.getId(), State.Type.FAILED, null);
        var maybeExecutor = harness.loopExecutionEventMessageHandler().handle(message);

        // Then — the loop keeps running (emits the last iteration) and records both terminal states
        assertThat(maybeExecutor).isEmpty();
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, Map.of("SUCCESS", 1, "FAILED", 1));
    }

    @Test
    void shouldReturnEmptyWhenSubExecutionKillSwitched() {
        // Given — sub execution is kill-switched
        var execution = Execution.newExecution(loopFlow(), Collections.emptyList());
        var loopRun = new LoopRun(execution, "loop", "taskrun", 0, null, "a", null);
        var message = new LoopExecutionEvent(loopRun, "sub-exec-1", State.Type.SUCCESS, null);
        when(harness.killSwitchService().evaluate("sub-exec-1")).thenReturn(EvaluationType.IGNORE);

        // When
        Optional<ExecutorContext> result = harness.loopExecutionEventMessageHandler().handle(message);

        // Then
        assertThat(result).isEmpty();
        assertThat(harness.executionQueue().emitted()).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenParentExecutionKillSwitched() {
        // Given — sub execution passes but parent is kill-switched
        var execution = Execution.newExecution(loopFlow(), Collections.emptyList());
        var loopRun = new LoopRun(execution, "loop", "taskrun", 0, null, "a", null);
        var message = new LoopExecutionEvent(loopRun, "sub-exec-1", State.Type.SUCCESS, null);
        when(harness.killSwitchService().evaluate(execution.getId())).thenReturn(EvaluationType.IGNORE);

        // When
        Optional<ExecutorContext> result = harness.loopExecutionEventMessageHandler().handle(message);

        // Then
        assertThat(result).isEmpty();
        assertThat(harness.executionQueue().emitted()).isEmpty();
    }

    private Flow loopFlow() {
        return loopFlow(true);
    }

    private Flow loopFlow(boolean transmitFailed) {
        var logTask = Log.builder().id("log").type(Log.class.getName()).message("Hello").build();
        var loopTask = Loop.builder()
            .id("loop")
            .type(Loop.class.getName())
            .values(List.of("a", "b", "c"))
            .tasks(List.of(logTask))
            .transmitFailed(transmitFailed)
            .build();
        return Flow.builder()
            .tenantId("tenant")
            .namespace("namespace")
            .id(IdUtils.create())
            .revision(1)
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
            .attempts(
                List.of(
                    TaskRunAttempt.builder()
                        .state(new State().withState(State.Type.RUNNING))
                        .build()
                )
            )
            .build();
    }
}
