package io.kestra.worker.services;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.executions.ExecutionKilledTaskRuns;
import io.kestra.core.models.executions.ExecutionKilledTrigger;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.runners.WorkerTriggerData;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExecutionKilledManager}.
 */
@MicronautTest
class ExecutionKilledManagerTest {

    @Inject
    private MetricRegistry metricRegistry;

    private ExecutionKilledManager manager;

    @BeforeEach
    void setUp() {
        manager = new ExecutionKilledManager(metricRegistry);
    }

    // --- isExecutionKilled ---

    @Test
    void shouldReturnFalseForUnknownExecution() {
        assertThat(manager.isExecutionKilled("unknown-exec-id")).isFalse();
    }

    @Test
    void shouldReturnTrueAfterKillReceived() {
        // Given
        ExecutionKilledExecution killed = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .build();

        // When
        manager.onKillReceived(killed);

        // Then
        assertThat(manager.isExecutionKilled("exec-1")).isTrue();
    }

    @Test
    void shouldReturnFalseForDifferentExecution() {
        // Given
        ExecutionKilledExecution killed = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .build();

        // When
        manager.onKillReceived(killed);

        // Then
        assertThat(manager.isExecutionKilled("exec-2")).isFalse();
    }

    @Test
    void shouldNotCacheTriggerKills() {
        // Given
        ExecutionKilledTrigger killed = ExecutionKilledTrigger.builder()
            .namespace("ns")
            .flowId("flow")
            .triggerId("trigger")
            .build();

        // When
        manager.onKillReceived(killed);

        // Then
        assertThat(manager.isExecutionKilled("ns")).isFalse();
    }

    // --- register / unregister ---

    @Test
    void shouldRegisterAndUnregisterJob() {
        // Given
        WorkerTask mockTask = createMockWorkerTask("exec-1", "tenant-1");

        // When
        manager.register("job-1", mockTask, state ->
        {
        });

        // Then
        manager.unregister("job-1");
    }

    @Test
    void shouldHandleUnregisteringUnknownJob() {
        manager.unregister("unknown-job");
    }

    // --- onKillReceived - ExecutionKilledExecution ---

    @Test
    void shouldKillMatchingRunningTask() {
        // Given
        AtomicBoolean killed = new AtomicBoolean(false);
        WorkerTask mockTask = createMockWorkerTask("exec-1", null);
        manager.register("job-1", mockTask, state -> killed.set(true));

        ExecutionKilledExecution killEvent = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .build();

        // When
        manager.onKillReceived(killEvent);

        // Then
        assertThat(killed.get()).isTrue();
    }

    @Test
    void shouldNotKillNonMatchingTask() {
        // Given
        AtomicBoolean killed = new AtomicBoolean(false);
        WorkerTask mockTask = createMockWorkerTask("exec-2", null);
        manager.register("job-1", mockTask, state -> killed.set(true));

        ExecutionKilledExecution killEvent = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .build();

        // When
        manager.onKillReceived(killEvent);

        // Then
        assertThat(killed.get()).isFalse();
    }

    @Test
    void shouldKillMultipleMatchingTasks() {
        // Given
        AtomicBoolean killed1 = new AtomicBoolean(false);
        AtomicBoolean killed2 = new AtomicBoolean(false);
        AtomicBoolean killed3 = new AtomicBoolean(false);

        WorkerTask task1 = createMockWorkerTask("exec-1", null);
        WorkerTask task2 = createMockWorkerTask("exec-1", null);
        WorkerTask task3 = createMockWorkerTask("exec-other", null);

        manager.register("job-1", task1, state -> killed1.set(true));
        manager.register("job-2", task2, state -> killed2.set(true));
        manager.register("job-3", task3, state -> killed3.set(true));

        ExecutionKilledExecution killEvent = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .build();

        // When
        manager.onKillReceived(killEvent);

        // Then
        assertThat(killed1.get()).isTrue();
        assertThat(killed2.get()).isTrue();
        assertThat(killed3.get()).isFalse();
    }

