package io.kestra.worker;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.*;
import io.kestra.core.runners.test.TaskThatFail;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(rebuildContext = true)
class WorkerTaskCallableTest {
    @Inject
    ApplicationContext applicationContext;

    @Inject
    @Named(QueueFactoryInterface.WORKERJOB_NAMED)
    QueueInterface<WorkerJob> workerTaskQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void failedTaskWithTimeoutPreservesOutput() throws QueueException {
        DefaultWorker worker = applicationContext.createBean(DefaultWorker.class, IdUtils.create(), 8, null);
        worker.run();

        List<WorkerTaskResult> workerTaskResults = new CopyOnWriteArrayList<>();
        Flux<WorkerTaskResult> receive = TestsUtils.receive(workerTaskResultQueue, either -> workerTaskResults.add(either.getLeft()));

        // Task that fails with output, timeout configured but NOT exceeded
        TaskThatFail task = TaskThatFail.builder()
            .type(TaskThatFail.class.getName())
            .id("fail-with-output")
            .message("preserved-output")
            .timeout(Property.ofValue(Duration.ofSeconds(30)))
            .build();

        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unit-test")
            .tasks(Collections.singletonList(task))
            .build();

        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        ResolvedTask resolvedTask = ResolvedTask.of(task);
        String executionId = execution.getId();

        WorkerTask workerTask = WorkerTask.builder()
            .runContext(runContextFactory.of(ImmutableMap.of("key", "value")))
            .task(task)
            .taskRun(TaskRun.of(execution, resolvedTask))
            .build();

        workerTaskQueue.emit(workerTask);

        Awaitility.await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofMinutes(1))
            .until(
                () -> workerTaskResults.stream()
                    .anyMatch(r -> r.getTaskRun().getExecutionId().equals(executionId) && r.getTaskRun().getState().isTerminated())
            );
        receive.blockLast();
        worker.shutdown();

        WorkerTaskResult result = workerTaskResults.stream()
            .filter(r -> r.getTaskRun().getExecutionId().equals(executionId) && r.getTaskRun().getState().isTerminated())
            .findFirst()
            .orElseThrow();

        assertThat(result.getTaskRun().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(result.getTaskRun().getOutputs()).isNotNull();
        assertThat(result.getTaskRun().getOutputs()).containsEntry("message", "preserved-output");
    }
}
