package io.kestra.worker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskData;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.worker.Controller;
import io.kestra.plugin.core.flow.Pause;
import io.kestra.plugin.core.flow.Sleep;
import io.kestra.plugin.core.flow.WorkingDirectory;

import io.kestra.controller.grpc.services.WorkerJobDispatcher;
import io.kestra.worker.services.ExecutionKilledManager;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

@KestraTest(rebuildContext = true)
class WorkerTest {

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue;

    @Inject
    private DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    private ExecutionKilledManager executionKilledManager;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private WorkerJobDispatcher workerJobDispatcher;

    private Controller controller;

    @BeforeEach
    void setUp() {
        controller = applicationContext.createBean(Controller.class);
        controller.start();
    }

    @AfterEach
    void tearDown() {
        controller.close();
    }

    @Test
    void shouldSucceedWhenTaskIsExecuted() throws QueueException {
        // Given
        List<WorkerTaskResult> results = new ArrayList<>();
        workerTaskResultQueue.addListener(results::add);

        // When
        try (Worker worker = applicationContext.createBean(Worker.class)) {
            worker.start(1, null);
            workerJobEventQueue.emit(null, WorkerJobEvent.of(workerTask(Duration.ofSeconds(1)), null));

            await()
                .atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> !results.isEmpty() && results.getLast().getTaskRun().getState().isTerminated());
        }

        // Then
        assertThat(results.getLast().getTaskRun().getState().getHistories()).hasSize(3);
    }

    @Test
    void shouldFailWhenWorkerReceivesFlowableTask() throws QueueException {
        // Given
        Pause pause = Pause.builder()
            .type(Pause.class.getName())
            .pauseDuration(Property.ofValue(Duration.ofSeconds(1)))
            .id("unit-test")
            .build();

        WorkingDirectory flowableTask = WorkingDirectory.builder()
            .type(WorkingDirectory.class.getName())
            .id("worker-unit-test")
            .tasks(List.of(pause))
            .build();

        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unit-test")
            .tasks(List.of(flowableTask))
            .build();

        Execution execution = TestsUtils.mockExecution(flow, Map.of());
        ResolvedTask resolvedTask = ResolvedTask.of(pause);
        WorkerTask workerTask = WorkerTask.builder()
            .data(WorkerTaskData.from(runContextFactory.of(Map.of("key", "value"))))
            .task(flowableTask)
            .taskRun(TaskRun.of(execution, resolvedTask))
            .build();

        List<WorkerTaskResult> results = new ArrayList<>();
        workerTaskResultQueue.addListener(results::add);

        // When
        try (Worker worker = applicationContext.createBean(Worker.class)) {
            worker.start(1, null);
            workerJobEventQueue.emit(null, WorkerJobEvent.of(workerTask, null));

            await()
                .atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> !results.isEmpty() && results.getLast().getTaskRun().getState().isFailed());
        }

        // Then
        assertThat(results.getLast().getTaskRun().getState().getHistories()).hasSize(3);
    }

    @Test
    void shouldKillTasksWhenExecutionKillEventReceived() throws QueueException {
        // Given
        List<WorkerTaskResult> results = new CopyOnWriteArrayList<>();
        workerTaskResultQueue.addListener(results::add);

        String executionId = IdUtils.create();

        try (Worker worker = applicationContext.createBean(Worker.class)) {
            worker.start(2, null);

            await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> workerJobDispatcher.getActiveWorkerCount() > 0);

            // one long task to kill, one short task with a different executionId to keep
            workerJobEventQueue.emit(null, WorkerJobEvent.of(workerTask(Duration.ofSeconds(60), executionId), null));
            workerJobEventQueue.emit(null, WorkerJobEvent.of(workerTask(Duration.ofSeconds(1)), null));

            // Wait until both jobs are running AND the kill target has transitioned to RUNNING
            // state. getRunningJobs() is satisfied before the task-run state is updated, so
            // sending the kill without the second condition races and produces [CREATED, KILLED]
            // instead of [CREATED, RUNNING, KILLED].
            await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> worker.getRunningJobs().size() >= 2
                    && results.stream().anyMatch(r ->
                        r.getTaskRun().getExecutionId().equals(executionId)
                        && r.getTaskRun().getState().getCurrent() == State.Type.RUNNING));

            // When
            ExecutionKilledExecution killedExecution = ExecutionKilledExecution.builder()
                .executionId(executionId)
                .build();
            executionKilledManager.onKillReceived(killedExecution);

            await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> results.stream().filter(r -> r.getTaskRun().getState().isTerminated()).count() == 2);

            // Then
            WorkerTaskResult killed = results.stream()
                .filter(r -> r.getTaskRun().getState().getCurrent() == State.Type.KILLED)
                .findFirst()
                .orElseThrow();
            assertThat(killed.getTaskRun().getState().getHistories()).hasSize(3);

            WorkerTaskResult succeeded = results.stream()
                .filter(r -> r.getTaskRun().getState().getCurrent() == State.Type.SUCCESS)
                .findFirst()
                .orElseThrow();
            assertThat(succeeded.getTaskRun().getState().getHistories()).hasSize(3);
        }
    }

    @Test
    void shouldCreateInstanceGivenApplicationContext() {
        assertThatCode(() ->
        {
            try (var worker = applicationContext.getBean(Worker.class)) {
                // do nothing
            }
        }).doesNotThrowAnyException();
    }

    private WorkerTask workerTask(Duration duration) {
        return workerTask(duration, IdUtils.create());
    }

    private WorkerTask workerTask(Duration duration, String executionId) {
        Sleep bash = Sleep.builder()
            .type(Sleep.class.getName())
            .id("unit-test")
            .duration(Property.ofValue(duration))
            .build();

        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unit-test")
            .tasks(List.of(bash))
            .build();

        Execution execution = TestsUtils.mockExecution(flow, Map.of());
        execution = execution.toBuilder().id(executionId).build();

        ResolvedTask resolvedTask = ResolvedTask.of(bash);

        return WorkerTask.builder()
            .data(WorkerTaskData.from(runContextFactory.of(Map.of("key", "value"))))
            .task(bash)
            .taskRun(TaskRun.of(execution, resolvedTask))
            .build();
    }
}