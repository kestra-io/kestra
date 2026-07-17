package io.kestra.worker.processors;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.runners.RunContextLoggerFactory;
import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.trace.Tracer;
import io.kestra.core.trace.TracerFactory;
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.core.worker.models.WorkerTriggerResult;
import io.kestra.plugin.core.flow.WorkingDirectory;
import io.kestra.worker.WorkerConfig;
import io.kestra.worker.WorkerSecurityService;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.kestra.worker.services.ExecutionKilledManager;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class WorkerJobProcessorFactory {

    @Inject
    private MetricRegistry metricRegistry;
    @Inject
    private WorkerSecurityService workerSecurityService;
    @Inject
    private RunContextInitializer runContextInitializer;
    @Inject
    private RunContextLoggerFactory runContextLoggerFactory;
    @Inject
    private ServerConfig serverConfig;
    // QUEUES
    @Inject
    private WorkerQueueRegistry workerQueueRegistry;

    @Inject
    private ExecutionKilledManager executionKilledManager;

    @Inject
    private WorkerConfig workerConfig;

    @Inject
    private TracerFactory tracerFactory;
    private Tracer tracer;

    @PostConstruct
    public void init() {
        this.tracer = tracerFactory.getTracer(Worker.class, "WORKER");
    }

    @SuppressWarnings("unchecked")
    public <T extends WorkerJob> WorkerJobProcessor<T> create(WorkerContext context, T job) {
        if (job instanceof WorkerTask workerTask) {
            return (WorkerJobProcessor<T>) createWorkerTaskProcessor(context, workerTask);
        } else if (job instanceof WorkerTrigger) {
            return (WorkerJobProcessor<T>) new WorkerTriggerProcessor(
                context.workerGroupId(),
                metricRegistry,
                workerSecurityService,
                tracer,
                runContextInitializer,
                workerQueueRegistry.getOrCreate(context, LogEntry.class),
                workerQueueRegistry.getOrCreate(context, WorkerTriggerResult.class),
                executionKilledManager,
                workerConfig.pollingTriggerTimeout()
            );
        }

        throw new IllegalArgumentException("Unsupported worker job type [" + job.getClass().getName() + "]");
    }

    private AbstractWorkerTaskProcessor createWorkerTaskProcessor(WorkerContext context, WorkerTask workerTask) {
        final String workerId = context.workerId();
        final String workerGroupId = context.workerGroupId();
        final WorkerQueue<WorkerTaskResult> workerTaskResultQueue = workerQueueRegistry.getOrCreate(context, WorkerTaskResult.class);
        final WorkerQueue<MetricEntry> workerMetricQueue = workerQueueRegistry.getOrCreate(context, MetricEntry.class);

        if (workerTask.getTask() instanceof WorkingDirectory) {
            return new WorkingDirectoryTaskProcessor(
                workerId,
                workerGroupId,
                serverConfig,
                metricRegistry,
                workerSecurityService,
                tracer,
                runContextInitializer,
                runContextLoggerFactory,
                workerTaskResultQueue,
                workerMetricQueue,
                executionKilledManager
            );
        }
        return new WorkerTaskProcessor(
            workerId,
            workerGroupId,
            serverConfig,
            metricRegistry,
            workerSecurityService,
            tracer,
            runContextInitializer,
            runContextLoggerFactory,
            workerTaskResultQueue,
            workerMetricQueue,
            executionKilledManager
        );
    }

}
