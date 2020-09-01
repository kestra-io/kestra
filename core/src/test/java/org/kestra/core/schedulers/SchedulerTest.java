package org.kestra.core.schedulers;

import com.devskiller.friendly_id.FriendlyId;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.triggers.types.Schedule;
import org.kestra.core.models.triggers.types.ScheduleBackfill;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.ExecutionRepositoryInterface;
import org.kestra.core.repositories.TriggerRepositoryInterface;
import org.kestra.core.services.FlowListenersService;
import org.kestra.core.tasks.debugs.Return;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import javax.inject.Inject;
import javax.inject.Named;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@MicronautTest
class SchedulerTest {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private TriggerRepositoryInterface triggerContextRepository;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowListenersService flowListenersService;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    private static Flow create() {
        System.out.println(date(-4));

        Schedule schedule = Schedule.builder()
            .id("monthly")
            .type(Schedule.class.getName())
            .cron("0 0 1 * *")
            .backfill(ScheduleBackfill.builder()
                .start(date(-5))
                .build()
            )
            .build();

        return Flow.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace("org.kestra.unittest")
            .revision(1)
            .triggers(Collections.singletonList(schedule))
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();
    }

    private static ZonedDateTime date(int plus) {
        return ZonedDateTime.now()
            .withMonth(ZonedDateTime.now().getMonthValue() + plus)
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS);
    }

    @Test
    void taskPoolTrigger() throws Exception {
        // mock flow listeners
        FlowListenersService flowListenersServiceSpy = spy(this.flowListenersService);
        ExecutionRepositoryInterface executionRepositorySpy = spy(this.executionRepository);
        CountDownLatch queueCount = new CountDownLatch(5);

        Flow flow = create();

        doReturn(Collections.singletonList(flow))
            .when(flowListenersServiceSpy)
            .getFlows();

        // mock the backfill execution is ended
        doAnswer(invocation -> Optional.of(Execution.builder().state(new State().withState(State.Type.SUCCESS)).build()))
            .when(executionRepositorySpy)
            .findById(any());

        // scheduler
        try (Scheduler scheduler = new Scheduler(
            applicationContext,
            executionQueue,
            flowListenersServiceSpy,
            executionRepositorySpy,
            triggerContextRepository
        )) {

            // wait for execution
            executionQueue.receive(SchedulerTest.class, execution -> {
                queueCount.countDown();
                assertThat(execution.getFlowId(), is(flow.getId()));
            });

            scheduler.run();
            queueCount.await();
        }
    }
}
