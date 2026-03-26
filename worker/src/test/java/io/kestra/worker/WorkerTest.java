package io.kestra.worker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.queues.BroadcastQueueInterface;
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
    private BroadcastQueueInterface<ExecutionKilled> executionKilledQueue;

    @Inject
    private DispatchQueueInterface<LogEntry> workerTaskLogQueue;

    @Inject
    private RunContextFactory runContextFactory;

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
    @FlakyTest
    void shouldKillTasksWhenExecutionKillEventReceived() throws InterruptedException, QueueException {
        // Given
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        workerTaskLogQueue.addListener(logs::add);

        List<WorkerTaskResult> results = new CopyOnWriteArrayList<>();
        workerTaskResultQueue.addListener(results::add);

        // we emit 4 tasks that will last 60 seconds, and one that will last 1 second.
        // We will kill the 4 first ones, but not the last one.
        String executionId = IdUtils.create();

        try (Worker worker = applicationContext.createBean(Worker.class)) {
            worker.start(1, null);

            workerJobEventQueue.emit(null, WorkerJobEvent.of(workerTask(Duration.ofSeconds(60), executionId), null));
            workerJobEventQueue.emit(null, WorkerJobEvent.of(workerTask(Duration.ofSeconds(60), executionId), null));
            workerJobEventQueue.emit(null, WorkerJobEvent.of(workerTask(Duration.ofSeconds(60), executionId), null));
            workerJobEventQueue.emit(null, WorkerJobEvent.of(workerTask(Duration.ofSeconds(60), executionId), null));
            workerJobEventQueue.emit(null, WorkerJobEvent.of(workerTask(Duration.ofSeconds(1)), null));

            Thread.sleep(500);

            // When
            ExecutionKilledExecution killedExecution = ExecutionKilledExecution.builder()
                .executionId(executionId)
                .state(ExecutionKilled.State.EXECUTED)
                .build();
            executionKilledQueue.emit(killedExecution);

            await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> results.stream().filter(r -> r.getTaskRun().getState().isTerminated()).count() == 5);

            // Then
            WorkerTaskResult oneKilled = results.stream()
                .filter(r -> r.getTaskRun().getState().getCurrent() == State.Type.KILLED)
                .findFirst()
                .orElseThrow();
            assertThat(oneKilled.getTaskRun().getState().getHistories()).hasSize(3);

            WorkerTaskResult oneNotKilled = results.stream()
                .filter(r -> r.getTaskRun().getState().getCurrent() == State.Type.SUCCESS)
                .findFirst()
                .orElseThrow();
            assertThat(oneNotKilled.getTaskRun().getState().getHistories()).hasSize(3);

            // child process is stopped and we never received 3 logs
            Thread.sleep(1000);
            assertThat(logs.stream().filter(logEntry -> logEntry.getMessage().equals("3")).count()).isEqualTo(0L);
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