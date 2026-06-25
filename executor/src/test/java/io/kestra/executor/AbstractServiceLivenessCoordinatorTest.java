package io.kestra.executor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.WorkerSelector;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.VNodeDispatchQueueInterface;
import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskData;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.runners.WorkerTriggerData;
import io.kestra.core.worker.models.WorkerTriggerResult;
import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.scheduler.events.TriggerReceived;
import io.kestra.core.scheduler.events.TriggerWorkerLost;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.services.MaintenanceService;
import io.kestra.core.services.WorkerQueueService;
import io.kestra.core.tasks.test.SleepTrigger;
import io.kestra.core.utils.CountDownLatchTask;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.worker.WorkerAgent;
import io.kestra.worker.WorkerJobExecutor;
import io.kestra.worker.fetchers.WorkerJobFetcher;
import io.kestra.worker.senders.GrpcWorkerIOSender;
import io.kestra.worker.services.WorkerConnectionService;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@KestraTest(environments = { "test", "liveness" }, startRunner = true, startWorker = false, startScheduler = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // must be per-class to allow calling once init() which took a lot of time
public abstract class AbstractServiceLivenessCoordinatorTest {

    public static final String WORKER_QUEUE_UID = "worker-queue-id";

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private TestRunContextFactory runContextFactory;

    @Inject
    private KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue;

    @Inject
    private DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    private VNodeDispatchQueueInterface<TriggerEvent> triggerEventQueue;

    @Inject
    private DefaultServiceLivenessCoordinator jdbcServiceLivenessHandler;

    @Inject
    private IgnoreExecutionService ignoreExecutionService;

    @BeforeAll
    void init() {
        // Simulate that executor and workers are not running on the same JVM.
        jdbcServiceLivenessHandler.setServerInstance(IdUtils.create());
    }

    @ParameterizedTest
    @ValueSource(strings = { WORKER_QUEUE_UID, "<null>" })
    public void shouldResubmitTaskWhenWorkerIsStopped(String workerQueueId) throws Exception {
        workerQueueId = "<null>".equals(workerQueueId) ? null : workerQueueId;

        CountDownLatch holdLatch = new CountDownLatch(1);
        CountDownLatch runningLatch = new CountDownLatch(1);
        CountDownLatch resubmitLatch = new CountDownLatch(1);

        // GIVEN - create first worker with worker group key "workerQueueId".
        Worker worker = newWorker();
        worker.start(1);

        final WorkerTask workerTask = workerTaskWithLatch(holdLatch, workerQueueId);
        final AtomicReference<WorkerTaskResult> workerTaskResult = new AtomicReference<>();
        workerTaskResultQueue.addListener(item ->
        {
            if (item.uid().equals(workerTask.uid())) {
                if (item.getTaskRun().getState().getCurrent() == State.Type.SUCCESS) {
                    workerTaskResult.set(item);
                    resubmitLatch.countDown();
                }

                if (item.getTaskRun().getState().getCurrent() == State.Type.RUNNING) {
                    runningLatch.countDown();
                }
            }
        });
        workerJobEventQueue.emit(null, WorkerJobEvent.of(workerTask, null));
        assertThat(runningLatch.await(30, TimeUnit.SECONDS)).isTrue();

        // WHEN - stop first worker. The task is guaranteed to still be running because it is
        // blocked on holdLatch.
        worker.close();

        // Release the hold so the re-submitted task on worker 2 can complete immediately.
        holdLatch.countDown();

        // WHEN - create second worker (this will revoke previously one).
        Worker newWorker = newWorker();
        newWorker.start(1);

        // THEN - task should be re-emitted to the same worker group and processed successfully.
        assertThat(resubmitLatch.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(workerTaskResult.get()).isNotNull();
        assertThat(workerTaskResult.get().getTaskRun().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(workerTaskResult.get().getTaskRun().getAttempts()).hasSize(2);
        assertThat(workerTaskResult.get().getTaskRun().getAttempts().getFirst().getState().getHistories().stream().anyMatch(it -> it.getState() == State.Type.RESUBMITTED)).isTrue();
        newWorker.close();
    }

    private Worker newWorker() {
        // new instance to be able to start multiple workers in the same JVM
        return new WorkerAgent(
            applicationContext.getEventPublisher(ServiceStateChangeEvent.class),
            applicationContext.createBean(WorkerConnectionService.class),
            applicationContext.createBean(WorkerJobExecutor.class),
            applicationContext.createBean(WorkerJobFetcher.class),
            List.of(
                applicationContext.createBean(GrpcWorkerIOSender.class, Qualifiers.byTypeArguments(WorkerTaskResult.class)),
                applicationContext.createBean(GrpcWorkerIOSender.class, Qualifiers.byTypeArguments(WorkerTriggerResult.class)),
                applicationContext.createBean(GrpcWorkerIOSender.class, Qualifiers.byTypeArguments(LogEntry.class)),
                applicationContext.createBean(GrpcWorkerIOSender.class, Qualifiers.byTypeArguments(MetricEntry.class))
            ),
            applicationContext.getBean(MaintenanceService.class),
            applicationContext.getBean(MetricRegistry.class),
            applicationContext.getBean(ServerConfig.class)
        );
    }

    @Test
    void shouldNotResubmitTaskForIgnoredExecution() throws Exception {
        // holdLatch keeps the task blocked, guaranteeing it is still running when the worker stops.
        CountDownLatch holdLatch = new CountDownLatch(1);
        CountDownLatch runningLatch = new CountDownLatch(1);

        Worker worker = newWorker();
        worker.start(1);

        WorkerTask workerTask = workerTaskWithLatch(holdLatch, null);
        ignoreExecutionService.setIgnoredExecutions(List.of(workerTask.getTaskRun().getExecutionId()));

        var taskResults = new ArrayList<WorkerTaskResult>();
        workerTaskResultQueue.addListener(item ->
        {
            if (!item.uid().equals(workerTask.uid())) {
                return;
            }
            taskResults.add(item);
            if (item.getTaskRun().getState().getCurrent() == State.Type.SUCCESS) {
                // no resubmit should happen!
                fail();
            }

            if (item.getTaskRun().getState().getCurrent() == State.Type.RUNNING) {
                runningLatch.countDown();
            }
        });

        workerJobEventQueue.emit(null, WorkerJobEvent.of(workerTask, null));
        assertThat(runningLatch.await(30, TimeUnit.SECONDS)).isTrue();

        // Task is held by holdLatch, guaranteed still running when we stop the worker.
        worker.close();

        Worker newWorker = newWorker();
        newWorker.start(1);

        // wait a little to be sure there is no resubmit
        Thread.sleep(500);
        holdLatch.countDown();
        newWorker.close();
        assertThat(taskResults.getLast().getTaskRun().getState().getCurrent()).isNotEqualTo(State.Type.SUCCESS);
    }

    @ParameterizedTest
    @ValueSource(strings = { WORKER_QUEUE_UID, "<null>" })
    public void shouldNotifyTriggerWorkerLostWhenWorkerIsStopped(String workerQueueId) throws Exception {
        workerQueueId = "<null>".equals(workerQueueId) ? null : workerQueueId;
        // Given - create first worker.
        WorkerAgent worker = (WorkerAgent) newWorker();
        worker.start(1);

        WorkerTrigger workerTrigger = workerTrigger(Duration.ofSeconds(5), workerQueueId);

        CountDownLatch lostLatch = new CountDownLatch(1);
        CountDownLatch receivedLatch = new CountDownLatch(1);
        triggerEventQueue.addListener(event ->
        {
            if (!event.uid().equals(workerTrigger.uid())) {
                return;
            }
            if (event instanceof TriggerReceived) {
                receivedLatch.countDown();
            }
            if (event instanceof TriggerWorkerLost) {
                lostLatch.countDown();
            }
        });

        workerJobEventQueue.emit(null, WorkerJobEvent.of(workerTrigger, null));
        assertThat(receivedLatch.await(30, TimeUnit.SECONDS)).isTrue();
        // WHEN - stop first worker.
        worker.stopNow(); // simulate a non-graceful stop (hard shutdown, crash, etc.).

        // WHEN - create second worker (this will revoke previously one).
        WorkerAgent newWorker = (WorkerAgent) newWorker();
        newWorker.start(1);

        // THEN - the scheduler is notified instead of the job being re-emitted to a worker.
        assertThat(lostLatch.await(30, TimeUnit.SECONDS)).isTrue();
        newWorker.close();
    }

    @MockBean(WorkerQueueService.class)
    WorkerQueueService workerGroupService() {
        return new WorkerQueueService.Default();
    }

    private WorkerTask workerTaskWithLatch(CountDownLatch holdLatch, String workerQueueId) {
        WorkerSelector workerSelector = workerQueueId != null
            ? new WorkerSelector(java.util.List.of(workerQueueId), null)
            : null;
        // signalLatch satisfies the @NotNull countDownLatchKey constraint; not used in assertions.
        CountDownLatchTask task = CountDownLatchTask.getTaskForCountDownLatch(
            new CountDownLatch(1),
            holdLatch,
            Duration.ofSeconds(30),
            workerSelector
        );

        Execution execution = TestsUtils.mockExecution(flowForTask(task), ImmutableMap.of());
        ResolvedTask resolvedTask = ResolvedTask.of(task);

        return WorkerTask.builder()
            .data(WorkerTaskData.from(runContextFactory.of(ImmutableMap.of("key", "value"))))
            .task(task)
            .taskRun(TaskRun.of(execution, resolvedTask))
            .build();
    }

    private WorkerTrigger workerTrigger(Duration sleep, String workerQueueId) {
        SleepTrigger trigger = SleepTrigger.builder()
            .type(SleepTrigger.class.getName())
            .id("unit-test")
            .duration(sleep.toMillis())
            .workerSelector(workerQueueId != null ? new io.kestra.core.models.tasks.WorkerSelector(java.util.List.of(workerQueueId), null) : null)
            .build();

        Map.Entry<ConditionContext, TriggerState> mockedTrigger = TestsUtils.mockTrigger(runContextFactory, trigger);

        return WorkerTrigger.builder()
            .trigger(trigger)
            .data(WorkerTriggerData.from(mockedTrigger.getKey(), mockedTrigger.getValue().context(), Map.of()))
            .build();
    }

    private Flow flowForTask(CountDownLatchTask task) {
        return Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unit-test")
            .tasks(Collections.singletonList(task))
            .build();
    }
}
