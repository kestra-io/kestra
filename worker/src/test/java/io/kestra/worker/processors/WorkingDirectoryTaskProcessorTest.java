package io.kestra.worker.processors;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
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
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.flow.WorkingDirectory;
import io.kestra.worker.WorkerSecurityService;
import io.kestra.worker.queues.InMemoryWorkerQueue;
import io.kestra.worker.services.ExecutionKilledManager;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class WorkingDirectoryTaskProcessorTest {

    @Inject
    private WorkerJobProcessorFactory workerJobProcessorFactory;

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
    void shouldCreateWorkingDirectoryTaskProcessorWhenTaskIsWorkingDirectory() {
        // Given
        Return child = Return.builder()
            .id("child")
            .type(Return.class.getName())
            .format(io.kestra.core.models.property.Property.ofValue("ok"))
            .build();
        WorkingDirectory workingDirectory = WorkingDirectory.builder()
            .id("wdir")
            .type(WorkingDirectory.class.getName())
            .tasks(List.of(child))
            .build();

        // When
        var processor = workerJobProcessorFactory.create(workerContext(), workerTask(workingDirectory));

        // Then
        assertThat(processor).isInstanceOf(WorkingDirectoryTaskProcessor.class);
    }

    @Test
    void shouldCreateWorkerTaskProcessorWhenTaskIsRunnable() {
        // Given
        Return task = Return.builder()
            .id("task")
            .type(Return.class.getName())
            .format(io.kestra.core.models.property.Property.ofValue("ok"))
            .build();

        // When
        var processor = workerJobProcessorFactory.create(workerContext(), workerTask(task));

        // Then
        assertThat(processor).isInstanceOf(WorkerTaskProcessor.class)
            .isNotInstanceOf(WorkingDirectoryTaskProcessor.class);
    }

    @Test
    void shouldFailWhenTaskIsNotWorkingDirectory() {
        // Given
        Return task = Return.builder()
            .id("task")
            .type(Return.class.getName())
            .format(io.kestra.core.models.property.Property.ofValue("ok"))
            .build();
        WorkingDirectoryTaskProcessor processor = new WorkingDirectoryTaskProcessor(
            "test-worker",
            WorkerGroups.DEFAULT_ID,
            serverConfig,
            metricRegistry,
            workerSecurityService,
            tracerFactory.getTracer(Worker.class, "WORKER"),
            runContextInitializer,
            runContextLoggerFactory,
            new InMemoryWorkerQueue<WorkerTaskResult>(100),
            new InMemoryWorkerQueue<>(100),
            executionKilledManager
        );

        // When / Then
        assertThatThrownBy(() -> processor.process(workerTask(task)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a WorkingDirectory task");
    }

    private WorkerContext workerContext() {
        return new WorkerContext("test-worker", WorkerGroups.DEFAULT_ID, 4);
    }

    private WorkerTask workerTask(Task task) {
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
}
