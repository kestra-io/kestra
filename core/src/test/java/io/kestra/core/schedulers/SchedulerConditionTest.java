package io.kestra.core.schedulers;

import io.kestra.core.models.conditions.types.DayWeekInMonthCondition;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.types.Schedule;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.TestMethodScopedWorker;
import io.kestra.core.runners.Worker;
import jakarta.inject.Inject;
import org.junitpioneer.jupiter.RetryingTest;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

class SchedulerConditionTest extends AbstractSchedulerTest {
    @Inject
    protected FlowListeners flowListenersService;

    @Inject
    protected SchedulerTriggerStateInterface triggerState;

    private static Flow createScheduleFlow() {
        Schedule schedule = Schedule.builder()
            .id("hourly")
            .type(Schedule.class.getName())
            .cron("0 0 * * *")
            .inputs(Map.of(
                "testInputs", "test-inputs"
            ))
            .scheduleConditions(List.of(
                DayWeekInMonthCondition.builder()
                    .type(DayWeekInMonthCondition.class.getName())
                    .date("{{ trigger.date }}")
                    .dayOfWeek(DayOfWeek.MONDAY)
                    .dayInMonth(DayWeekInMonthCondition.DayInMonth.FIRST)
                    .build()
            ))
            .build();

        return createFlow(Collections.singletonList(schedule));
    }

    @RetryingTest(10)
    void schedule() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        CountDownLatch queueCount = new CountDownLatch(4);

        Flow flow = createScheduleFlow();

        triggerState.save(Trigger.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .triggerId("hourly")
            .date(ZonedDateTime.parse("2021-09-06T02:00:00+01:00[Europe/Paris]"))
            .build()
        );

        doReturn(Collections.singletonList(flow))
            .when(flowListenersServiceSpy)
            .flows();

        // scheduler
        try (AbstractScheduler scheduler = new DefaultScheduler(
            applicationContext,
            flowListenersServiceSpy,
            triggerState);
             Worker worker = new TestMethodScopedWorker(applicationContext, 8, null)) {
            // wait for execution
            Runnable assertionStop = executionQueue.receive(SchedulerConditionTest.class, either -> {
                Execution execution = either.getLeft();
                if (execution.getState().getCurrent() == State.Type.CREATED) {
                    executionQueue.emit(execution.withState(State.Type.SUCCESS));

                    queueCount.countDown();
                    if (queueCount.getCount() == 0) {
                        assertThat(ZonedDateTime.parse((String) execution.getTrigger().getVariables().get("date")), is(ZonedDateTime.parse("2022-01-03T00:00:00+01:00")));
                    }
                }
                assertThat(execution.getFlowId(), is(flow.getId()));
            });

            worker.run();
            scheduler.run();
            queueCount.await(15, TimeUnit.SECONDS);
            // needed for RetryingTest to work since there is no context cleaning between method => we have to clear assertion receiver manually
            assertionStop.run();

            assertThat(queueCount.getCount(), is(0L));
        }
    }
}
