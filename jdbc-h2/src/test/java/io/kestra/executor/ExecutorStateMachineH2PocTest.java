package io.kestra.executor;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Proof of concept for single-cycle executor state-machine testing over real H2:
 * feed events into the real DefaultExecutor one cycle at a time, and assert on the
 * persisted execution (H2) plus the downstream emit (recording queue).
 * No runner, no worker, no scheduler, no threads.
 */
@MicronautTest
@Property(name = "kestra.test.executor-state-machine", value = "true")
class ExecutorStateMachineH2PocTest {
    @Inject
    DefaultExecutor executor;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    ExecutionRepositoryInterface executionRepository;

    @Inject
    DispatchQueueInterface<ExecutionEvent> executionEventQueue;

    @Factory
    @Requires(property = "kestra.test.executor-state-machine", value = "true")
    static class RecordingQueues {
        @Singleton
        @Replaces(value = DispatchQueueInterface.class, factory = io.kestra.queue.jdbc.JdbcQueueFactory.class)
        DispatchQueueInterface<ExecutionEvent> executionEventQueue() {
            return mock(DispatchQueueInterface.class);
        }
    }

    // The H2 file is shared and never cleaned, so each test isolates on its own unique tenant to
    // avoid colliding with — or polluting — every other test; the tenant is always required.
    private static Flow flow(String tenantId) {
        return Flow.builder()
            .tenantId(tenantId)
            .namespace("io.kestra.unittest")
            .id(IdUtils.create())
            .revision(1)
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("hello").build()))
            .build();
    }

    @Test
    void shouldTerminateExecutionAcrossTwoCyclesWhenLastTaskSucceeds() throws Exception {
        // Given: a one-task flow with a RUNNING execution whose only task is SUBMITTED, persisted in H2
        String tenantId = "statemachine-" + IdUtils.create();
        Flow flow = flow(tenantId);
        flowRepository.create(GenericFlow.of(flow));

        Execution execution = Execution.newExecution(flow, Collections.emptyList())
            .withState(State.Type.RUNNING);
        TaskRun taskRun = TaskRun.builder()
            .executionId(execution.getId())
            .namespace(execution.getNamespace())
            .flowId(execution.getFlowId())
            .id("taskrun")
            .taskId(flow.getTasks().getFirst().getId())
            .state(new State().withState(State.Type.SUBMITTED))
            .build();
        executionRepository.save(execution.withTaskRunList(Collections.singletonList(taskRun)));

        WorkerTaskResult workerTaskResult = WorkerTaskResult.builder()
            .taskRun(taskRun.withState(State.Type.SUCCESS))
            .build();

        // When: cycle 1 — join the worker task result (handle + toExecution), synchronous
        executor.onWorkerTaskResult(workerTaskResult);

        // Then: the task is done but the execution is still RUNNING; the executor asked for one
        // more cycle by emitting an UPDATED event (the terminal transition happens next cycle).
        Execution afterCycle1 = executionRepository.findById(execution.getTenantId(), execution.getId()).orElseThrow();
        assertThat(afterCycle1.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(afterCycle1.getState().getCurrent()).isEqualTo(State.Type.RUNNING);

        ArgumentCaptor<ExecutionEvent> captor = ArgumentCaptor.forClass(ExecutionEvent.class);
        verify(executionEventQueue).emit(captor.capture());
        assertThat(captor.getValue().executionId()).isEqualTo(execution.getId());
        assertThat(captor.getValue().eventType()).isEqualTo(ExecutionEventType.UPDATED);

        // When: cycle 2 — feed the next event back to the executor by hand (no queue, no threads)
        executor.onExecutionEvent(new ExecutionEvent(afterCycle1, ExecutionEventType.UPDATED));

        // Then: the execution is now persisted in H2 as SUCCESS, and a TERMINATED event was emitted
        Execution afterCycle2 = executionRepository.findById(execution.getTenantId(), execution.getId()).orElseThrow();
        assertThat(afterCycle2.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        verify(executionEventQueue, times(2)).emit(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(ExecutionEventType.TERMINATED);
    }
}
