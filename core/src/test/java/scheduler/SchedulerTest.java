package scheduler;

import io.micronaut.test.annotation.MicronautTest;
import it.sauronsoftware.cron4j.InvalidPatternException;
import it.sauronsoftware.cron4j.Predictor;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.validations.ModelValidator;
import org.kestra.core.schedulers.FlowPool;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.triggers.types.Schedule;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.runners.Indexer;
import org.kestra.core.tasks.debugs.Return;

import javax.inject.Inject;
import javax.inject.Named;
import javax.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class SchedulerTest {

    @Inject
    private FlowPool pool;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    private QueueInterface<Flow> flowQueue;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ModelValidator modelValidator;

    @Test
    void cronTask() throws Exception {
        String cron = "1 1 1 1 1";
        Schedule trigger = Schedule.builder().cron(cron).build();
        assertThat(trigger.isReady(Instant.now()), is(false));
        assertThat(trigger.hasNextSchedule(), is(true));
    }

    @Test
    void taskPoolBasics() throws Exception {

        Flow flow1 = Flow.builder().id("flow1").build();
        assertThat(pool.getActiveFlowCount(), is(0));
        pool.upsert(flow1);
        assertThat(pool.getActiveFlowCount(), is(0));
        Schedule schedule = Schedule.builder().cron("* * * * *").build();
        Flow flow2 = Flow.builder().id("flow2").triggers(Arrays.asList(schedule)).build();
        pool.upsert(flow2);
        assertThat(pool.getActiveFlowCount(), is(1));
        Flow flow2Unschedule = Flow.builder().id("flow2").build();
        pool.upsert(flow2Unschedule);
        assertThat(pool.getActiveFlowCount(), is(0));
    }

    @Test
    void taskPoolTrigger() throws Exception {
        /*
        Secure and fast test for async system, avoid flaky tests
        wait 1 second max for expected result to append
        */
        Schedule schedule = Schedule.builder().cron("* * * * *").build();
        Flow scheduledFlow = Flow.builder().id("scheduleFlowTest").triggers(Arrays.asList(schedule)).build();
        pool.upsert(scheduledFlow);
        AtomicBoolean executionFired = new AtomicBoolean(false);
        executionQueue.receive(Indexer.class, execution -> {
            executionFired.set(true);
            assertThat(execution.getFlowId(), is("scheduleFlowTest"));
        });
        long i = Instant.ofEpochMilli(new Predictor("* * * * *").nextMatchingDate().getTime()).getEpochSecond() - 1;
        pool.triggerReadyFlows(Instant.ofEpochSecond(i));
        int retryCount = 0;
        while (!executionFired.get() && retryCount++ < 100) {
            Thread.sleep(10);
        }
        assertThat(executionFired.get(), is(true));
        pool.upsert(Flow.builder().id("scheduleFlowTest").build());
        assertThat(pool.getActiveFlowCount(), is(0));
    }

    @Test
    void flowScheduleOnChange() throws Exception {
        AtomicInteger eventCount = new AtomicInteger();
        flowQueue.receive(Indexer.class, flow -> {
            assertThat(flow.getId(), is("testFlowOnChange"));
            eventCount.getAndIncrement();
        });
        List<Task> tasks = Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build());
        Flow f = Flow.builder().id("testFlowOnChange").namespace("org.kestra.unitest.scheduler").tasks(tasks).build();
        f = flowRepository.create(f);
        f = flowRepository.update(f, f);
        flowRepository.delete(f);
        int retryCount = 0;
        while (eventCount.get() != 3 && retryCount++ < 100) {
            Thread.sleep(10);
        }
        assertThat(eventCount.get(), is(3));

    }

    @Test
    void CronValidation() throws Exception {
        Schedule build = Schedule.builder()
            .type(Schedule.class.getName())
            .cron("* * * * *")
            .build();

        assertThat(modelValidator.isValid(build).isEmpty(), is(true));

        build = Schedule.builder()
            .type(Schedule.class.getName())
            .cron("$ome Inv@lid Cr0n")
            .build();

        assertThat(modelValidator.isValid(build).isPresent(), is(true));
        assertThat(modelValidator.isValid(build).get().getMessage(), containsString("invalid cron expression"));
    }
}
