package io.kestra.core.schedulers;

import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.condition.DayWeekInMonthCondition;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.plugin.core.trigger.Schedule;
import io.kestra.core.runners.FlowListeners;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

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
            .conditions(List.of(
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

    @Test
    void schedule() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        CountDownLatch queueCount = new CountDownLatch(4);

        Flow flow = createScheduleFlow();

        triggerState.create(Trigger.builder()
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
            triggerState)) {
            // wait for execution
            Flux<Execution> receive = TestsUtils.receive(executionQueue, SchedulerConditionTest.class, either -> {
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

            scheduler.run();
            queueCount.await(30, TimeUnit.SECONDS);

            receive.blockLast();

            assertThat(queueCount.getCount(), is(0L));
        }
    }
}