    @Test
    void shouldNotKillAlreadyUnregisteredTask() {
        // Given
        AtomicBoolean killed = new AtomicBoolean(false);
        WorkerTask mockTask = createMockWorkerTask("exec-1", null);
        manager.register("job-1", mockTask, state -> killed.set(true));
        manager.unregister("job-1");

        ExecutionKilledExecution killEvent = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .build();

        // When
        manager.onKillReceived(killEvent);

        // Then
        assertThat(killed.get()).isFalse();
    }

    @Test
    void shouldNotKillWhenTenantDoesNotMatch() {
        // Given
        AtomicBoolean killed = new AtomicBoolean(false);
        WorkerTask mockTask = createMockWorkerTask("exec-1", "tenant-A");
        manager.register("job-1", mockTask, state -> killed.set(true));

        ExecutionKilledExecution killEvent = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .tenantId("tenant-B")
            .build();

        // When
        manager.onKillReceived(killEvent);

        // Then
        assertThat(killed.get()).isFalse();
    }

    @Test
    void shouldKillWhenTenantMatches() {
        // Given
        AtomicBoolean killed = new AtomicBoolean(false);
        WorkerTask mockTask = createMockWorkerTask("exec-1", "tenant-A");
        manager.register("job-1", mockTask, state -> killed.set(true));

        ExecutionKilledExecution killEvent = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .tenantId("tenant-A")
            .build();

        // When
        manager.onKillReceived(killEvent);

        // Then
        assertThat(killed.get()).isTrue();
    }

    @Test
    void shouldIncrementMetricOnKill() {
        double count = metricRegistry.findCounter(
            MetricRegistry.METRIC_WORKER_KILLED_COUNT
        ).count();

        // Given
        ExecutionKilledExecution killEvent = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .build();

        // When
        manager.onKillReceived(killEvent);

        // Then
        double newCount = metricRegistry.findCounter(
            MetricRegistry.METRIC_WORKER_KILLED_COUNT
        ).count();
        assertThat(newCount).isEqualTo(count + 1);
    }

    // --- onKillReceived - ExecutionKilledTrigger ---

    @Test
    void shouldKillMatchingRunningTrigger() {
        // Given
        AtomicBoolean killed = new AtomicBoolean(false);
        WorkerTrigger mockTrigger = createMockWorkerTrigger("ns", "flow-1", "trigger-1", null);
        manager.register("job-1", mockTrigger, state -> killed.set(true));

        ExecutionKilledTrigger killEvent = ExecutionKilledTrigger.builder()
            .namespace("ns")
            .flowId("flow-1")
            .triggerId("trigger-1")
            .build();

        // When
        manager.onKillReceived(killEvent);

        // Then
        assertThat(killed.get()).isTrue();
    }

    @Test
    void shouldNotKillNonMatchingTrigger() {
        // Given
        AtomicBoolean killed = new AtomicBoolean(false);
        WorkerTrigger mockTrigger = createMockWorkerTrigger("ns", "flow-1", "trigger-1", null);
        manager.register("job-1", mockTrigger, state -> killed.set(true));

        ExecutionKilledTrigger killEvent = ExecutionKilledTrigger.builder()
            .namespace("ns")
            .flowId("flow-1")
            .triggerId("different-trigger")
            .build();

        // When
        manager.onKillReceived(killEvent);

        // Then
        assertThat(killed.get()).isFalse();
    }

    @Test
    void shouldNotKillTaskJobsOnTriggerKill() {
        // Given
        AtomicBoolean killed = new AtomicBoolean(false);
        WorkerTask mockTask = createMockWorkerTask("exec-1", null);
        manager.register("job-1", mockTask, state -> killed.set(true));

        ExecutionKilledTrigger killEvent = ExecutionKilledTrigger.builder()
            .namespace("ns")
            .flowId("flow-1")
            .triggerId("trigger-1")
            .build();

        // When
        manager.onKillReceived(killEvent);

        // Then
        assertThat(killed.get()).isFalse();
    }

    @Test
    void shouldNotKillTriggerJobsOnExecutionKill() {
        // Given
        AtomicBoolean killed = new AtomicBoolean(false);
        WorkerTrigger mockTrigger = createMockWorkerTrigger("ns", "flow-1", "trigger-1", null);
        manager.register("job-1", mockTrigger, state -> killed.set(true));

        ExecutionKilledExecution killEvent = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .build();

        // When
        manager.onKillReceived(killEvent);

        // Then
        assertThat(killed.get()).isFalse();
    }

