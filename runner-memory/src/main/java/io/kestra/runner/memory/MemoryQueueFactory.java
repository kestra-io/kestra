package io.kestra.runner.memory;

import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.queues.QueueService;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.queues.WorkerTriggerResultQueueInterface;
import io.kestra.core.runners.Executor;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.core.runners.WorkerInstance;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerJobRunning;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.runners.WorkerTriggerResult;
import io.kestra.core.utils.ExecutorsUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.NotImplementedException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.io.IOException;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@Factory
@MemoryQueueEnabled
public class MemoryQueueFactory implements QueueFactoryInterface {

    private static final String QUEUE_NAME = "memory-queue";

    @Inject
    ApplicationContext applicationContext;

    @Inject
    ExecutorsUtils executorsUtils;

    @Inject
    QueueService queueService;

    @Override
    @Singleton
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    public QueueInterface<Execution> execution() {
        return createQueueForType(Execution.class);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.EXECUTOR_NAMED)
    public QueueInterface<Executor> executor() {
        throw new NotImplementedException();
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERJOB_NAMED)
    public QueueInterface<WorkerJob> workerJob() {
        return createQueueForType(WorkerJob.class);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    public QueueInterface<WorkerTaskResult> workerTaskResult() {
        return createQueueForType(WorkerTaskResult.class);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTRIGGERRESULT_NAMED)
    public QueueInterface<WorkerTriggerResult> workerTriggerResult() {
        return createQueueForType(WorkerTriggerResult.class);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    public QueueInterface<LogEntry> logEntry() {
        return createQueueForType(LogEntry.class);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.METRIC_QUEUE)
    public QueueInterface<MetricEntry> metricEntry() {
        return createQueueForType(MetricEntry.class);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.FLOW_NAMED)
    public QueueInterface<Flow> flow() {
        return createQueueForType(Flow.class);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.KILL_NAMED)
    public QueueInterface<ExecutionKilled> kill() {
        return createQueueForType(ExecutionKilled.class);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.TEMPLATE_NAMED)
    public QueueInterface<Template> template() {
        return createQueueForType(Template.class);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERINSTANCE_NAMED)
    public QueueInterface<WorkerInstance> workerInstance() {
        return createQueueForType(WorkerInstance.class);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERJOBRUNNING_NAMED)
    public QueueInterface<WorkerJobRunning> workerJobRunning() {
        return createQueueForType(WorkerJobRunning.class);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.TRIGGER_NAMED)
    public QueueInterface<Trigger> trigger() {
        return createQueueForType(Trigger.class);
    }

    @Override
    @Prototype // must be prototype so we can create two Worker in the same application context for testing purpose.
    public WorkerJobQueueInterface workerJobQueue() {
        return new MemoryWorkerJobQueue(applicationContext);
    }

    @Override
    @Singleton
    public WorkerTriggerResultQueueInterface workerTriggerResultQueue() {
        return new MemoryWorkerTriggerResultQueue(applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.SUBFLOWEXECUTIONRESULT_NAMED)
    public QueueInterface<SubflowExecutionResult> subflowExecutionResult() {
        return createQueueForType(SubflowExecutionResult.class);
    }

    @PreDestroy
    void closeAllQueue() throws IOException {
        this.applicationContext.getBeansOfType(MemoryQueue.class).forEach(throwConsumer(MemoryQueue::close));
    }

    private <T> MemoryQueue<T> createQueueForType(final Class<T> type) {
        return new MemoryQueue<>(type, queueService, executorsUtils.cachedThreadPool(QUEUE_NAME));
    }
}
