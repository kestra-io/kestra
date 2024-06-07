package io.kestra.core.schedulers;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.PluginDefault;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.TestMethodScopedWorker;
import io.kestra.core.runners.Worker;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.runner.JdbcScheduler;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class SchedulerThreadTest extends AbstractSchedulerTest {
    @Inject
    protected FlowListeners flowListenersService;

    @Inject
    protected SchedulerTriggerStateInterface triggerState;

    @Test
    void thread() throws Exception {
        Flow flow = createThreadFlow();
        CountDownLatch queueCount = new CountDownLatch(2);

        // wait for execution
        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();

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
            AbstractScheduler scheduler = new JdbcScheduler(
                applicationContext,
                flowListenersServiceSpy
            );
            Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 8, null)
        ) {
            // start the worker as it execute polling triggers
            worker.run();
            scheduler.run();
            boolean sawSuccessExecution = queueCount.await(1, TimeUnit.MINUTES);
            Execution last = receive.blockLast();

            assertThat("Countdown latch returned " + sawSuccessExecution, last, notNullValue());
            assertThat(last.getTrigger().getVariables().get("defaultInjected"), is("done"));
            assertThat(last.getTrigger().getVariables().get("counter"), is(3));
            assertThat(last.getLabels(), hasItem(new Label("flow-label-1", "flow-label-1")));
            assertThat(last.getLabels(), hasItem(new Label("flow-label-2", "flow-label-2")));
            AbstractSchedulerTest.COUNTER = 0;
        }
    }
}
