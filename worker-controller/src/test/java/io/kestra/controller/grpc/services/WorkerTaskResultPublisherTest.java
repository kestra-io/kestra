package io.kestra.controller.grpc.services;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.executor.WorkerJobRunningStateStore;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.runners.RunContextLoggerFactory;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkerTaskResultPublisherTest {
    @SuppressWarnings("unchecked")
    private final DispatchQueueInterface<WorkerTaskResult> queue = mock(DispatchQueueInterface.class);
    private final WorkerJobRunningStateStore store = mock(WorkerJobRunningStateStore.class);
    private final RunContextLoggerFactory loggerFactory = mock(RunContextLoggerFactory.class);
    private final WorkerTaskResultPublisher publisher = new WorkerTaskResultPublisher(queue, store, loggerFactory);

    @Test
    void shouldEmitOneBatchAndReleaseOnlyTheTerminatedTaskRunsWhenPublishingSeveralResults() throws QueueException {
        WorkerTaskResult running = result(State.Type.RUNNING);
        WorkerTaskResult success = result(State.Type.SUCCESS);
        WorkerTaskResult failed = result(State.Type.FAILED);

        publisher.publish(List.of(running, success, failed));

        verify(queue).emit(List.of(running, success, failed));
        verify(queue, never()).emit(any(WorkerTaskResult.class));
        verify(store).deleteByKeys(List.of(success.getTaskRun().getId(), failed.getTaskRun().getId()));
        verify(store, never()).deleteByKey(anyString());
    }

    @Test
    void shouldFallBackToOneEmitPerResultWhenTheBatchFails() throws QueueException {
        WorkerTaskResult running = result(State.Type.RUNNING);
        WorkerTaskResult success = result(State.Type.SUCCESS);
        doThrow(new QueueException("Unable to emit a message to the queue")).when(queue).emit(anyList());
        when(loggerFactory.create(any(WorkerTaskResult.class))).thenReturn(mock(RunContextLogger.class, RETURNS_DEEP_STUBS));

        publisher.publish(List.of(running, success));

        verify(queue).emit(running);
        verify(queue).emit(success);
        verify(store).deleteByKey(success.getTaskRun().getId());
        verify(store, never()).deleteByKeys(anyList());
    }

    private static WorkerTaskResult result(State.Type state) {
        return new WorkerTaskResult(
            TaskRun.builder()
                .id(IdUtils.create())
                .executionId(IdUtils.create())
                .namespace("io.kestra.unittest")
                .flowId("worker-task-result-publisher")
                .taskId("log")
                .state(new State().withState(state))
                .build()
        );
    }
}