    // --- Mixed scenarios ---

    @Test
    void shouldHandleKillWithNoRunningJobs() {
        // Given
        ExecutionKilledExecution killEvent = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .build();

        // When
        manager.onKillReceived(killEvent);

        // Then
        assertThat(manager.isExecutionKilled("exec-1")).isTrue();
    }

    @Test
    void shouldHandleMultipleKillsForSameExecution() {
        // Given
        AtomicBoolean killed = new AtomicBoolean(false);
        WorkerTask mockTask = createMockWorkerTask("exec-1", null);
        manager.register("job-1", mockTask, state -> killed.set(true));

        ExecutionKilledExecution killEvent = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .build();

        // When
        manager.onKillReceived(killEvent);
        assertThat(killed.get()).isTrue();

        killed.set(false);
        manager.onKillReceived(killEvent);

        // Then
        assertThat(killed.get()).isTrue();
        assertThat(manager.isExecutionKilled("exec-1")).isTrue();
    }

    @Test
    void shouldPreserveKilledStateAfterJobUnregisters() {
        // Given
        WorkerTask mockTask = createMockWorkerTask("exec-1", null);
        manager.register("job-1", mockTask, state ->
        {
        });

        ExecutionKilledExecution killEvent = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .build();
        manager.onKillReceived(killEvent);

        // When
        manager.unregister("job-1");

        // Then
        assertThat(manager.isExecutionKilled("exec-1")).isTrue();
    }

    // --- onKillReceived - ExecutionKilledTaskRuns ---

    @Test
    void shouldInterruptOnlyMatchingTaskRunsOnExecutionKilledTaskRuns() {
        // Given
        AtomicBoolean interrupted1 = new AtomicBoolean(false);
        AtomicBoolean interrupted2 = new AtomicBoolean(false);
        WorkerTask task1 = createMockWorkerTask("exec-1", null, "taskrun-1");
        WorkerTask task2 = createMockWorkerTask("exec-1", null, "taskrun-2");
        manager.register("job-1", task1, state -> interrupted1.set(true));
        manager.register("job-2", task2, state -> interrupted2.set(true));

        ExecutionKilledTaskRuns event = ExecutionKilledTaskRuns.builder()
            .executionId("exec-1")
            .taskRunIds(List.of("taskrun-1"))
            .taskRunState(State.Type.CANCELLED)
            .build();

        // When
        manager.onKillReceived(event);

        // Then
        assertThat(interrupted1.get()).isTrue();
        assertThat(interrupted2.get()).isFalse();
    }

    @Test
    void shouldPassTheConfiguredStateToTheInterruptAction() {
        // Given
        AtomicReference<State.Type> reportedState = new AtomicReference<>();
        WorkerTask task = createMockWorkerTask("exec-1", null, "taskrun-1");
        manager.register("job-1", task, reportedState::set);

        ExecutionKilledTaskRuns event = ExecutionKilledTaskRuns.builder()
            .executionId("exec-1")
            .taskRunIds(List.of("taskrun-1"))
            .taskRunState(State.Type.FAILED)
            .build();

        // When
        manager.onKillReceived(event);

        // Then
        assertThat(reportedState.get()).isEqualTo(State.Type.FAILED);
    }

    @Test
    void shouldNotAffectKilledExecutionsCacheOnTaskRunsEvent() {
        // Given
        ExecutionKilledTaskRuns event = ExecutionKilledTaskRuns.builder()
            .executionId("exec-1")
            .taskRunIds(List.of("taskrun-1"))
            .taskRunState(State.Type.CANCELLED)
            .build();

        // When
        manager.onKillReceived(event);

        // Then: an ExecutionKilledTaskRuns must never poison the whole-execution kill cache,
        // otherwise every other task of the same execution would be treated as killed too
        assertThat(manager.isExecutionKilled("exec-1")).isFalse();
    }

