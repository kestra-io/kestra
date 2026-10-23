package io.kestra.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.scheduler.events.TriggerExecutionDeleted;
import io.kestra.core.scheduler.events.TriggerExecutionTerminated;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.server.Service.ServiceState;
import io.kestra.scheduler.utils.InMemoryTriggerStateStore;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.micronaut.context.BeanProvider;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TriggerSchedulerMonitorTest {

    private static final int TEST_VNODE = 0;
    private static final Duration RECOVERY_DELAY = Duration.ofMinutes(5);

    private TriggerEventQueue triggerEventQueue;
    private MetricRegistry metricRegistry;
    private ExecutionRepositoryInterface executionRepository;
    private InMemoryTriggerStateStore triggerStateStore;
    private DefaultScheduler defaultScheduler;
    private BeanProvider<DefaultScheduler> schedulerProvider;
    private TriggerSchedulerMonitor monitor;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        metricRegistry = mock(MetricRegistry.class);
        when(metricRegistry.tags(any(TriggerId.class))).thenReturn(new String[0]);
        when(metricRegistry.timer(anyString(), anyString())).thenReturn(new NoopTimer(mock(Meter.Id.class)));

        executionRepository = mock(ExecutionRepositoryInterface.class);
        when(executionRepository.findAllByTrigger(any())).thenReturn(Flux.empty());

        triggerStateStore = new InMemoryTriggerStateStore();

        defaultScheduler = mock(DefaultScheduler.class);
        when(defaultScheduler.currentVNodesAssignment()).thenReturn(Set.of(TEST_VNODE));
        when(defaultScheduler.getState()).thenReturn(ServiceState.RUNNING);

        schedulerProvider = mock(BeanProvider.class);
        when(schedulerProvider.get()).thenReturn(defaultScheduler);

        triggerEventQueue = mock(TriggerEventQueue.class);
        when(metricRegistry.counter(anyString(), anyString(), any(String[].class))).thenReturn(mock(Counter.class));

        monitor = monitorWithRecoveryDelay(null);
    }

    private TriggerSchedulerMonitor monitorWithRecoveryDelay(Duration recoveryDelay) {
        return new TriggerSchedulerMonitor(
            metricRegistry, executionRepository, triggerStateStore, schedulerProvider, triggerEventQueue, Optional.ofNullable(recoveryDelay)
        );
    }

    @Test
    void shouldResendTerminationWhenLockingExecutionTerminatedBeforeTheRecoveryDelay() {
        // Given a trigger still locked by an execution that ended an hour ago
        triggerStateStore.save(lockedTriggerOf("schedule-trigger", TriggerType.SCHEDULE, "exec-1"));
        Instant endDate = Instant.now().minus(Duration.ofHours(1));
        when(executionRepository.findByIdWithoutAcl("tenant", "exec-1")).thenReturn(Optional.of(execution("exec-1", State.Type.SUCCESS, endDate)));

        // When
        monitorWithRecoveryDelay(RECOVERY_DELAY).run();

        // Then the lost termination is sent again, fenced by the locking execution id
        ArgumentCaptor<TriggerEvent> event = ArgumentCaptor.forClass(TriggerEvent.class);
        verify(triggerEventQueue).send(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(TriggerExecutionTerminated.class, terminated -> {
            assertThat(terminated.executionId()).isEqualTo("exec-1");
            assertThat(terminated.executionState()).isEqualTo(State.Type.SUCCESS);
        });
    }

    @Test
    void shouldReleaseLockWhenLockingExecutionNoLongerExists() {
        // Given a trigger locked for longer than the recovery delay by an execution that cannot be found
        triggerStateStore.save(lockedTriggerOf("schedule-trigger", TriggerType.SCHEDULE, "exec-1", Instant.now().minus(Duration.ofHours(1))));
        when(executionRepository.findByIdWithoutAcl("tenant", "exec-1")).thenReturn(Optional.empty());

        // When
        monitorWithRecoveryDelay(RECOVERY_DELAY).run();

        // Then
        ArgumentCaptor<TriggerEvent> event = ArgumentCaptor.forClass(TriggerEvent.class);
        verify(triggerEventQueue).send(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(
            TriggerExecutionDeleted.class, deleted -> assertThat(deleted.executionId()).isEqualTo("exec-1")
        );
    }

    @Test
    void shouldKeepLockWhenRecoveryIsNotConfigured() {
        // Given one trigger locked by a long-terminated execution and one by a missing execution
        triggerStateStore.save(lockedTriggerOf("terminated-trigger", TriggerType.SCHEDULE, "exec-1"));
        triggerStateStore.save(lockedTriggerOf("missing-trigger", TriggerType.SCHEDULE, "exec-2", Instant.now().minus(Duration.ofDays(1))));
        when(executionRepository.findByIdWithoutAcl("tenant", "exec-1"))
            .thenReturn(Optional.of(execution("exec-1", State.Type.SUCCESS, Instant.now().minus(Duration.ofDays(1)))));
        when(executionRepository.findByIdWithoutAcl("tenant", "exec-2")).thenReturn(Optional.empty());

        // When the monitor runs without a recovery delay
        monitor.run();

        // Then nothing is released
        verify(triggerEventQueue, never()).send(any());
    }

    @Test
    void shouldKeepLockWhenLockingExecutionTerminatedWithinTheRecoveryDelay() {
        // Given an execution that just ended: the Executor's own termination may still be in flight
        triggerStateStore.save(lockedTriggerOf("schedule-trigger", TriggerType.SCHEDULE, "exec-1"));
        when(executionRepository.findByIdWithoutAcl("tenant", "exec-1"))
            .thenReturn(Optional.of(execution("exec-1", State.Type.KILLED, Instant.now().minusSeconds(5))));

        // When
        monitorWithRecoveryDelay(RECOVERY_DELAY).run();

        // Then
        verify(triggerEventQueue, never()).send(any());
    }

    @Test
    void shouldSkipRealtimeTriggersWhenMonitoring() {
        // Given a locked realtime trigger that would otherwise be reported as blocked indefinitely.
        triggerStateStore.save(lockedTriggerOf("realtime-trigger", TriggerType.REALTIME));

        // When the monitor runs
        monitor.run();

        // Then the realtime trigger is never inspected for executions and produces no metrics.
        verify(executionRepository, never()).findAllByTrigger(any());
        verify(metricRegistry, never()).timer(anyString(), anyString());
    }

    @Test
    void shouldStillMonitorScheduleAndPollingTriggersWhenRealtimeAreMixedIn() {
        // Given a mix of locked triggers across types.
        triggerStateStore.save(lockedTriggerOf("schedule-trigger", TriggerType.SCHEDULE));
        triggerStateStore.save(lockedTriggerOf("polling-trigger", TriggerType.POLLING));
        triggerStateStore.save(lockedTriggerOf("realtime-trigger", TriggerType.REALTIME));

        // When the monitor runs
        monitor.run();

        // Then findAllByTrigger is invoked once per non-realtime trigger.
        verify(executionRepository, times(2)).findAllByTrigger(any());
    }

    @Test
    void shouldNotPropagateWhenSchedulerFailsToConstruct() {
        // Given a scheduler that fails to construct (e.g. a dependency's @PostConstruct throwing).
        when(schedulerProvider.get()).thenThrow(new RuntimeException("boom"));
        triggerStateStore.save(lockedTriggerOf("schedule-trigger", TriggerType.SCHEDULE));

        // When the monitor runs, the failure is caught locally instead of propagating to the caller.
        monitor.run();

        // Then no trigger is inspected.
        verify(executionRepository, never()).findAllByTrigger(any());
    }

    @Test
    void shouldSkipMonitoringWhenSchedulerIsNotRunning() {
        // Given a constructed scheduler that is not currently running.
        when(defaultScheduler.getState()).thenReturn(ServiceState.TERMINATING);
        triggerStateStore.save(lockedTriggerOf("schedule-trigger", TriggerType.SCHEDULE));

        // When the monitor runs
        monitor.run();

        // Then no trigger is inspected.
        verify(executionRepository, never()).findAllByTrigger(any());
    }

    private static TriggerState lockedTriggerOf(String triggerId, TriggerType type, String executionId) {
        return lockedTriggerOf(triggerId, type, executionId, Instant.now().minus(Duration.ofHours(2)));
    }

    private static TriggerState lockedTriggerOf(String triggerId, TriggerType type, String executionId, Instant lockedAt) {
        return TriggerState.builder()
            .tenantId("tenant")
            .namespace("io.kestra.unittest")
            .flowId("flow")
            .triggerId(triggerId)
            .updatedAt(lockedAt)
            .nextEvaluationDate(Instant.now().minusSeconds(60))
            .vnode(TEST_VNODE)
            .locked(true)
            .executionId(executionId)
            .type(type)
            .stopAfter(List.of())
            .build();
    }

    private static Execution execution(String id, State.Type current, Instant endDate) {
        Instant startDate = endDate.minusSeconds(60);
        return Execution.builder()
            .id(id)
            .tenantId("tenant")
            .state(State.of(current, List.of(
                new State.History(State.Type.CREATED, startDate),
                new State.History(State.Type.RUNNING, startDate),
                new State.History(current, endDate)
            )))
            .build();
    }

    private static TriggerState lockedTriggerOf(String triggerId, TriggerType type) {
        return TriggerState.builder()
            .tenantId("tenant")
            .namespace("io.kestra.unittest")
            .flowId("flow")
            .triggerId(triggerId)
            .updatedAt(Instant.now().minusSeconds(120))
            .nextEvaluationDate(Instant.now().minusSeconds(60))
            .vnode(TEST_VNODE)
            .locked(true)
            .type(type)
            .stopAfter(List.of())
            .build();
    }
}
