package io.kestra.core.schedulers;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.types.Schedule;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.TestMethodScopedWorker;
import io.kestra.core.runners.Worker;
import jakarta.inject.Inject;
import org.junitpioneer.jupiter.RetryingTest;
import org.junitpioneer.jupiter.RetryingTest;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

public class SchedulerScheduleTest extends AbstractSchedulerTest {
    @Inject
    protected FlowListeners flowListenersService;

    @Inject
    protected SchedulerTriggerStateInterface triggerState;

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

    protected AbstractScheduler scheduler(FlowListeners flowListenersServiceSpy) {
        return new DefaultScheduler(
            applicationContext,
            flowListenersServiceSpy,
            triggerState
        );
    }

    @RetryingTest(5)
    void schedule() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        CountDownLatch queueCount = new CountDownLatch(5);
        Set<String> date = new HashSet<>();
        Set<String> executionId = new HashSet<>();

        Flow flow = createScheduleFlow();

        doReturn(Collections.singletonList(flow))
            .when(flowListenersServiceSpy)
            .flows();

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy);
             Worker worker = new TestMethodScopedWorker(applicationContext, 8, null)) {
            // wait for execution
            Runnable assertionStop = executionQueue.receive(execution -> {
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

            worker.run();
            scheduler.run();
            queueCount.await(1, TimeUnit.MINUTES);
            // needed for RetryingTest to work since there is no context cleaning between method => we have to clear assertion receiver manually
            assertionStop.run();

            assertThat(queueCount.getCount(), is(0L));
            assertThat(date.size(), is(3));
            assertThat(executionId.size(), is(3));
        }
    }
}
