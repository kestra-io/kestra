package io.kestra.executor;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.WorkerGroup;
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
import io.kestra.core.scheduler.events.TriggerEvaluated;
import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.scheduler.events.TriggerReceived;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.services.MaintenanceService;
import io.kestra.core.services.WorkerGroupService;
import io.kestra.core.tasks.test.SleepTrigger;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.flow.Sleep;
import io.kestra.worker.WorkerAgent;
import io.kestra.worker.WorkerJobExecutor;
import io.kestra.worker.fetchers.WorkerJobFetcher;
import io.kestra.worker.senders.WorkerIOSender;
import io.kestra.worker.services.WorkerConnectionService;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@KestraTest(environments =  {"test", "liveness"}, startRunner = true, startWorker = false, startScheduler = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // must be per-class to allow calling once init() which took a lot of time
public abstract class AbstractServiceLivenessCoordinatorTest {

    public static final String WORKER_GROUP_KEY = "workerGroupKey";

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
    @ValueSource(strings = {WORKER_GROUP_KEY, "<null>"})
    public void shouldResubmitTaskWhenWorkerIsStopped(String workerGroupKey) throws Exception {
        workerGroupKey = "<null>".equals(workerGroupKey) ? null : workerGroupKey;
        CountDownLatch runningLatch = new CountDownLatch(1);
        CountDownLatch resubmitLatch = new CountDownLatch(1);

        // GIVEN - create first worker with worker group key "workerGroupKey".
        Worker worker = newWorker();
        worker.start(1, workerGroupKey);

        final WorkerTask workerTask = workerTask(Duration.ofSeconds(5), workerGroupKey);
        final AtomicReference<WorkerTaskResult> workerTaskResult = new AtomicReference<>();
        workerTaskResultQueue.addListener(item -> {
            if (item.uid().equals(workerTask.uid())) {
                if (item.getTaskRun().getState().getCurrent() == State.Type.SUCCESS) {
                    resubmitLatch.countDown();
                    workerTaskResult.set(item);
                }

                if (item.getTaskRun().getState().getCurrent() == State.Type.RUNNING) {
                    runningLatch.countDown();
                }
            }
        });
        workerJobEventQueue.emit(workerGroupKey, WorkerJobEvent.of(workerTask, workerGroupKey));
        boolean runningLatchAwait = runningLatch.await(10, TimeUnit.SECONDS);
        assertThat(runningLatchAwait).isTrue();

        // WHEN - stop first worker.
        worker.close(); // stop processing task
        Thread.sleep(Duration.ofSeconds(5).toMillis());

        // WHEN - create second worker (this will revoke previously one).
        Worker newWorker = newWorker();
        newWorker.start(1, workerGroupKey);

        // THEN - task should be re-emitted to the same worker group and processed successfully.
        assertThat(resubmitLatch.await(10, TimeUnit.SECONDS)).isTrue();
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
                applicationContext.createBean(WorkerIOSender.class, Qualifiers.byName("taskResultSender")),
                applicationContext.createBean(WorkerIOSender.class, Qualifiers.byName("triggerResultSender")),
                applicationContext.createBean(WorkerIOSender.class, Qualifiers.byName("logEntrySender")),
                applicationContext.createBean(WorkerIOSender.class, Qualifiers.byName("metricsSender"))
            ),
            applicationContext.getBean(MaintenanceService.class),
            applicationContext.getBean(MetricRegistry.class),
            applicationContext.getBean(ServerConfig.class)
        );
    }

    @Test
    void shouldNotResubmitTaskForIgnoredExecution() throws Exception {
        CountDownLatch runningLatch = new CountDownLatch(1);

        Worker worker = newWorker();
        worker.start(1, null);

        WorkerTask workerTask = workerTask(Duration.ofSeconds(5));
        ignoreExecutionService.setIgnoredExecutions(List.of(workerTask.getTaskRun().getExecutionId()));

        var taskResults = new ArrayList<WorkerTaskResult>();
        workerTaskResultQueue.addListener(item -> {
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
        boolean runningLatchAwait = runningLatch.await(10, TimeUnit.SECONDS);
        assertThat(runningLatchAwait).isTrue();
        worker.close();

        Worker newWorker = newWorker();
        newWorker.start(1, null);

        // wait a little to be sure there is no resubmit
        Thread.sleep(500);
        newWorker.close();
        assertThat(taskResults.getLast().getTaskRun().getState().getCurrent()).isNotEqualTo(State.Type.SUCCESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {WORKER_GROUP_KEY, "<null>"})
    public void shouldResubmitTriggerWhenWorkerIsStopped(String workerGroupKey) throws Exception {
        workerGroupKey = "<null>".equals(workerGroupKey) ? null : workerGroupKey;
        // Given - create first worker.
        WorkerAgent worker = (WorkerAgent) newWorker();
        worker.start(1, workerGroupKey);

        WorkerTrigger workerTrigger = workerTrigger(Duration.ofSeconds(5), workerGroupKey);

        CountDownLatch evaluatedLatch = new CountDownLatch(1);
        CountDownLatch receivedLatch = new CountDownLatch(1);
        triggerEventQueue.addListener(event -> {
            if (!event.uid().equals(workerTrigger.uid())) {
                return;
            }
            if (event instanceof TriggerReceived) {
                receivedLatch.countDown();
            }
            if (event instanceof TriggerEvaluated) {
                evaluatedLatch.countDown();
            }
        });

        workerJobEventQueue.emit(workerGroupKey, WorkerJobEvent.of(workerTrigger, workerGroupKey));
        assertThat(receivedLatch.await(30, TimeUnit.SECONDS)).isTrue();
        // WHEN - stop first worker.
        worker.stopNow();  // simulate a non-graceful stop (hard shutdown, crash, etc.).

        // WHEN - create second worker (this will revoke previously one).
        WorkerAgent newWorker = (WorkerAgent) newWorker();
        newWorker.start(1, workerGroupKey);

        // THEN
        assertThat(evaluatedLatch.await(30, TimeUnit.SECONDS)).isTrue();
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
            .data(WorkerTaskData.from(runContextFactory.of(ImmutableMap.of("key", "value"))))
            .task(bash)
            .taskRun(TaskRun.of(execution, resolvedTask))
            .build();
    }

    private WorkerTrigger workerTrigger(Duration sleep, String workerGroupKey) {
        SleepTrigger trigger = SleepTrigger.builder()
            .type(SleepTrigger.class.getName())
            .id("unit-test")
            .duration(sleep.toMillis())
            .workerGroup(workerGroupKey != null ? new WorkerGroup(workerGroupKey, null) : null)
            .build();

        Map.Entry<ConditionContext, TriggerState> mockedTrigger = TestsUtils.mockTrigger(runContextFactory, trigger);

        return WorkerTrigger.builder()
            .trigger(trigger)
            .triggerContext(mockedTrigger.getValue().context())
            .data(WorkerTriggerData.from(mockedTrigger.getKey()))
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