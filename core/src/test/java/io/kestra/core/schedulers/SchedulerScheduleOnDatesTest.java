package io.kestra.core.schedulers;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.RecoverMissedSchedules;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.runner.JdbcScheduler;
import io.kestra.plugin.core.trigger.ScheduleOnDates;
import jakarta.inject.Inject;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class SchedulerScheduleOnDatesTest extends AbstractSchedulerTest {
    @Inject
    protected FlowListeners flowListenersService;

    @Inject
    protected SchedulerTriggerStateInterface triggerState;

    private ScheduleOnDates.ScheduleOnDatesBuilder<?, ?> createScheduleOnDatesTrigger(String zone, List<ZonedDateTime> dates, String triggerId) {
        return ScheduleOnDates.builder()
            .id(triggerId)
            .type(ScheduleOnDates.class.getName())
            .dates(Property.of(dates))
            .timezone(zone)
            .inputs(Map.of(
                "testInputs", "test-inputs"
            ));
    }

    private Flow createScheduleFlow(String zone, String triggerId) {
        var now = ZonedDateTime.now();
        var before = now.minusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        var after = now.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        var later = now.plusSeconds(2).truncatedTo(ChronoUnit.SECONDS);
        ScheduleOnDates schedule = createScheduleOnDatesTrigger(zone, List.of(before, after, later), triggerId).build();

        return createFlow(Collections.singletonList(schedule));
    }

    protected AbstractScheduler scheduler(FlowListeners flowListenersServiceSpy) {
        return new JdbcScheduler(
            applicationContext,
            flowListenersServiceSpy
        );
    }


    @Test
    void scheduleOnDates() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        CountDownLatch queueCount = new CountDownLatch(4);
        Set<String> date = new HashSet<>();
        Set<String> executionId = new HashSet<>();

        // then flow should be executed 4 times
        Flow flow = createScheduleFlow("Europe/Paris", "schedule");

        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        Trigger trigger = Trigger
            .builder()
            .triggerId("schedule")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .date(ZonedDateTime.now())
            .build();

        triggerState.create(trigger);

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy)) {
            // wait for execution
            Flux<Execution> receiveExecutions = TestsUtils.receive(executionQueue, throwConsumer(either -> {
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
            }));

            scheduler.run();
            queueCount.await(1, TimeUnit.MINUTES);
            // needed for RetryingTest to work since there is no context cleaning between method => we have to clear assertion receiver manually
            receiveExecutions.blockLast();

            assertThat(queueCount.getCount(), is(0L));
        }
    }

    @Test
    void recoverALLMissing() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        var now = ZonedDateTime.now();
        var earlier = now.minusSeconds(2).truncatedTo(ChronoUnit.SECONDS);
        var before = now.minusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        var after = now.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        var later = now.plusSeconds(2).truncatedTo(ChronoUnit.SECONDS);
        ScheduleOnDates schedule = createScheduleOnDatesTrigger(null, List.of(earlier, before, after, later), "recoverALLMissing")
            .recoverMissedSchedules(RecoverMissedSchedules.ALL)
            .build();
        Flow flow = createFlow(List.of(schedule));
        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        ZonedDateTime lastDate = ZonedDateTime.now().minusHours(3L);
        Trigger lastTrigger = Trigger
            .builder()
            .triggerId("recoverALLMissing")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .date(lastDate)
            .build();
        triggerState.create(lastTrigger);

        CountDownLatch queueCount = new CountDownLatch(1);

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy)) {
            // wait for execution
            Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
                Execution execution = either.getLeft();
                assertThat(execution.getFlowId(), is(flow.getId()));
                queueCount.countDown();
            });

            scheduler.run();

            queueCount.await(1, TimeUnit.MINUTES);
            // needed for RetryingTest to work since there is no context cleaning between method => we have to clear assertion receiver manually
            receive.blockLast();

            assertThat(queueCount.getCount(), is(0L));
            Trigger newTrigger = this.triggerState.findLast(lastTrigger).orElseThrow();
            assertThat(newTrigger.getDate().toLocalDateTime(), is(earlier.toLocalDateTime()));
            assertThat(newTrigger.getNextExecutionDate().toLocalDateTime(), is(before.toLocalDateTime()));
        }
    }

    @Test
    void recoverLASTMissing() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        var now = ZonedDateTime.now();
        var earlier = now.minusSeconds(2).truncatedTo(ChronoUnit.SECONDS);
        var before = now.minusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        var after = now.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        var later = now.plusSeconds(2).truncatedTo(ChronoUnit.SECONDS);
        ScheduleOnDates schedule = createScheduleOnDatesTrigger(null, List.of(earlier, before, after, later), "recoverLASTMissing")
            .recoverMissedSchedules(RecoverMissedSchedules.LAST)
            .build();
        Flow flow = createFlow(List.of(schedule));
        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        ZonedDateTime lastDate = ZonedDateTime.now().minusHours(3L);
        Trigger lastTrigger = Trigger
            .builder()
            .triggerId("recoverLASTMissing")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .date(lastDate)
            .build();
        triggerState.create(lastTrigger);

        CountDownLatch queueCount = new CountDownLatch(1);

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy)) {
            // wait for execution
            Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
                Execution execution = either.getLeft();
                assertThat(execution.getFlowId(), is(flow.getId()));
                queueCount.countDown();
            });

            scheduler.run();

            queueCount.await(1, TimeUnit.MINUTES);
            // needed for RetryingTest to work since there is no context cleaning between method => we have to clear assertion receiver manually
            receive.blockLast();

            assertThat(queueCount.getCount(), is(0L));
            Trigger newTrigger = this.triggerState.findLast(lastTrigger).orElseThrow();
            assertThat(newTrigger.getDate().toLocalDateTime(), is(before.toLocalDateTime()));
            assertThat(newTrigger.getNextExecutionDate().toLocalDateTime(), is(after.toLocalDateTime()));
        }
    }

    @Test
    void recoverNONEMissing() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        var now = ZonedDateTime.now();
        var before = now.minusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        var after = now.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        var later = now.plusSeconds(2).truncatedTo(ChronoUnit.SECONDS);
        ScheduleOnDates schedule = createScheduleOnDatesTrigger(null, List.of(before, after, later), "recoverNONEMissing")
            .recoverMissedSchedules(RecoverMissedSchedules.NONE)
            .build();
        Flow flow = createFlow(List.of(schedule));
        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        ZonedDateTime lastDate = ZonedDateTime.now().minusHours(3L);
        Trigger lastTrigger = Trigger
            .builder()
            .triggerId("recoverNONEMissing")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .date(lastDate)
            .build();
        triggerState.create(lastTrigger);

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy)) {
            scheduler.run();

            Await.until(() -> scheduler.isReady(), Duration.ofMillis(100), Duration.ofSeconds(5));

            Trigger newTrigger = this.triggerState.findLast(lastTrigger).orElseThrow();
            // depending on the exact timing of events, the next date can be now or after
            assertThat(newTrigger.getNextExecutionDate().toLocalDateTime().truncatedTo(ChronoUnit.SECONDS),
                oneOf(now.toLocalDateTime().truncatedTo(ChronoUnit.SECONDS), after.toLocalDateTime().truncatedTo(ChronoUnit.SECONDS)));
        }
    }
}
