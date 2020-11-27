package org.kestra.core.schedulers;

import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.runner.memory.MemoryFlowListeners;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SchedulerThreadTest extends AbstractSchedulerTest {
    @Inject
    protected MemoryFlowListeners flowListenersService;

    @Inject
    protected SchedulerTriggerStateInterface triggerState;

    @Inject
    protected SchedulerExecutionStateInterface executionState;

    private static Flow createThreadFlow() {
        UnitTest schedule = UnitTest.builder()
            .id("sleep")
            .type(UnitTest.class.getName())
            .build();

        return createFlow(Collections.singletonList(schedule));
    }

    @Test
    void thread() throws Exception {
        // mock flow listeners
        MemoryFlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        SchedulerExecutionStateInterface executionRepositorySpy = spy(this.executionState);
        CountDownLatch queueCount = new CountDownLatch(2);

        Flow flow = createThreadFlow();

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

            AtomicReference<Execution> last = new AtomicReference<>();

            // wait for execution
            executionQueue.receive(SchedulerThreadTest.class, execution -> {
                last.set(execution);
                queueCount.countDown();
                assertThat(execution.getFlowId(), is(flow.getId()));
            });

            scheduler.run();
            queueCount.await();

            assertThat(last.get().getVariables().get("counter"), is(3));
        }
    }

}
