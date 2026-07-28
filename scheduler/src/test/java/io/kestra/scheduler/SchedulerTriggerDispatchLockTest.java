package io.kestra.scheduler;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.SchedulerTriggerStateInterface;
import io.kestra.core.runners.WorkerGroupExecutorInterface;
import io.kestra.core.services.WorkerGroupService;
import io.kestra.core.tasks.test.PollingTrigger;
import io.kestra.core.utils.Await;
import io.kestra.jdbc.runner.JdbcScheduler;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Verifies that the scheduler only takes a polling trigger's evaluation lock when the worker job was
 * actually dispatched. When dispatch is a no-op (here: the resolved worker group does not exist), the
 * trigger must stay unlocked (evaluateRunningDate == null) so it remains eligible for scheduling
 * instead of being silently stuck forever.
 */
public class SchedulerTriggerDispatchLockTest extends AbstractSchedulerTest {
    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    private FlowListeners flowListenersService;

    @Inject
    private SchedulerTriggerStateInterface schedulerTriggerState;

    @MockBean(WorkerGroupService.class)
    WorkerGroupService workerGroupService() {
        WorkerGroupService mockService = mock(WorkerGroupService.class);
        when(mockService.resolveGroupFromJob(any(), any())).thenReturn(Optional.of(new WorkerGroup("missing-group", null)));
        when(mockService.resolveGroupFromKey(any())).thenReturn(null);
        return mockService;
    }

    @MockBean(WorkerGroupExecutorInterface.class)
    WorkerGroupExecutorInterface workerGroupExecutorInterface() {
        WorkerGroupExecutorInterface mockExecutor = mock(WorkerGroupExecutorInterface.class);
        // the resolved worker group does not exist -> the scheduler cannot dispatch the trigger job
        when(mockExecutor.isWorkerGroupExistForKey(any(), any())).thenReturn(false);
        when(mockExecutor.isWorkerGroupAvailableForKey(any())).thenReturn(false);
        when(mockExecutor.listAllWorkerGroupKeys()).thenReturn(Set.of());
        return mockExecutor;
    }

    @Test
    void shouldNotLockTriggerWhenWorkerJobNotDispatched() throws Exception {
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        PollingTrigger pollingTrigger = PollingTrigger.builder()
            .id("polling-trigger")
            .type(PollingTrigger.class.getName())
            .duration(500L)
            .build();
        Flow flow = createFlow(Collections.singletonList(pollingTrigger));
        doReturn(List.of(flow)).when(flowListenersServiceSpy).flows();

        try (AbstractScheduler scheduler = new JdbcScheduler(applicationContext, flowListenersServiceSpy)) {
            scheduler.run();

            Trigger trigger = Trigger.of(flow, pollingTrigger);

            // wait until the scheduler has created & started evaluating the trigger
            Await.until(() -> schedulerTriggerState.findLast(trigger).isPresent(), Duration.ofMillis(100), Duration.ofSeconds(10));

            // Across several evaluation ticks the trigger must never acquire the evaluation lock, because
            // the worker job is never dispatched. Before the fix, the lock was persisted on the first tick
            // and never released, silently stalling the schedule.
            for (int i = 0; i < 15; i++) {
                assertThat(schedulerTriggerState.findLast(trigger))
                    .get()
                    .extracting(Trigger::getEvaluateRunningDate)
                    .isNull();
                Thread.sleep(200);
            }
        }
    }
}
