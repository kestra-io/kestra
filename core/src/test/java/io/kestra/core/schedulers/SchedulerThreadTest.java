package io.kestra.core.schedulers;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.TaskDefault;
import io.kestra.core.runners.FlowListeners;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SchedulerThreadTest extends AbstractSchedulerTest {
    @Inject
    protected FlowListeners flowListenersService;

    @Inject
    protected SchedulerTriggerStateInterface triggerState;

    @Inject
    protected SchedulerExecutionStateInterface executionState;

    public static Flow createThreadFlow() {
        UnitTest schedule = UnitTest.builder()
            .id("sleep")
            .type(UnitTest.class.getName())
            .build();

        return createFlow(Collections.singletonList(schedule), List.of(
            TaskDefault.builder()
                .type(UnitTest.class.getName())
                .values(Map.of("defaultInjected", "done"))
                .build()
        ));
    }

    @Test
    void thread() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        SchedulerExecutionStateInterface schedulerExecutionStateSpy = spy(this.executionState);
        CountDownLatch queueCount = new CountDownLatch(2);

        Flow flow = createThreadFlow();

        doReturn(Collections.singletonList(flow))
            .when(flowListenersServiceSpy)
            .flows();

        // mock the backfill execution is ended
        doAnswer(invocation -> Optional.of(Execution.builder().state(new State().withState(State.Type.SUCCESS)).build()))
            .when(schedulerExecutionStateSpy)
            .findById(any());

        // scheduler
        try (AbstractScheduler scheduler = new DefaultScheduler(
            applicationContext,
            flowListenersServiceSpy,
            schedulerExecutionStateSpy,
            triggerState
        )) {
            AtomicReference<Execution> last = new AtomicReference<>();

            // wait for execution
            executionQueue.receive(SchedulerThreadTest.class, execution -> {
                last.set(execution);

                assertThat(execution.getFlowId(), is(flow.getId()));

                if (execution.getState().getCurrent() != State.Type.SUCCESS) {
                    executionQueue.emit(execution.withState(State.Type.SUCCESS));
                    queueCount.countDown();
                }
            });

            scheduler.run();
            queueCount.await(1, TimeUnit.MINUTES);

            assertThat(last.get().getVariables().get("defaultInjected"), is("done"));
            assertThat(last.get().getVariables().get("counter"), is(3));
            AbstractSchedulerTest.COUNTER = 0;
        }
    }

}
