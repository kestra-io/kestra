package io.kestra.jdbc.runner;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State.Type;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.*;
import io.kestra.core.services.SkipExecutionService;
import io.kestra.core.services.WorkerGroupService;
import io.kestra.core.tasks.test.SleepTrigger;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.repository.AbstractJdbcWorkerJobRunningRepository;
import io.kestra.plugin.core.flow.Sleep;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@KestraTest(environments = {"test", "liveness"}, startRunner = true, startWorker = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// must be per-class to allow calling once init() which took a lot of time
@Property(name = "kestra.server-type", value = "EXECUTOR")
public abstract class JdbcServiceLivenessCoordinatorTest {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private TestRunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.WORKERJOB_NAMED)
    private QueueInterface<WorkerJob> workerJobQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    private QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTRIGGERRESULT_NAMED)
    private QueueInterface<WorkerTriggerResult> workerTriggerResultQueue;

    @Inject
    @Named(QueueFactoryInterface.TRIGGER_NAMED)
    private QueueInterface<Trigger> triggerQueue;

    @Inject
    private JdbcServiceLivenessCoordinator jdbcServiceLivenessHandler;

    @Inject
    private SkipExecutionService skipExecutionService;

    @Inject
    private AbstractJdbcWorkerJobRunningRepository workerJobRunningRepository;

    @BeforeAll
    void init() {
        // Simulate that executor and workers are not running on the same JVM.
        jdbcServiceLivenessHandler.setServerInstance(IdUtils.create());
    }

    @AfterEach
    void tearDown() {
        List<WorkerJobRunning> workerJobRunnings = workerJobRunningRepository.findAll();
        workerJobRunnings.forEach(workerJobRunning -> workerJobRunningRepository.deleteByKey(workerJobRunning.uid()));
    }

    @FlakyTest
    @Test
    void shouldReEmitTasksWhenWorkerIsDetectedAsNonResponding() throws Exception {
        CountDownLatch runningLatch = new CountDownLatch(1);
        CountDownLatch resubmitLatch = new CountDownLatch(1);

        // create first worker
        Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 1, null);
        worker.run();

        Flux<WorkerTaskResult> receive = TestsUtils.receive(workerTaskResultQueue, either -> {
            if (either.getLeft().getTaskRun().getState().getCurrent() == Type.SUCCESS) {
                resubmitLatch.countDown();
            }

            if (either.getLeft().getTaskRun().getState().getCurrent() == Type.RUNNING) {
                runningLatch.countDown();
            }

            if (either.getLeft().getTaskRun().getState().getCurrent() == Type.FAILED) {
                fail("Worker task result should not be in FAILED state as it should be resubmitted");
            }
        });

        workerJobQueue.emit(workerTask(Duration.ofSeconds(5)));
        boolean runningLatchAwait = runningLatch.await(5, TimeUnit.SECONDS);
        assertThat(runningLatchAwait).isTrue();
        worker.close(); // stop processing task

        // create second worker (this will revoke previously one).
        Worker newWorker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 1, null);
        newWorker.run();
        boolean resubmitLatchAwait = resubmitLatch.await(10, TimeUnit.SECONDS);
        assertThat(resubmitLatchAwait).isTrue();
        WorkerTaskResult workerTaskResult = receive.blockLast();
        assertThat(workerTaskResult).isNotNull();
        assertThat(workerTaskResult.getTaskRun().getState().getCurrent()).isEqualTo(Type.SUCCESS);
        assertThat(workerTaskResult.getTaskRun().getAttempts()).hasSize(2);
        assertThat(workerTaskResult.getTaskRun().getAttempts().getFirst().getState().getHistories().stream().anyMatch(it -> it.getState() == Type.RESUBMITTED)).isTrue();
        newWorker.close();
    }

    @Test
    void shouldReEmitTasksToTheSameWorkerGroup() throws Exception {
        CountDownLatch runningLatch = new CountDownLatch(1);
        CountDownLatch resubmitLatch = new CountDownLatch(1);
        String workerGroup = IdUtils.create();

        // create first worker
        Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 1, workerGroup);
        worker.run();

        var workerTaskResultQueueAppendLog = new ArrayList<WorkerTaskResult>();// to debug flaky test
        Flux<WorkerTaskResult> receive = TestsUtils.receive(workerTaskResultQueue, either -> {
            workerTaskResultQueueAppendLog.add(either.getLeft());
            if (either.getLeft().getTaskRun().getState().getCurrent() == Type.SUCCESS) {
                resubmitLatch.countDown();
            }

            if (either.getLeft().getTaskRun().getState().getCurrent() == Type.RUNNING) {
                runningLatch.countDown();
            }
        });

        workerJobQueue.emit(workerGroup, workerTask(Duration.ofSeconds(5), workerGroup));
        boolean runningLatchAwait = runningLatch.await(5, TimeUnit.SECONDS);
        assertThat(runningLatchAwait).isTrue();
        worker.close(); // stop processing task

        // create second worker (this will revoke previously one).
        Worker newWorker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 1, workerGroup);
        newWorker.run();
        boolean resubmitLatchAwait = resubmitLatch.await(30, TimeUnit.SECONDS);
        assertThat(resubmitLatchAwait)
            .withFailMessage(() -> "shouldReEmitTasksToTheSameWorkerGroup: resubmitLatchAwait was not OK, workerTaskResultQueue content: " + TestsUtils.stringify(workerTaskResultQueueAppendLog))
            .isTrue();
        WorkerTaskResult workerTaskResult = receive.blockLast();
        assertThat(workerTaskResult).isNotNull();
        assertThat(workerTaskResult.getTaskRun().getState().getCurrent()).isEqualTo(Type.SUCCESS);
        assertThat(workerTaskResult.getTaskRun().getAttempts()).hasSize(2);
        assertThat(workerTaskResult.getTaskRun().getAttempts().getFirst().getState().getHistories().stream().anyMatch(it -> it.getState() == Type.RESUBMITTED)).isTrue();
        newWorker.close();
    }

    @Test
    void taskResubmitSkipExecution() throws Exception {
        CountDownLatch runningLatch = new CountDownLatch(1);

        Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 8, null);
        worker.run();

        WorkerTask workerTask = workerTask(Duration.ofSeconds(5));
        skipExecutionService.setSkipExecutions(List.of(workerTask.getTaskRun().getExecutionId()));

        Flux<WorkerTaskResult> receive = TestsUtils.receive(workerTaskResultQueue, either -> {
            if (either.getLeft().getTaskRun().getState().getCurrent() == Type.SUCCESS) {
                // no resubmit should happen!
                fail();
            }

            if (either.getLeft().getTaskRun().getState().getCurrent() == Type.RUNNING) {
                runningLatch.countDown();
            }
        });

        workerJobQueue.emit(workerTask);
        boolean runningLatchAwait = runningLatch.await(10, TimeUnit.SECONDS);
        assertThat(runningLatchAwait).isTrue();
        worker.close();

        Worker newWorker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 1, null);
        newWorker.run();

        // wait a little to be sure there is no resubmit
        Thread.sleep(500);
        receive.blockLast();
        newWorker.close();
        assertThat(receive.blockLast().getTaskRun().getState().getCurrent()).isNotEqualTo(Type.SUCCESS);
    }

    @FlakyTest
    @Test
    void shouldReEmitTriggerWhenWorkerIsDetectedAsNonResponding() throws Exception {
        Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 1, null);
        worker.run();

        WorkerTrigger workerTrigger = workerTrigger(Duration.ofSeconds(5));

        // 2 trigger should happen because of the resubmit
        CountDownLatch countDownLatch = new CountDownLatch(2);
        Flux<WorkerTriggerResult> receive = TestsUtils.receive(workerTriggerResultQueue, workerTriggerResult -> countDownLatch.countDown());

        // we wait that the worker receive the trigger
        CountDownLatch triggerCountDownLatch = new CountDownLatch(1);
        Flux<Trigger> receiveTrigger = TestsUtils.receive(triggerQueue, either -> {
            if (either.getLeft().getWorkerId().equals(worker.getId())) {
                triggerCountDownLatch.countDown();
            }
        });
        workerJobQueue.emit(workerTrigger);
        assertTrue(triggerCountDownLatch.await(10, TimeUnit.SECONDS));
        receiveTrigger.blockLast();
        worker.close();

        Worker newWorker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 1, null);
        newWorker.run();
        assertThat(countDownLatch.await(30, TimeUnit.SECONDS)).isTrue();

        receive.blockLast();
        newWorker.close();
    }

    @Test
    void shouldReEmitTriggerToTheSameWorkerGroup() throws Exception {
        String workerGroup = IdUtils.create();

        Worker worker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 1, workerGroup);
        worker.run();

        WorkerTrigger workerTrigger = workerTrigger(Duration.ofSeconds(5), workerGroup);

        // 2 triggers should happen because of the resubmit
        CountDownLatch countDownLatch = new CountDownLatch(2);
        Flux<WorkerTriggerResult> receive = TestsUtils.receive(workerTriggerResultQueue, workerTriggerResult -> countDownLatch.countDown());

        // we wait that the worker receives the trigger
        CountDownLatch triggerCountDownLatch = new CountDownLatch(1);
        Flux<Trigger> receiveTrigger = TestsUtils.receive(triggerQueue, either -> {
            if (either.getLeft().getWorkerId().equals(worker.getId())) {
                triggerCountDownLatch.countDown();
            }
        });
        workerJobQueue.emit(workerGroup, workerTrigger);
        assertTrue(triggerCountDownLatch.await(10, TimeUnit.SECONDS));
        receiveTrigger.blockLast();
        worker.close();

        Worker newWorker = applicationContext.createBean(TestMethodScopedWorker.class, IdUtils.create(), 1, workerGroup);
        newWorker.run();
        assertThat(countDownLatch.await(30, TimeUnit.SECONDS)).isTrue();

        receive.blockLast();
        newWorker.close();
    }

    @MockBean(WorkerGroupService.class)
    WorkerGroupService workerGroupService() {
        return new WorkerGroupService() {
            @Override
            public String resolveGroupFromKey(String workerGroupKey) {
                return workerGroupKey;
            }
        };
    }

    private WorkerTask workerTask(Duration sleep) {
        return workerTask(sleep, null);
    }

    private WorkerTask workerTask(Duration sleep, String workerGroupKey) {
        Sleep bash = Sleep.builder()
            .type(Sleep.class.getName())
            .id("unit-test")
            .duration(io.kestra.core.models.property.Property.ofValue(sleep))
            .workerGroup(workerGroupKey != null ? new WorkerGroup(workerGroupKey, null) : null)
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
        return workerTrigger(sleep, null);
    }

    private WorkerTrigger workerTrigger(Duration sleep, String workerGroupKey) {
        SleepTrigger trigger = SleepTrigger.builder()
            .type(SleepTrigger.class.getName())
            .id("unit-test")
            .duration(sleep.toMillis())
            .workerGroup(workerGroupKey != null ? new WorkerGroup(workerGroupKey, null) : null)
            .build();

        Map.Entry<ConditionContext, Trigger> mockedTrigger = TestsUtils.mockTrigger(runContextFactory, trigger);

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
            .duration(io.kestra.core.models.property.Property.ofValue(sleep))
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
