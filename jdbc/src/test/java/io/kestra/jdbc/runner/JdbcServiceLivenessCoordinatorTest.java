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
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.StandAloneRunner;
import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.runners.WorkerTriggerResult;
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
import org.junit.jupiter.api.Disabled;
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

@MicronautTest(transactional = false, environments =  {"test", "liveness"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // must be per-class to allow calling once init() which took a lot of time
@Property(name = "kestra.server-type", value = "EXECUTOR")
public abstract class JdbcServiceLivenessCoordinatorTest {
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
    JdbcServiceLivenessCoordinator jdbcServiceLivenessHandler;

    @Inject
    SkipExecutionService skipExecutionService;

    @BeforeAll
    void init() throws IOException, URISyntaxException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
        TestsUtils.loads(repositoryLoader);
        // Simulate that executor and workers are not running on the same JVM.
        jdbcServiceLivenessHandler.setServerInstance(IdUtils.create());
    }

    @Test
    void shouldReEmitTasksWhenWorkerIsDetectedAsNonResponding() throws Exception {
        CountDownLatch runningLatch = new CountDownLatch(1);
        CountDownLatch resubmitLatch = new CountDownLatch(1);

        // create first worker
        Worker worker = applicationContext.createBean(Worker.class, IdUtils.create(), 1, null);
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

        workerJobQueue.emit(workerTask(Duration.ofSeconds(10)));
        runningLatch.await(5, TimeUnit.SECONDS);
        worker.shutdown(); // stop processing task

        // create second worker (this will revoke previously one).
        Worker newWorker = applicationContext.createBean(Worker.class, IdUtils.create(), 1, null);
        newWorker.run();
        resubmitLatch.await(30, TimeUnit.SECONDS);
        newWorker.shutdown();
        assertThat(workerTaskResult.get().getTaskRun().getState().getCurrent(), is(Type.SUCCESS));
    }

    @Test
    void taskResubmitSkipExecution() throws Exception {
        CountDownLatch runningLatch = new CountDownLatch(1);

        Worker worker = applicationContext.createBean(Worker.class, IdUtils.create(), 8, null);
        worker.run();
        runner.setSchedulerEnabled(false);
        runner.setWorkerEnabled(false);
        runner.run();
        WorkerTask workerTask = workerTask(Duration.ofSeconds(10));
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

        Worker newWorker = applicationContext.createBean(Worker.class, IdUtils.create(), 1, null);
        newWorker.run();

        // wait a little to be sure there is no resubmit
        Thread.sleep(500);
        assertionStop.run();
        newWorker.shutdown();
        assertThat(workerTaskResult.get().getTaskRun().getState().getCurrent(), not(Type.SUCCESS));
    }

    @Test
    void shouldReEmitTriggerWhenWorkerIsDetectedAsNonResponding() throws Exception {
        Worker worker = applicationContext.createBean(Worker.class, IdUtils.create(), 1, null);
        worker.run();
        runner.setSchedulerEnabled(false);
        runner.setWorkerEnabled(false);
        runner.run();

        WorkerTrigger workerTrigger = workerTrigger(Duration.ofSeconds(5));

        // 2 trigger should happen because of the resubmit
        CountDownLatch countDownLatch = new CountDownLatch(2);
        workerJobQueue.receive(workerJob -> countDownLatch.countDown());

        workerJobQueue.emit(workerTrigger);
        Await.until(() -> worker.getEvaluateTriggerRunningCount()
                .get(workerTrigger.getTriggerContext().uid()) != null,
            Duration.ofMillis(100),
            Duration.ofSeconds(5)
        );
        worker.shutdown();

        Worker newWorker = applicationContext.createBean(Worker.class, IdUtils.create(), 1, null);
        applicationContext.registerSingleton(newWorker);
        newWorker.run();

        boolean lastAwait = countDownLatch.await(15, TimeUnit.SECONDS);

        newWorker.shutdown();
        assertThat(lastAwait, is(true));
    }

    private WorkerTask workerTask(Duration sleep) {
        Sleep bash = Sleep.builder()
            .type(Sleep.class.getName())
            .id("unit-test")
            .duration(sleep.toMillis())
            .build();

        Execution execution = TestsUtils.mockExecution(flowBuilder(sleep), ImmutableMap.of());

        ResolvedTask resolvedTask = ResolvedTask.of(bash);

        return WorkerTask.builder()
            .runContext(runContextFactory.of(ImmutableMap.of("key", "value")))
            .task(bash)
            .taskRun(TaskRun.of(execution, resolvedTask))
            .build();
    }

    private WorkerTrigger workerTrigger(Duration sleep) {
        SleepTrigger trigger = SleepTrigger.builder()
            .type(SleepTrigger.class.getName())
            .id("unit-test")
            .duration(sleep.toMillis())
            .build();

        Map.Entry<ConditionContext, TriggerContext> mockedTrigger = TestsUtils.mockTrigger(runContextFactory, trigger);

        return WorkerTrigger.builder()
            .trigger(trigger)
            .triggerContext(mockedTrigger.getValue())
            .conditionContext(mockedTrigger.getKey())
            .build();
    }

    private Flow flowBuilder(final Duration sleep) {
        Sleep bash = Sleep.builder()
            .type(Sleep.class.getName())
            .id("unit-test")
            .duration(sleep.toMillis())
            .build();

        SleepTrigger trigger = SleepTrigger.builder()
            .type(SleepTrigger.class.getName())
            .id("unit-test")
            .duration(sleep.toMillis())
            .build();

        return Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unit-test")
            .tasks(Collections.singletonList(bash))
            .triggers(Collections.singletonList(trigger))
            .build();
    }
}
