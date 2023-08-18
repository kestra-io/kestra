package io.kestra.core.schedulers;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.types.Schedule;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.TestMethodScopedWorker;
import io.kestra.core.runners.Worker;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class SchedulerInvalidFlowTest extends AbstractSchedulerTest {
    @Inject
    protected FlowListeners flowListenersService;

    @Inject
    protected SchedulerTriggerStateInterface triggerState;

    protected AbstractScheduler scheduler(FlowListeners flowListenersServiceSpy) {
        return new DefaultScheduler(
            applicationContext,
            flowListenersServiceSpy,
            triggerState
        );
    }

    @Test
    void invalidFlow() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);

        Flow flow = createScheduleFlow();

        doReturn(Collections.singletonList(flow))
            .when(flowListenersServiceSpy)
            .flows();

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy);
             Worker worker = new TestMethodScopedWorker(applicationContext, 8, null)) {
            // check that no executions are triggered
            executionQueue.receive(execution -> {
                fail();
            });

            worker.run();
            scheduler.run();

            // wait for the scheduler to start
            Thread.sleep(100);

            // invalid flow must be filtered from the schedulable list
            assertThat(scheduler.getSchedulable(), empty());
        }
    }

    private static Flow createScheduleFlow() {
        Schedule schedule = Schedule.builder()
            .id("hourly")
            .type(Schedule.class.getName())
            .cron("0 * * * *")
            .build();

        return FlowWithException.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .revision(1)
            .exception("Invalid type io.kestra.test.task.NotExisting")
            .triggers(List.of(schedule))
            .tasks(List.of(new Task() {
                @Override
                public String getId() {
                    return "id";
                }

                @Override
                public String getType() {
                    return "io.kestra.test.task.NotExisting";
                }
            }))
            .build();
    }
}
