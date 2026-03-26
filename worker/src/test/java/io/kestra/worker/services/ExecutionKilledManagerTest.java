package io.kestra.worker.services;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.executions.ExecutionKilledTrigger;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.runners.WorkerTriggerData;

import io.micrometer.core.instrument.Counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExecutionKilledManager}.
 */
class ExecutionKilledManagerTest {

    private MetricRegistry metricRegistry;
    private ExecutionKilledManager manager;

    @BeforeEach
    void setUp() {
        metricRegistry = mock(MetricRegistry.class);
        Counter mockCounter = mock(Counter.class);
        when(metricRegistry.counter(anyString(), anyString())).thenReturn(mockCounter);

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
        manager.register("job-1", mockTask, () ->
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
        manager.register("job-1", mockTask, () -> killed.set(true));

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
        manager.register("job-1", mockTask, () -> killed.set(true));

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

        manager.register("job-1", task1, () -> killed1.set(true));
        manager.register("job-2", task2, () -> killed2.set(true));
        manager.register("job-3", task3, () -> killed3.set(true));

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
        manager.register("job-1", mockTask, () -> killed.set(true));
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
        manager.register("job-1", mockTask, () -> killed.set(true));

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
        manager.register("job-1", mockTask, () -> killed.set(true));

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
        // Given
        Counter mockCounter = mock(Counter.class);
        when(
            metricRegistry.counter(
                eq(MetricRegistry.METRIC_WORKER_KILLED_COUNT),
                eq(MetricRegistry.METRIC_WORKER_KILLED_COUNT_DESCRIPTION)
            )
        ).thenReturn(mockCounter);

        ExecutionKilledExecution killEvent = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .build();

        // When
        manager.onKillReceived(killEvent);

        // Then
        verify(mockCounter).increment();
    }

    // --- onKillReceived - ExecutionKilledTrigger ---

    @Test
    void shouldKillMatchingRunningTrigger() {
        // Given
        AtomicBoolean killed = new AtomicBoolean(false);
        WorkerTrigger mockTrigger = createMockWorkerTrigger("ns", "flow-1", "trigger-1", null);
        manager.register("job-1", mockTrigger, () -> killed.set(true));

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
        manager.register("job-1", mockTrigger, () -> killed.set(true));

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
        manager.register("job-1", mockTask, () -> killed.set(true));

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
        manager.register("job-1", mockTrigger, () -> killed.set(true));

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
        manager.register("job-1", mockTask, () -> killed.set(true));

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
        manager.register("job-1", mockTask, () ->
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

    // --- Helper methods ---

    private static WorkerTask createMockWorkerTask(String executionId, String tenantId) {
        TaskRun taskRun = mock(TaskRun.class);
        when(taskRun.getExecutionId()).thenReturn(executionId);
        when(taskRun.getTenantId()).thenReturn(tenantId);

        WorkerTask workerTask = mock(WorkerTask.class);
        when(workerTask.getTaskRun()).thenReturn(taskRun);
        when(workerTask.uid()).thenReturn("task-" + executionId);
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