    @Test
    void shouldRememberPendingInterruptAndApplyOnLateRegister() {
        // Given: the interrupt arrives before the matching job is registered on this worker
        ExecutionKilledTaskRuns event = ExecutionKilledTaskRuns.builder()
            .executionId("exec-1")
            .taskRunIds(List.of("taskrun-1"))
            .taskRunState(State.Type.CANCELLED)
            .build();
        manager.onKillReceived(event);

        // When
        AtomicReference<State.Type> reportedState = new AtomicReference<>();
        WorkerTask task = createMockWorkerTask("exec-1", null, "taskrun-1");
        manager.register("job-1", task, reportedState::set);

        // Then
        assertThat(reportedState.get()).isEqualTo(State.Type.CANCELLED);
    }

    @Test
    void shouldNotReapplyPendingInterruptOnASecondRegister() {
        // Given
        ExecutionKilledTaskRuns event = ExecutionKilledTaskRuns.builder()
            .executionId("exec-1")
            .taskRunIds(List.of("taskrun-1"))
            .taskRunState(State.Type.CANCELLED)
            .build();
        manager.onKillReceived(event);

        AtomicInteger callCount = new AtomicInteger(0);
        WorkerTask task = createMockWorkerTask("exec-1", null, "taskrun-1");
        manager.register("job-1", task, state -> callCount.incrementAndGet());
        assertThat(callCount.get()).isEqualTo(1);

        // When: the same task run registers again (e.g. re-dispatched)
        manager.register("job-1", task, state -> callCount.incrementAndGet());

        // Then: the pending entry was already consumed, so this is a no-op
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void shouldKillMatchingTaskRunOnly() {
        // Given
        AtomicReference<State.Type> killedState1 = new AtomicReference<>();
        AtomicReference<State.Type> killedState2 = new AtomicReference<>();

        WorkerTask task1 = createMockWorkerTask("exec-1", null, "taskrun-1");
        WorkerTask task2 = createMockWorkerTask("exec-1", null, "taskrun-2");

        manager.register("job-1", task1, killedState1::set);
        manager.register("job-2", task2, killedState2::set);

        ExecutionKilledExecution killEvent = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .taskRunId("taskrun-1")
            .executionState(State.Type.FAILED)
            .build();

        // When
        manager.onKillReceived(killEvent);

        // Then
        assertThat(killedState1.get()).isEqualTo(State.Type.FAILED);
        assertThat(killedState2.get()).isNull();
    }

    // --- Helper methods ---

    private static final AtomicInteger TASK_RUN_ID_SEQUENCE = new AtomicInteger();

    private static WorkerTask createMockWorkerTask(String executionId, String tenantId) {
        return createMockWorkerTask(executionId, tenantId, "taskrun-" + TASK_RUN_ID_SEQUENCE.incrementAndGet());
    }

    private static WorkerTask createMockWorkerTask(String executionId, String tenantId, String taskRunId) {
        TaskRun taskRun = mock(TaskRun.class);
        when(taskRun.getId()).thenReturn(taskRunId);
        when(taskRun.getExecutionId()).thenReturn(executionId);
        when(taskRun.getTenantId()).thenReturn(tenantId);

        WorkerTask workerTask = mock(WorkerTask.class);
        when(workerTask.getTaskRun()).thenReturn(taskRun);
        when(workerTask.uid()).thenReturn("task-" + executionId + "-" + taskRunId);
        return workerTask;
    }

    private static WorkerTrigger createMockWorkerTrigger(String namespace, String flowId, String triggerId, String tenantId) {
        AbstractTrigger trigger = mock(AbstractTrigger.class);
        when(trigger.getId()).thenReturn(triggerId);

        WorkerTriggerData data = mock(WorkerTriggerData.class);
        when(data.tenantId()).thenReturn(tenantId);
        when(data.namespace()).thenReturn(namespace);
        when(data.flowId()).thenReturn(flowId);

        WorkerTrigger workerTrigger = mock(WorkerTrigger.class);
        when(workerTrigger.getTrigger()).thenReturn(trigger);
        when(workerTrigger.getData()).thenReturn(data);
        when(workerTrigger.triggerId()).thenReturn(TriggerId.of(tenantId, namespace, flowId, triggerId));
        when(workerTrigger.uid()).thenReturn("trigger-" + namespace + "-" + flowId + "-" + triggerId);
        return workerTrigger;
    }
}