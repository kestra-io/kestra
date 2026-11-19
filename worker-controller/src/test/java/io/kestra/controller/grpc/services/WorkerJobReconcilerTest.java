package io.kestra.controller.grpc.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.executor.WorkerJobRunningStateStore;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.metrics.MetricConfig;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.TransactionContext;
import io.kestra.core.runners.WorkerInstance;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerJobRunning;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskRunning;
import io.kestra.core.runners.WorkerTriggerData;
import io.kestra.core.runners.WorkerTriggerRunning;
import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.scheduler.events.TriggerWorkerLost;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.worker.StickyWorkerRouting;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkerJobReconcilerTest {

    private static final String WORKER_ID = "worker-1";

    private WorkerJobRunningStateStore store;
    private KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue;
    private TriggerEventQueue triggerEventQueue;
    private StickyWorkerRouting stickyWorkerRouting;
    private ExecutionRepositoryInterface executionRepository;
    private KillSwitchService killSwitchService;
    private SimpleMeterRegistry meterRegistry;
    private WorkerJobReconciler reconciler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        store = mock(WorkerJobRunningStateStore.class);
        workerJobEventQueue = mock(KeyedDispatchQueueInterface.class);
        triggerEventQueue = mock(TriggerEventQueue.class);
        stickyWorkerRouting = mock(StickyWorkerRouting.class);
        executionRepository = mock(ExecutionRepositoryInterface.class);
        when(executionRepository.findByIdWithoutAcl("main", "exec-1")).thenReturn(Optional.of(execution(State.Type.RUNNING)));
        killSwitchService = mock(KillSwitchService.class);
        when(killSwitchService.evaluate(any(TaskRun.class))).thenReturn(EvaluationType.PASS);
        meterRegistry = new SimpleMeterRegistry();
        reconciler = new WorkerJobReconciler(store, workerJobEventQueue, triggerEventQueue, stickyWorkerRouting, executionRepository, killSwitchService, new MetricRegistry(meterRegistry, new MetricConfig()));
    }

    @Test
    void shouldResubmitTaskSentOnEarlierStreamWhenWorkerDoesNotHoldIt() throws QueueException {
        // Given
        WorkerTaskRunning running = taskRunning("task-run-1", 1L, null);
        givenRunningEntries(running);

        // When
        reconciler.reconcile(WORKER_ID, "group-1", 2L, List.of());

        // Then
        ArgumentCaptor<WorkerJobEvent> event = ArgumentCaptor.forClass(WorkerJobEvent.class);
        verify(workerJobEventQueue).emit(eq("queue-1"), event.capture());
        assertThat(event.getValue().job().uid()).isEqualTo("task-run-1");
        assertThat(event.getValue().stickyRouting()).isNull();
        verify(store).deleteByKeyAndWorker(any(), eq("task-run-1"), eq(WORKER_ID));
        assertThat(resubmitted()).isEqualTo(1.0);
    }

    @Test
    void shouldKeepTaskWhenWorkerHoldsIt() {
        // Given
        givenRunningEntries(taskRunning("task-run-1", 1L, null));

        // When
        reconciler.reconcile(WORKER_ID, "group-1", 2L, List.of("task-run-1"));

        // Then
        verifyNoInteractions(workerJobEventQueue);
        verify(store, never()).deleteByKeyAndWorker(any(), anyString(), anyString());
        assertThat(resubmitted()).isZero();
    }

    @Test
    void shouldKeepTaskSentOnCurrentStream() {
        // Given
        givenRunningEntries(taskRunning("task-run-1", 2L, null));

        // When
        reconciler.reconcile(WORKER_ID, "group-1", 2L, List.of());

        // Then
        verifyNoInteractions(workerJobEventQueue);
        verify(store, never()).deleteByKeyAndWorker(any(), anyString(), anyString());
    }

    @Test
    void shouldSendWorkingDirectoryTaskBackToItsWorker() throws QueueException {
        // Given
        givenRunningEntries(taskRunning("task-run-1", 1L, "working-directory-1"));
        WorkerJobEvent sticky = new WorkerJobEvent("queue-1", mock(WorkerTask.class), new WorkerJobEvent.StickyRouting(WORKER_ID, "__vnode-3"));
        when(stickyWorkerRouting.route(any(), eq("queue-1"), eq(WORKER_ID))).thenReturn(sticky);

        // When
        reconciler.reconcile(WORKER_ID, "group-1", 2L, List.of());

        // Then
        verify(workerJobEventQueue).emit("__vnode-3", sticky);
    }

    @Test
    void shouldKeepEntryWhenTaskCannotBeQueuedAgain() throws QueueException {
        // Given
        givenRunningEntries(taskRunning("task-run-1", 1L, null));
        doThrow(new QueueException("unavailable")).when(workerJobEventQueue).emit(anyString(), any(WorkerJobEvent.class));

        // When
        reconciler.reconcile(WORKER_ID, "group-1", 2L, List.of());

        // Then
        verify(store, never()).deleteByKeyAndWorker(any(), anyString(), anyString());
        assertThat(resubmitted()).isZero();
    }

    @Test
    void shouldHandTriggerBackToSchedulerWhenWorkerDoesNotHoldIt() {
        // Given
        WorkerTriggerRunning running = triggerRunning(1L);
        givenRunningEntries(running);

        // When
        reconciler.reconcile(WORKER_ID, "group-1", 2L, Set.of());

        // Then
        ArgumentCaptor<TriggerEvent> event = ArgumentCaptor.forClass(TriggerEvent.class);
        verify(triggerEventQueue).send(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(TriggerWorkerLost.class, lost ->
        {
            assertThat(lost.id()).isEqualTo(TriggerId.of("main", "io.kestra.test", "flow-1", "trigger-1"));
            assertThat(lost.workerUid()).isEqualTo(WORKER_ID);
            assertThat(lost.dispatchEpoch()).isEqualTo(7L);
        });
        String uid = running.uid();
        verify(store).deleteByKeyAndWorker(any(), eq(uid), eq(WORKER_ID));
    }

    @Test
    void shouldDiscardTaskWhenItsExecutionWasKilled() {
        // Given
        givenRunningEntries(taskRunning("task-run-1", 1L, null));
        when(executionRepository.findByIdWithoutAcl("main", "exec-1")).thenReturn(Optional.of(execution(State.Type.KILLED)));

        // When
        reconciler.reconcile(WORKER_ID, "group-1", 2L, List.of());

        // Then
        verifyNoInteractions(workerJobEventQueue);
        verify(store).deleteByKeyAndWorker(any(), eq("task-run-1"), eq(WORKER_ID));
    }

    @Test
    void shouldDiscardTaskWhenItsExecutionNoLongerExists() {
        // Given
        givenRunningEntries(taskRunning("task-run-1", 1L, null));
        when(executionRepository.findByIdWithoutAcl("main", "exec-1")).thenReturn(Optional.empty());

        // When
        reconciler.reconcile(WORKER_ID, "group-1", 2L, List.of());

        // Then
        verifyNoInteractions(workerJobEventQueue);
        verify(store).deleteByKeyAndWorker(any(), eq("task-run-1"), eq(WORKER_ID));
    }

    @Test
    void shouldDiscardTaskWhenAKillSwitchApplies() {
        // Given
        givenRunningEntries(taskRunning("task-run-1", 1L, null));
        when(killSwitchService.evaluate(any(TaskRun.class))).thenReturn(EvaluationType.KILL);

        // When
        reconciler.reconcile(WORKER_ID, "group-1", 2L, List.of());

        // Then
        verifyNoInteractions(workerJobEventQueue);
        verify(store).deleteByKeyAndWorker(any(), eq("task-run-1"), eq(WORKER_ID));
    }

    @Test
    void shouldResubmitTaskWhenItsExecutionCannotBeRead() throws QueueException {
        // Given
        givenRunningEntries(taskRunning("task-run-1", 1L, null));
        when(executionRepository.findByIdWithoutAcl("main", "exec-1")).thenThrow(new IllegalStateException("database unavailable"));

        // When
        reconciler.reconcile(WORKER_ID, "group-1", 2L, List.of());

        // Then
        verify(workerJobEventQueue).emit(eq("queue-1"), any(WorkerJobEvent.class));
    }

    @Test
    void shouldNotReconcileWhenStreamHasNoGeneration() {
        // When
        reconciler.reconcile(WORKER_ID, "group-1", 0L, List.of());

        // Then
        verifyNoInteractions(store);
    }

    private double resubmitted() {
        Counter counter = meterRegistry.find(MetricRegistry.METRIC_CONTROLLER_JOB_RESUBMITTED_TOTAL)
            .tag(MetricRegistry.TAG_WORKER_GROUP, "group-1")
            .tag(MetricRegistry.TAG_WORKER_QUEUE, "queue-1")
            .counter();
        return counter == null ? 0.0 : counter.count();
    }

    @SuppressWarnings("unchecked")
    private void givenRunningEntries(WorkerJobRunning... entries) {
        doAnswer(invocation ->
        {
            BiConsumer<TransactionContext, WorkerJobRunning> consumer = invocation.getArgument(2);
            TransactionContext txContext = invocation.getArgument(0);
            for (WorkerJobRunning entry : entries) {
                consumer.accept(txContext, entry);
            }
            return null;
        }).when(store).processWorkerJobsForDeadWorker(any(), eq(WORKER_ID), any(BiConsumer.class));
    }

    private static Execution execution(State.Type state) {
        return Execution.builder()
            .id("exec-1")
            .tenantId("main")
            .namespace("io.kestra.test")
            .flowId("flow-1")
            .state(new State().withState(state))
            .build();
    }

    private static WorkerTaskRunning taskRunning(String taskRunId, long streamGeneration, String workingDirectoryId) {
        TaskRun taskRun = TaskRun.builder()
            .id(taskRunId)
            .tenantId("main")
            .namespace("io.kestra.test")
            .flowId("flow-1")
            .taskId("task-1")
            .executionId("exec-1")
            .state(new State().withState(State.Type.SUBMITTED))
            .build();
        return WorkerTaskRunning.builder()
            .taskRun(taskRun)
            .task(mock(Task.class))
            .workingDirectoryId(workingDirectoryId)
            .workerInstance(new WorkerInstance(WORKER_ID, "queue-1", streamGeneration))
            .build();
    }

    private static WorkerTriggerRunning triggerRunning(long streamGeneration) {
        AbstractTrigger trigger = mock(AbstractTrigger.class);
        when(trigger.getId()).thenReturn("trigger-1");
        return WorkerTriggerRunning.builder()
            .trigger(trigger)
            .data(new WorkerTriggerData("main", "io.kestra.test", "flow-1", null, null, null, null, Map.of(), null, Map.of()))
            .dispatchEpoch(7L)
            .workerInstance(new WorkerInstance(WORKER_ID, null, streamGeneration))
            .build();
    }
}
