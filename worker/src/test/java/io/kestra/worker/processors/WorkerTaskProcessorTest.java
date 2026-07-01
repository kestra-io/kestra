package io.kestra.worker.processors;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.runners.RunContextLoggerFactory;
import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskData;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.trace.TracerFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.worker.WorkerGroups;
import io.kestra.worker.WorkerSecurityService;
import io.kestra.worker.queues.InMemoryWorkerQueue;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.services.ExecutionKilledManager;

import jakarta.inject.Inject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Component-level tests for {@link WorkerTaskProcessor}'s behavior when the worker is shutting down,
 * covering the regression from <a href="https://github.com/kestra-io/kestra/issues/17124">#17124</a>:
 * a task that fails on its own during the termination grace period must still have its terminal
 * result emitted, while a task the shutdown actually interrupted is deferred for resubmission.
 */
@KestraTest
class WorkerTaskProcessorTest {

    @Inject
    private ServerConfig serverConfig;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    private WorkerSecurityService workerSecurityService;

    @Inject
    private TracerFactory tracerFactory;

    @Inject
    private RunContextInitializer runContextInitializer;

    @Inject
    private RunContextLoggerFactory runContextLoggerFactory;

    @Inject
    private ExecutionKilledManager executionKilledManager;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldEmitFailedResultWhenTaskFailsOnItsOwnDuringShutdownDrain() throws Exception {
        // Given a processor in the graceful drain window (stopped) that did NOT interrupt the task
        InMemoryWorkerQueue<WorkerTaskResult> resultQueue = new InMemoryWorkerQueue<>(100);
        WorkerTaskProcessor processor = newProcessor(resultQueue);
        processor.stop();

        // When the task reaches a FAILED state on its own during the drain window
        processor.process(failingWorkerTask());

        // Then its terminal FAILED result is emitted, not silently dropped
        List<WorkerTaskResult> results = drain(resultQueue);
        assertThat(results)
            .as("a genuine failure during the drain window must be emitted, not dropped (#17124)")
            .anyMatch(result -> result.getTaskRun().getState().isFailed());
    }

    @Test
    void shouldDropFailedResultWhenTaskWasInterruptedByShutdown() throws Exception {
        // Given a processor whose task is forcibly interrupted by the shutdown
        InMemoryWorkerQueue<WorkerTaskResult> resultQueue = new InMemoryWorkerQueue<>(100);
        WorkerTaskProcessor processor = newProcessor(resultQueue);
        processor.signalShutdownInterrupt();
        processor.stop();

        // When the interrupted task ends in a failed state
        processor.process(failingWorkerTask());

        // Then no terminal result is emitted (it will be resubmitted) — only the RUNNING preamble
        List<WorkerTaskResult> results = drain(resultQueue);
        assertThat(results)
            .as("an interrupted task's failure must be deferred for resubmission, not reported")
            .noneMatch(result -> result.getTaskRun().getState().isFailed());
    }

    private WorkerTaskProcessor newProcessor(WorkerQueue<WorkerTaskResult> resultQueue) {
        return new WorkerTaskProcessor(
            "test-worker",
            WorkerGroups.DEFAULT_ID,
            serverConfig,
            metricRegistry,
            workerSecurityService,
            tracerFactory.getTracer(Worker.class, "WORKER"),
            runContextInitializer,
            runContextLoggerFactory,
            resultQueue,
            new InMemoryWorkerQueue<>(100),
            executionKilledManager
        );
    }

    private WorkerTask failingWorkerTask() {
        AlwaysFail task = AlwaysFail.builder()
            .type(AlwaysFail.class.getName())
            .id("fail-task")
            .build();

        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unit-test")
            .tasks(List.of(task))
            .build();

        Execution execution = TestsUtils.mockExecution(flow, Map.of());
        ResolvedTask resolvedTask = ResolvedTask.of(task);

        return WorkerTask.builder()
            .data(WorkerTaskData.from(runContextFactory.of(Map.of("key", "value"))))
            .task(task)
            .taskRun(TaskRun.of(execution, resolvedTask))
            .build();
    }

    private static List<WorkerTaskResult> drain(WorkerQueue<WorkerTaskResult> queue) throws InterruptedException {
        List<WorkerTaskResult> results = new ArrayList<>();
        WorkerTaskResult result;
        while ((result = queue.poll(Duration.ZERO)) != null) {
            results.add(result);
        }
        return results;
    }

    /**
     * A task that always fails on its own (throws when run), modeling e.g. a script container exiting
     * non-zero. Constructed and executed directly by the processor, so no plugin registration is needed.
     */
    @SuperBuilder
    @Getter
    @NoArgsConstructor
    public static class AlwaysFail extends Task implements RunnableTask<VoidOutput> {
        @Override
        public VoidOutput run(RunContext runContext) {
            throw new RuntimeException("simulated task failure during shutdown drain");
        }
    }
}
