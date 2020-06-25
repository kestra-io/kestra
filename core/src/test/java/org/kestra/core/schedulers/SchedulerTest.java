package org.kestra.core.schedulers;

import com.devskiller.friendly_id.FriendlyId;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.types.Schedule;
import org.kestra.core.models.validations.ModelValidator;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.services.FlowListenersService;
import org.kestra.core.tasks.debugs.Return;
import org.kestra.core.utils.ThreadMainFactoryBuilder;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Named;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@MicronautTest
class SchedulerTest {
    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    private QueueInterface<Flow> flowQueue;

    @Inject
    private FlowListenersService flowListenersService;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    private ThreadMainFactoryBuilder threadMainFactoryBuilder;

    private static Flow create() {
        Schedule schedule = Schedule.builder().type(Schedule.class.getName()).cron("0 0 1 * *").build();

        return Flow.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace("org.kestra.unittest")
            .revision(1)
            .triggers(Collections.singletonList(schedule))
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();
    }

    @Test
    void taskPoolTrigger() throws Exception {
        // mock flow listeners
        FlowListenersService flowListenersServiceSpy = spy(this.flowListenersService);

        Flow flow = create();

        doReturn(Collections.singletonList(flow))
            .when(flowListenersServiceSpy)
            .getFlows();

        // scheduler
        Scheduler schedulerSpy = spy(new Scheduler(
            executionQueue,
            flowListenersServiceSpy,
            threadMainFactoryBuilder
        ));

        // mock date
        ZonedDateTime date = ZonedDateTime.now()
            .withMonth(ZonedDateTime.now().getMonthValue() + 1)
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .minusSeconds(1)
            .truncatedTo(ChronoUnit.SECONDS);

        doReturn(date)
            .when(schedulerSpy)
            .now();

        schedulerSpy.run();

        // wait for execution
        CountDownLatch countDownLatch = new CountDownLatch(1);


        executionQueue.receive(SchedulerTest.class, execution -> {
            countDownLatch.countDown();
            assertThat(execution.getFlowId(), is(flow.getId()));
        });

        countDownLatch.await();

        schedulerSpy.close();
    }


//
//    @Test
//    void taskPoolTrigger() throws Exception {
//        /*
//        Secure and fast test for async system, avoid flaky tests
//        wait 1 second max for expected result to append
//        */
//
//        pool.upsert(scheduledFlow);
//        AtomicBoolean executionFired = new AtomicBoolean(false);
//        executionQueue.receive(Indexer.class, execution -> {
//            executionFired.set(true);
//            assertThat(execution.getFlowId(), is("scheduleFlowTest"));
//        });
//        long i = Instant.ofEpochMilli(new Predictor("* * * * *").nextMatchingDate().getTime()).getEpochSecond() - 1;
//        pool.triggerReadyFlows(Instant.ofEpochSecond(i));
//        int retryCount = 0;
//        while (!executionFired.get() && retryCount++ < 100) {
//            Thread.sleep(10);
//        }
//        assertThat(executionFired.get(), is(true));
//        pool.upsert(Flow.builder().id("scheduleFlowTest").build());
//        assertThat(pool.getActiveFlowCount(), is(0));
//    }

}
