package io.kestra.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.*;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.core.flow.Pause;
import io.kestra.plugin.core.flow.Sleep;
import io.kestra.plugin.core.flow.WorkingDirectory;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.micronaut.context.ApplicationContext;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.utils.Rethrow.throwSupplier;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(rebuildContext = true)
class WorkerTest {
    @Inject
    ApplicationContext applicationContext;

    @Inject
    DispatchQueueInterface<WorkerJobEvent> workerTaskQueue;

    @Inject
    DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    BroadcastQueueInterface<ExecutionKilled> executionKilledQueue;

    @Inject
    private DispatchQueueInterface<LogEntry> workerTaskLogQueue;

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void success() throws TimeoutException, QueueException {
        Worker worker = applicationContext.createBean(Worker.class);
        worker.start(8, null);

        List<WorkerTaskResult> workerTaskResult = new ArrayList<>();
        workerTaskResultQueue.addListener(workerTaskResult::add);
        workerTaskQueue.emit(WorkerJobEvent.of(workerTask(1000), null));

        Await.until(
            () -> !workerTaskResult.isEmpty() && workerTaskResult.getLast().getTaskRun().getState().isTerminated(),
            Duration.ofMillis(100),
            Duration.ofMinutes(1)
        );
        worker.close();
        assertThat(workerTaskResult.getLast().getTaskRun().getState().getHistories().size()).isEqualTo(3);
    }

    @Test
    void failOnWorkerTaskWithFlowable() throws TimeoutException, QueueException, JsonProcessingException {
        Worker worker = applicationContext.createBean(Worker.class);
        worker.start(8, null);

        List<WorkerTaskResult> workerTaskResult = new ArrayList<>();
        workerTaskResultQueue.addListener(workerTaskResult::add);

        Pause pause = Pause.builder()
            .type(Pause.class.getName())
            .delay(Property.ofValue(Duration.ofSeconds(1)))
            .id("unit-test")
            .build();

        WorkingDirectory theWorkerTask = WorkingDirectory.builder()
            .type(WorkingDirectory.class.getName())
            .id("worker-unit-test")
            .tasks(List.of(pause))
            .build();

        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unit-test")
            .tasks(Collections.singletonList(theWorkerTask))
            .build();

        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ResolvedTask resolvedTask = ResolvedTask.of(pause);

        WorkerTask workerTask = WorkerTask.builder()
            .runContext(runContextFactory.of(ImmutableMap.of("key", "value")))
            .task(theWorkerTask)
            .taskRun(TaskRun.of(execution, resolvedTask))
            .build();

        workerTaskQueue.emit(WorkerJobEvent.of(workerTask, null));

        Await.until(
            throwSupplier(() -> {
                WorkerTaskResult taskResult = workerTaskResult.getLast();
                return "WorkerTaskResult was " + (taskResult == null ? null : JacksonMapper.ofJson().writeValueAsString(taskResult));
            }),
            () -> !workerTaskResult.isEmpty() && workerTaskResult.getLast().getTaskRun().getState().isFailed(),
            Duration.ofMillis(100),
            Duration.ofMinutes(1)
        );
        worker.close();
        assertThat(workerTaskResult.getLast().getTaskRun().getState().getHistories().size()).isEqualTo(3);
    }

    @Test
    void killed() throws InterruptedException, TimeoutException, QueueException {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        workerTaskLogQueue.addListener(logs::add);

        Worker worker = applicationContext.createBean(Worker.class);
        worker.start(8, null);

        List<WorkerTaskResult> workerTaskResult = new CopyOnWriteArrayList<>();
        workerTaskResultQueue.addListener(workerTaskResult::add);
        WorkerTask workerTask = workerTask(999000);

        workerTaskQueue.emit(WorkerJobEvent.of(workerTask, null));
        workerTaskQueue.emit(WorkerJobEvent.of(workerTask, null));
        workerTaskQueue.emit(WorkerJobEvent.of(workerTask, null));
        workerTaskQueue.emit(WorkerJobEvent.of(workerTask, null));
        WorkerTask notKilled = workerTask(2000);
        workerTaskQueue.emit(WorkerJobEvent.of(notKilled, null));

        Thread.sleep(500);

        executionKilledQueue.emit(ExecutionKilledExecution.builder().executionId(workerTask.getTaskRun().getExecutionId()).build());

        Await.until(
            () -> {
                // copy the list to avoid concurrent modification exception if a WorkerTaskResult arrives in the queue
                var copy = new ArrayList<>(workerTaskResult);
                return copy.stream().filter(r -> r.getTaskRun().getState().isTerminated()).count() == 5;
            },
            Duration.ofMillis(100),
            Duration.ofMinutes(1)
        );

        WorkerTaskResult oneKilled = workerTaskResult.stream()
            .filter(r -> r.getTaskRun().getState().getCurrent() == State.Type.KILLED)
            .findFirst()
            .orElseThrow();
        assertThat(oneKilled.getTaskRun().getState().getHistories().size()).isEqualTo(3);
        assertThat(oneKilled.getTaskRun().getState().getCurrent()).isEqualTo(State.Type.KILLED);

        WorkerTaskResult oneNotKilled = workerTaskResult.stream()
            .filter(r -> r.getTaskRun().getState().getCurrent() == State.Type.SUCCESS)
            .findFirst()
            .orElseThrow();
        assertThat(oneNotKilled.getTaskRun().getState().getHistories().size()).isEqualTo(3);
        assertThat(oneNotKilled.getTaskRun().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // child process is stopped and we never received 3 logs
        Thread.sleep(1000);
        worker.close();
        assertThat(logs.stream().filter(logEntry -> logEntry.getMessage().equals("3")).count()).isEqualTo(0L);
    }

    @Test
    void shouldCreateInstanceGivenApplicationContext() {
        Assertions.assertDoesNotThrow(() -> {
            try (var worker = applicationContext.getBean(Worker.class)) {
                // do nothing
            }
        });
    }

    private WorkerTask workerTask(long sleepDuration) {
        Sleep bash = Sleep.builder()
            .type(Sleep.class.getName())
            .id("unit-test")
            .duration(Property.ofValue(Duration.ofMillis(sleepDuration)))
            .build();

        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unit-test")
            .tasks(Collections.singletonList(bash))
            .build();

        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        ResolvedTask resolvedTask = ResolvedTask.of(bash);

        return WorkerTask.builder()
            .runContext(runContextFactory.of(ImmutableMap.of("key", "value")))
            .task(bash)
            .taskRun(TaskRun.of(execution, resolvedTask))
            .build();
    }
}
