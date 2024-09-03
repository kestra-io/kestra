package io.kestra.core.runners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.core.flow.Pause;
import io.kestra.plugin.core.flow.WorkingDirectory;
import io.kestra.core.tasks.test.Sleep;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static io.kestra.core.utils.Rethrow.throwSupplier;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@KestraTest
class WorkerTest {
    @Inject
    ApplicationContext applicationContext;

    @Inject
    @Named(QueueFactoryInterface.WORKERJOB_NAMED)
    QueueInterface<WorkerJob> workerTaskQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    @Named(QueueFactoryInterface.KILL_NAMED)
    QueueInterface<ExecutionKilled> executionKilledQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> workerTaskLogQueue;

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void success() throws TimeoutException, QueueException {
        Worker worker = applicationContext.createBean(Worker.class, IdUtils.create(), 8, null);
        worker.run();

        AtomicReference<WorkerTaskResult> workerTaskResult = new AtomicReference<>(null);
        Flux<WorkerTaskResult> receive = TestsUtils.receive(workerTaskResultQueue, either -> workerTaskResult.set(either.getLeft()));

        workerTaskQueue.emit(workerTask(1000));

        Await.until(
            () -> workerTaskResult.get() != null && workerTaskResult.get().getTaskRun().getState().isTerminated(),
            Duration.ofMillis(100),
            Duration.ofMinutes(1)
        );
        receive.blockLast();
        worker.shutdown();

        assertThat(workerTaskResult.get().getTaskRun().getState().getHistories().size(), is(3));
    }

    @Test
    void workerGroup() {
        Worker worker = applicationContext.createBean(Worker.class, IdUtils.create(), 8, "toto");
        assertThat(worker.getWorkerGroup(), nullValue());
    }

    @Test
    void failOnWorkerTaskWithFlowable() throws TimeoutException, QueueException, JsonProcessingException {
        Worker worker = applicationContext.createBean(Worker.class, IdUtils.create(), 8, null);
        worker.run();

        AtomicReference<WorkerTaskResult> workerTaskResult = new AtomicReference<>(null);
        Flux<WorkerTaskResult> receive = TestsUtils.receive(workerTaskResultQueue, either -> workerTaskResult.set(either.getLeft()));

        Pause pause = Pause.builder()
            .type(Pause.class.getName())
            .delay(Duration.ofSeconds(1))
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

        workerTaskQueue.emit(workerTask);

        Await.until(
            throwSupplier(() -> {
                WorkerTaskResult taskResult = workerTaskResult.get();
                return "WorkerTaskResult was " + (taskResult == null ? null : JacksonMapper.ofJson().writeValueAsString(taskResult));
            }),
            () -> workerTaskResult.get() != null && workerTaskResult.get().getTaskRun().getState().isFailed(),
            Duration.ofMillis(100),
            Duration.ofMinutes(1)
        );
        receive.blockLast();
        worker.shutdown();

        assertThat(workerTaskResult.get().getTaskRun().getState().getHistories().size(), is(3));
    }

    @Test
    void killed() throws InterruptedException, TimeoutException, QueueException {
        Flux<LogEntry> receiveLogs = TestsUtils.receive(workerTaskLogQueue);

        Worker worker = applicationContext.createBean(Worker.class, IdUtils.create(), 8, null);
        worker.run();

        List<WorkerTaskResult> workerTaskResult = new ArrayList<>();
        Flux<WorkerTaskResult> receiveWorkerTaskResults = TestsUtils.receive(workerTaskResultQueue, either -> workerTaskResult.add(either.getLeft()));

        WorkerTask workerTask = workerTask(999000);

        workerTaskQueue.emit(workerTask);
        workerTaskQueue.emit(workerTask);
        workerTaskQueue.emit(workerTask);
        workerTaskQueue.emit(workerTask);

        WorkerTask notKilled = workerTask(2000);
        workerTaskQueue.emit(notKilled);

        Thread.sleep(500);

        executionKilledQueue.emit(ExecutionKilledExecution.builder().executionId(workerTask.getTaskRun().getExecutionId()).build());

        Await.until(
            () -> workerTaskResult.stream().filter(r -> r.getTaskRun().getState().isTerminated()).count() == 5,
            Duration.ofMillis(100),
            Duration.ofMinutes(1)
        );
        receiveWorkerTaskResults.blockLast();

        WorkerTaskResult oneKilled = workerTaskResult.stream()
            .filter(r -> r.getTaskRun().getState().getCurrent() == State.Type.KILLED)
            .findFirst()
            .orElseThrow();
        assertThat(oneKilled.getTaskRun().getState().getHistories().size(), is(3));
        assertThat(oneKilled.getTaskRun().getState().getCurrent(), is(State.Type.KILLED));

        WorkerTaskResult oneNotKilled = workerTaskResult.stream()
            .filter(r -> r.getTaskRun().getState().getCurrent() == State.Type.SUCCESS)
            .findFirst()
            .orElseThrow();
        assertThat(oneNotKilled.getTaskRun().getState().getHistories().size(), is(3));
        assertThat(oneNotKilled.getTaskRun().getState().getCurrent(), is(State.Type.SUCCESS));

        // child process is stopped and we never received 3 logs
        Thread.sleep(1000);
        worker.shutdown();
        assertThat(receiveLogs.toStream().filter(logEntry -> logEntry.getMessage().equals("3")).count(), is(0L));
    }

    @Test
    void shouldCreateInstanceGivenApplicationContext() {
        Assertions.assertDoesNotThrow(() -> applicationContext.createBean(Worker.class, IdUtils.create(), 8, null));

    }

    private WorkerTask workerTask(long sleepDuration) {
        Sleep bash = Sleep.builder()
            .type(Sleep.class.getName())
            .id("unit-test")
            .duration(sleepDuration)
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
