package io.kestra.core.schedulers;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.TaskDefault;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.TestMethodScopedWorker;
import io.kestra.core.runners.Worker;
import jakarta.inject.Inject;
import org.junit.jupiter.api.RepeatedTest;
import org.junitpioneer.jupiter.RetryingTest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class SchedulerThreadTest extends AbstractSchedulerTest {
    @Inject
    protected FlowListeners flowListenersService;

    @Inject
    protected SchedulerTriggerStateInterface triggerState;

    public static Flow createThreadFlow() {
        return createThreadFlow(null);
    }

    public static Flow createThreadFlow(String workerGroup) {
        UnitTest schedule = UnitTest.builder()
            .id("sleep")
            .type(UnitTest.class.getName())
            .workerGroup(workerGroup == null ? null : new WorkerGroup(workerGroup))
            .build();

        return createFlow(Collections.singletonList(schedule), List.of(
            TaskDefault.builder()
                .type(UnitTest.class.getName())
                .values(Map.of("defaultInjected", "done"))
                .build()
        ));
    }

    @RetryingTest(5)
    void thread() throws Exception {
        Flow flow = createThreadFlow();
        CountDownLatch queueCount = new CountDownLatch(2);
        AtomicReference<Execution> last = new AtomicReference<>();

        // wait for execution
        Runnable assertionStop = executionQueue.receive(SchedulerThreadTest.class, either -> {
            Execution execution = either.getLeft();
            last.set(execution);

            assertThat(execution.getFlowId(), is(flow.getId()));

            if (execution.getState().getCurrent() != State.Type.SUCCESS) {
                executionQueue.emit(execution.withState(State.Type.SUCCESS));
                queueCount.countDown();
            }
        });

        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);


        doReturn(Collections.singletonList(flow))
            .when(flowListenersServiceSpy)
            .flows();

        // scheduler
        try (
            AbstractScheduler scheduler = new DefaultScheduler(
                applicationContext,
                flowListenersServiceSpy,
                triggerState
            );
            Worker worker = new TestMethodScopedWorker(applicationContext, 8, null);
        ) {
            // start the worker as it execute polling triggers
            worker.run();
            scheduler.run();
            boolean sawSuccessExecution = queueCount.await(1, TimeUnit.MINUTES);
            // needed for RetryingTest to work since there is no context cleaning between method => we have to clear assertion receiver manually
            assertionStop.run();

            assertThat("Countdown latch returned " + sawSuccessExecution, last.get(), notNullValue());
            assertThat(last.get().getTrigger().getVariables().get("defaultInjected"), is("done"));
            assertThat(last.get().getTrigger().getVariables().get("counter"), is(3));
            assertThat(last.get().getLabels(), hasItem(new Label("flow-label-1", "flow-label-1")));
            assertThat(last.get().getLabels(), hasItem(new Label("flow-label-2", "flow-label-2")));
            AbstractSchedulerTest.COUNTER = 0;
        }
    }
}
