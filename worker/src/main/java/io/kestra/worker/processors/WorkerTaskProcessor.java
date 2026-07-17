package io.kestra.worker.processors;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.runners.RunContextLoggerFactory;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.trace.Tracer;
import io.kestra.worker.WorkerSecurityService;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.services.ExecutionKilledManager;

/**
 * Processor executing a {@link WorkerTask} holding a single {@link RunnableTask}.
 */
public class WorkerTaskProcessor extends AbstractWorkerTaskProcessor {

    public WorkerTaskProcessor(final String workerId,
        final String workerGroup,
        final ServerConfig serverConfig,
        final MetricRegistry metricRegistry,
        final WorkerSecurityService workerSecurityService,
        final Tracer tracer,
        final RunContextInitializer runContextInitializer,
        final RunContextLoggerFactory runContextLoggerFactory,
        final WorkerQueue<WorkerTaskResult> workerTaskResultQueue,
        final WorkerQueue<MetricEntry> workerMetricQueue,
        final ExecutionKilledManager executionKilledManager) {
        super(workerId, workerGroup, serverConfig, metricRegistry, workerSecurityService, tracer,
            runContextInitializer, runContextLoggerFactory, workerTaskResultQueue, workerMetricQueue, executionKilledManager);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doProcess(final WorkerTask workerTask) {
        Task task = workerTask.getTask();
        if (!(task instanceof RunnableTask)) {
            throw new IllegalArgumentException("Unable to process the task '" + task.getId() + "' as it's not a runnable task");
        }
        runTask(workerTask, true);
    }
}
