package io.kestra.core.schedulers;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.types.Schedule;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.TestMethodScopedWorker;
import io.kestra.core.runners.Worker;
import io.kestra.core.utils.Await;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class SchedulerScheduleTest extends AbstractSchedulerTest {
    @Inject
    protected FlowListeners flowListenersService;

    @Inject
    protected SchedulerTriggerStateInterface triggerState;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    protected QueueInterface<LogEntry> logQueue;

    private static Flow createScheduleFlow(String zone) {
        Schedule schedule = Schedule.builder()
            .id("hourlyfromschedulederschedule")
            .type(Schedule.class.getName())
            .cron("0 * * * *")
            .timezone(zone)
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

    @Test
    void schedule() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        CountDownLatch queueCount = new CountDownLatch(6);
        CountDownLatch invalidLogCount = new CountDownLatch(1);
        Set<String> date = new HashSet<>();
        Set<String> executionId = new HashSet<>();

        // Create a flow with a backfill of 5 hours
        // then flow should be executed 6 times
        Flow invalid = createScheduleFlow("Asia/Delhi");
        Flow flow = createScheduleFlow("Europe/Paris");

        doReturn(List.of(invalid, flow))
            .when(flowListenersServiceSpy)
            .flows();

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy);
             Worker worker = new TestMethodScopedWorker(applicationContext, 8, null)) {
            // wait for execution
            Runnable assertionStop = executionQueue.receive(either -> {
                Execution execution = either.getLeft();
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

            Runnable logStop = logQueue.receive(e -> {
                if (e.getLeft().getMessage().contains("Unknown time-zone ID: Asia/Delhi")) {
                    invalidLogCount.countDown();
                }
            });

            worker.run();
            scheduler.run();
            queueCount.await(1, TimeUnit.MINUTES);
            invalidLogCount.await(1, TimeUnit.MINUTES);
            // needed for RetryingTest to work since there is no context cleaning between method => we have to clear assertion receiver manually
            assertionStop.run();
            logStop.run();

            assertThat(queueCount.getCount(), is(0L));
            assertThat(date.size(), is(3));
            assertThat(executionId.size(), is(3));
        }
    }

    // Test to ensure behavior between 0.14 > 0.15
    @Test
    void retroSchedule() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);

        Flow flow = createScheduleFlow("Europe/Paris");

        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        Trigger trigger = Trigger
            .builder()
            .triggerId("hourlyfromschedulederschedule")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .date(ZonedDateTime.now())
            .build();

        triggerState.create(trigger);

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy);
             Worker worker = new TestMethodScopedWorker(applicationContext, 8, null)) {
            worker.run();
            scheduler.run();

            Await.until(() -> {
                Optional<Trigger> optionalTrigger = this.triggerState.findLast(trigger);
                return optionalTrigger.filter(value -> value.getNextExecutionDate() != null).isPresent();
            });

            assertThat(this.triggerState.findLast(trigger).get().getNextExecutionDate().isAfter(trigger.getDate()), is(true));
        }
    }
}
