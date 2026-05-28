package io.kestra.scheduler;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.scheduler.utils.InMemoryTriggerStateStore;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.noop.NoopTimer;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TriggerSchedulerMonitorTest {

    private static final int TEST_VNODE = 0;

    private MetricRegistry metricRegistry;
    private ExecutionRepositoryInterface executionRepository;
    private InMemoryTriggerStateStore triggerStateStore;
    private DefaultScheduler defaultScheduler;
    private TriggerSchedulerMonitor monitor;

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

        monitor = new TriggerSchedulerMonitor(metricRegistry, executionRepository, triggerStateStore, defaultScheduler);
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
