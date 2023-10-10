package io.kestra.jdbc.runner;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.*;
import io.kestra.core.tasks.test.Sleep;
import io.kestra.core.tasks.test.SleepTrigger;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest(transactional = false, environments = "heartbeat")
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // must be per-class to allow calling once init() which took a lot of time
@Property(name = "kestra.server-type", value = "EXECUTOR")
public abstract class JdbcHeartbeatTest {
    @Inject
    private StandAloneRunner runner;

    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    private LocalFlowRepositoryLoader repositoryLoader;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.WORKERJOB_NAMED)
    QueueInterface<WorkerJob> workerJobQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTRIGGERRESULT_NAMED)
    QueueInterface<WorkerTriggerResult> workerTriggerResultQueue;

    @BeforeAll
    void init() throws IOException, URISyntaxException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();

        TestsUtils.loads(repositoryLoader);
    }

    @Test
    void taskResubmit() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Worker worker = new Worker(applicationContext, 8, null);
        applicationContext.registerSingleton(worker);
        worker.run();
        runner.setSchedulerEnabled(false);
        runner.setWorkerEnabled(false);
        runner.run();

        AtomicReference<WorkerTaskResult> workerTaskResult = new AtomicReference<>(null);
        workerTaskResultQueue.receive(either -> workerTaskResult.set(either.getLeft()));

        workerJobQueue.emit(workerTask(12000));
        countDownLatch.await(2, TimeUnit.SECONDS);

        worker.shutdown();

        countDownLatch.await(10, TimeUnit.SECONDS);
        Worker newWorker = new Worker(applicationContext, 8, null);
        applicationContext.registerSingleton(newWorker);
        newWorker.run();


        assertThat(workerTaskResult.get().getTaskRun().getState().getCurrent(), is(io.kestra.core.models.flows.State.Type.SUCCESS));
    }

    @Test
    void triggerResubmit() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Worker worker = new Worker(applicationContext, 8, null);
        worker.run();

        AtomicReference<WorkerTriggerResult> workerTriggerResult = new AtomicReference<>(null);
        workerTriggerResultQueue.receive(either -> workerTriggerResult.set(either.getLeft()));

        workerJobQueue.emit(workerTrigger(7000));


        Worker newWorker = new Worker(applicationContext, 8, null);
        newWorker.run();

        countDownLatch.await(9, TimeUnit.SECONDS);

        assertThat(workerTriggerResult.get().getSuccess(), is(true));
    }

    private WorkerTask workerTask(long sleepDuration) {
        Sleep bash = Sleep.builder()
            .type(Sleep.class.getName())
            .id("unit-test")
            .duration(sleepDuration)
            .build();

        Execution execution = TestsUtils.mockExecution(flowBuilder(sleepDuration), ImmutableMap.of());

        ResolvedTask resolvedTask = ResolvedTask.of(bash);

        return WorkerTask.builder()
            .runContext(runContextFactory.of(ImmutableMap.of("key", "value")))
            .task(bash)
            .taskRun(TaskRun.of(execution, resolvedTask))
            .build();
    }

    private WorkerTrigger workerTrigger(long sleepDuration) {
        SleepTrigger trigger = SleepTrigger.builder()
            .type(SleepTrigger.class.getName())
            .id("unit-test")
            .duration(sleepDuration)
            .build();

        Map.Entry<ConditionContext, TriggerContext> mockedTrigger = TestsUtils.mockTrigger(runContextFactory, trigger);

        return WorkerTrigger.builder()
            .trigger(trigger)
            .triggerContext(mockedTrigger.getValue())
            .conditionContext(mockedTrigger.getKey())
            .build();
    }

    private Flow flowBuilder(long sleepDuration) {
        Sleep bash = Sleep.builder()
            .type(Sleep.class.getName())
            .id("unit-test")
            .duration(sleepDuration)
            .build();

        SleepTrigger trigger = SleepTrigger.builder()
            .type(SleepTrigger.class.getName())
            .id("unit-test")
            .duration(sleepDuration)
            .build();

        return Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unit-test")
            .tasks(Collections.singletonList(bash))
            .triggers(Collections.singletonList(trigger))
            .build();
    }
}
