package io.kestra.core.schedulers;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.TestMethodScopedWorker;
import io.kestra.core.runners.Worker;
import io.kestra.core.tasks.executions.Fail;
import io.kestra.core.tasks.test.PollingTrigger;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@MicronautTest(transactional = false, rebuildContext = true) // without 'rebuildContext = true' the second test fail
public class SchedulerPollingTriggerTest extends AbstractSchedulerTest {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private SchedulerTriggerStateInterface triggerState;

    @Inject
    private FlowListeners flowListenersService;


    @Test
    void pollingTrigger() throws Exception {
        // mock flow listener
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        PollingTrigger pollingTrigger = createPollingTrigger(null);
        Flow flow = createPollingTriggerFlow(pollingTrigger);
        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        CountDownLatch queueCount = new CountDownLatch(1);

        try (
            AbstractScheduler scheduler = scheduler(flowListenersServiceSpy);
            Worker worker = new TestMethodScopedWorker(applicationContext, 8, null)
        ) {
            AtomicReference<Execution> last = new AtomicReference<>();

            Runnable executionQueueStop = executionQueue.receive(execution -> {
                if (execution.getLeft().getFlowId().equals(flow.getId())) {
                    last.set(execution.getLeft());
                    queueCount.countDown();
                }
            });

            worker.run();
            scheduler.run();

            queueCount.await(10, TimeUnit.SECONDS);
            // close the execution queue consumer
            executionQueueStop.run();

            assertThat(queueCount.getCount(), is(0L));
            assertThat(last.get(), notNullValue());
        }
    }

    @Test
    void pollingTriggerStopAfter() throws Exception {
        // mock flow listener
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        PollingTrigger pollingTrigger = createPollingTrigger(List.of(State.Type.FAILED));
        Flow flow = createPollingTriggerFlow(pollingTrigger)
            .toBuilder()
            .tasks(List.of(Fail.builder().id("fail").type(Fail.class.getName()).build()))
            .build();
        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        CountDownLatch queueCount = new CountDownLatch(2);

        try (
            AbstractScheduler scheduler = scheduler(flowListenersServiceSpy);
            Worker worker = new TestMethodScopedWorker(applicationContext, 8, null)
        ) {
            AtomicReference<Execution> last = new AtomicReference<>();

            Runnable executionQueueStop = executionQueue.receive(execution -> {
                if (execution.getLeft().getFlowId().equals(flow.getId())) {
                    last.set(execution.getLeft());
                    queueCount.countDown();

                    if (execution.getLeft().getState().getCurrent() == State.Type.CREATED) {
                        executionQueue.emit(execution.getLeft().withState(State.Type.FAILED));
                    }
                }
            });

            worker.run();
            scheduler.run();

            queueCount.await(10, TimeUnit.SECONDS);
            // close the execution queue consumer
            executionQueueStop.run();

            assertThat(queueCount.getCount(), is(0L));
            assertThat(last.get(), notNullValue());

            // Assert that the trigger is now disabled.
            // It needs to await on assertion as it will be disabled AFTER we receive a success execution.
            Trigger trigger = Trigger.of(flow, pollingTrigger);
            Await.until(() -> this.triggerState.findLast(trigger).map(t -> t.getDisabled()).orElse(false).booleanValue(), Duration.ofMillis(100), Duration.ofSeconds(10));
        }
    }

    private Flow createPollingTriggerFlow(PollingTrigger pollingTrigger) {
        return createFlow(Collections.singletonList(pollingTrigger));
    }

    private PollingTrigger createPollingTrigger(List<State.Type> stopAfter) {
        return PollingTrigger.builder()
            .id("polling-trigger")
            .type(PollingTrigger.class.getName())
            .duration(500L)
            .stopAfter(stopAfter)
            .build();
    }

    private AbstractScheduler scheduler(FlowListeners flowListenersServiceSpy) {
        return new DefaultScheduler(
            applicationContext,
            flowListenersServiceSpy,
            triggerState
        );
    }
}
