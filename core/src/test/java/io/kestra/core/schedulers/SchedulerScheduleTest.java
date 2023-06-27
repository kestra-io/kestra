package io.kestra.core.schedulers;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.types.Schedule;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.Worker;
import jakarta.inject.Inject;
import org.junitpioneer.jupiter.RetryingTest;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SchedulerScheduleTest extends AbstractSchedulerTest {
    @Inject
    protected FlowListeners flowListenersService;

    @Inject
    protected SchedulerTriggerStateInterface triggerState;

    @Inject
    protected SchedulerExecutionStateInterface executionState;

    private static Flow createScheduleFlow() {
        Schedule schedule = Schedule.builder()
            .id("hourly")
            .type(Schedule.class.getName())
            .cron("0 * * * *")
            .inputs(Map.of(
                "testInputs", "test-inputs"
            ))
            .backfill(Schedule.ScheduleBackfill.builder()
                .start(date(5))
                .build()
            )
            .build();

        return createFlow(Collections.singletonList(schedule));
    }

    private static ZonedDateTime date(int minus) {
        return ZonedDateTime.now()
            .minusHours(minus)
            .truncatedTo(ChronoUnit.HOURS);
    }

    protected AbstractScheduler scheduler(FlowListeners flowListenersServiceSpy, SchedulerExecutionStateInterface executionStateSpy) {
        return new DefaultScheduler(
            applicationContext,
            flowListenersServiceSpy,
            executionStateSpy,
            triggerState
        );
    }

    @RetryingTest(5)
    void schedule() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        SchedulerExecutionStateInterface executionStateSpy = spy(this.executionState);
        CountDownLatch queueCount = new CountDownLatch(5);
        Set<String> date = new HashSet<>();
        Set<String> executionId = new HashSet<>();

        Flow flow = createScheduleFlow();

        doReturn(Collections.singletonList(flow))
            .when(flowListenersServiceSpy)
            .flows();

        // mock the backfill execution is ended
        doAnswer(invocation -> Optional.of(Execution.builder().state(new State().withState(State.Type.SUCCESS)).build()))
            .when(executionStateSpy)
            .findById(any());

        // start the worker as it execute polling triggers
        Worker worker = new Worker(applicationContext, 8, null);
        worker.run();

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy, executionStateSpy)) {
            // wait for execution
            executionQueue.receive(execution -> {
                assertThat(execution.getInputs().get("testInputs"), is("test-inputs"));
                assertThat(execution.getInputs().get("def"), is("awesome"));

                date.add((String) execution.getTrigger().getVariables().get("date"));
                executionId.add(execution.getId());

                queueCount.countDown();
                if (execution.getState().getCurrent() == State.Type.CREATED) {
                    executionQueue.emit(execution.withState(State.Type.SUCCESS));
                }
                assertThat(execution.getFlowId(), is(flow.getId()));
            });

            scheduler.run();
            queueCount.await(1, TimeUnit.MINUTES);

            assertThat(queueCount.getCount(), is(0L));
            assertThat(date.size(), is(3));
            assertThat(executionId.size(), is(3));
        }
    }
}
