package org.kestra.core.schedulers;

import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.triggers.types.Schedule;
import org.kestra.runner.memory.MemoryFlowListeners;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SchedulerScheduleTest extends AbstractSchedulerTest {
    @Inject
    protected MemoryFlowListeners flowListenersService;

    @Inject
    protected SchedulerTriggerStateInterface triggerState;

    @Inject
    protected SchedulerExecutionStateInterface executionState;

    private static Flow createScheduleFlow() {
        Schedule schedule = Schedule.builder()
            .id("hourly")
            .type(Schedule.class.getName())
            .cron("0 * * * *")
            .backfill(Schedule.ScheduleBackfill.builder()
                .start(date(5))
                .build()
            )
            .build();

        return createFlow(Collections.singletonList(schedule));
    }

    private static ZonedDateTime date(int plus) {
        return ZonedDateTime.now()
            .minusHours(plus)
            .truncatedTo(ChronoUnit.HOURS);
    }

    @Test
    void schedule() throws Exception {
        // mock flow listeners
        MemoryFlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        SchedulerExecutionStateInterface executionRepositorySpy = spy(this.executionState);
        CountDownLatch queueCount = new CountDownLatch(5);

        Flow flow = createScheduleFlow();

        doReturn(Collections.singletonList(flow))
            .when(flowListenersServiceSpy)
            .flows();

        // mock the backfill execution is ended
        doAnswer(invocation -> Optional.of(Execution.builder().state(new State().withState(State.Type.SUCCESS)).build()))
            .when(executionRepositorySpy)
            .findById(any());

        // scheduler
        try (AbstractScheduler scheduler = new DefaultScheduler(
            applicationContext,
            flowListenersServiceSpy,
            executionRepositorySpy,
            triggerState
        )) {

            // wait for execution
            executionQueue.receive(SchedulerScheduleTest.class, execution -> {
                queueCount.countDown();
                assertThat(execution.getFlowId(), is(flow.getId()));
            });

            scheduler.run();
            queueCount.await(1, TimeUnit.MINUTES);
        }
    }
}
