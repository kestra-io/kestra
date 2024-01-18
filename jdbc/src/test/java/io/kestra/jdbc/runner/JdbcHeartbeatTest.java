package io.kestra.jdbc.runner;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State.Type;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.*;
import io.kestra.core.services.SkipExecutionService;
import io.kestra.core.tasks.test.Sleep;
import io.kestra.core.tasks.test.SleepTrigger;
import io.kestra.core.utils.Await;
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
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.fail;

@MicronautTest(transactional = false, environments = "heartbeat")
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // must be per-class to allow calling once init() which took a lot of time
@Property(name = "kestra.server-type", value = "EXECUTOR")
public abstract class JdbcHeartbeatTest {
    @Inject
    private StandAloneRunner runner;

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

    @Inject
    SkipExecutionService skipExecutionService;

    @BeforeAll
    void init() throws IOException, URISyntaxException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();

        TestsUtils.loads(repositoryLoader);
    }

    @Test
    void taskResubmit() throws Exception {
        CountDownLatch runningLatch = new CountDownLatch(1);
        CountDownLatch resubmitLatch = new CountDownLatch(1);

        Worker worker = new Worker(applicationContext, 8, null);
        applicationContext.registerSingleton(worker);
        worker.run();
        runner.setSchedulerEnabled(false);
        runner.setWorkerEnabled(false);
        runner.run();

        AtomicReference<WorkerTaskResult> workerTaskResult = new AtomicReference<>(null);
        workerTaskResultQueue.receive(either -> {
            workerTaskResult.set(either.getLeft());

            if (either.getLeft().getTaskRun().getState().getCurrent() == Type.SUCCESS) {
                resubmitLatch.countDown();
            }

            if (either.getLeft().getTaskRun().getState().getCurrent() == Type.RUNNING) {
                runningLatch.countDown();
            }
        });

        workerJobQueue.emit(workerTask(1500));
        runningLatch.await(2, TimeUnit.SECONDS);
        worker.shutdown();

        Worker newWorker = new Worker(applicationContext, 8, null);
        applicationContext.registerSingleton(newWorker);
        newWorker.run();
        resubmitLatch.await(15, TimeUnit.SECONDS);

        newWorker.shutdown();
        assertThat(workerTaskResult.get().getTaskRun().getState().getCurrent(), is(Type.SUCCESS));
    }

    @Test
    void taskResubmitSkipExecution() throws Exception {
        CountDownLatch runningLatch = new CountDownLatch(1);

        Worker worker = new Worker(applicationContext, 8, null);
        applicationContext.registerSingleton(worker);
        worker.run();
        runner.setSchedulerEnabled(false);
        runner.setWorkerEnabled(false);
        runner.run();
        WorkerTask workerTask = workerTask(1500);
        skipExecutionService.setSkipExecutions(List.of(workerTask.getTaskRun().getExecutionId()));

        AtomicReference<WorkerTaskResult> workerTaskResult = new AtomicReference<>(null);
        Runnable assertionStop = workerTaskResultQueue.receive(either -> {
            workerTaskResult.set(either.getLeft());

            if (either.getLeft().getTaskRun().getState().getCurrent() == Type.SUCCESS) {
                // no resubmit should happen!
                fail();
            }

            if (either.getLeft().getTaskRun().getState().getCurrent() == Type.RUNNING) {
                runningLatch.countDown();
            }
        });

        workerJobQueue.emit(workerTask);
        runningLatch.await(2, TimeUnit.SECONDS);
        worker.shutdown();

        Worker newWorker = new Worker(applicationContext, 8, null);
        applicationContext.registerSingleton(newWorker);
        newWorker.run();

        // wait a little to be sure there is no resubmit
        Thread.sleep(500);
        assertionStop.run();
        newWorker.shutdown();
        assertThat(workerTaskResult.get().getTaskRun().getState().getCurrent(), not(Type.SUCCESS));
    }

    @Test
    void triggerResubmit() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Worker worker = new Worker(applicationContext, 8, null);
        applicationContext.registerSingleton(worker);
        worker.run();
        runner.setSchedulerEnabled(false);
        runner.setWorkerEnabled(false);
        runner.run();

        AtomicReference<WorkerTriggerResult> workerTriggerResult = new AtomicReference<>(null);
        workerTriggerResultQueue.receive(either -> {
            workerTriggerResult.set(either.getLeft());
            countDownLatch.countDown();
        });

        WorkerTrigger workerTrigger = workerTrigger(7000);
        workerJobQueue.emit(workerTrigger);
        Await.until(() -> worker.getEvaluateTriggerRunningCount()
                .get("io.kestra.jdbc.runner.jdbcheartbeattest_workertrigger_unit-test") != null,
            Duration.ofMillis(100),
            Duration.ofSeconds(5)
        );
        worker.shutdown();

        Worker newWorker = new Worker(applicationContext, 8, null);
        applicationContext.registerSingleton(newWorker);
        newWorker.run();
        boolean lastAwait = countDownLatch.await(12, TimeUnit.SECONDS);

        newWorker.shutdown();
        assertThat("Last await result was " + lastAwait, workerTriggerResult.get().getSuccess(), is(true));
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
