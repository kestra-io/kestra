package io.kestra.runner.memory;

import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.queues.WorkerTriggerResultQueueInterface;
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
import io.kestra.core.runners.*;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.io.IOException;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@Factory
@MemoryQueueEnabled
public class MemoryQueueFactory implements QueueFactoryInterface {
    @Inject
    ApplicationContext applicationContext;

    @Override
    @Singleton
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    public QueueInterface<Execution> execution() {
        return new MemoryQueue<>(Execution.class, applicationContext);
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
        return new MemoryQueue<>(WorkerJob.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    public QueueInterface<WorkerTaskResult> workerTaskResult() {
        return new MemoryQueue<>(WorkerTaskResult.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTRIGGERRESULT_NAMED)
    public QueueInterface<WorkerTriggerResult> workerTriggerResult() {
        return new MemoryQueue<>(WorkerTriggerResult.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    public QueueInterface<LogEntry> logEntry() {
        return new MemoryQueue<>(LogEntry.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.METRIC_QUEUE)
    public QueueInterface<MetricEntry> metricEntry() {
        return new MemoryQueue<>(MetricEntry.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.FLOW_NAMED)
    public QueueInterface<Flow> flow() {
        return new MemoryQueue<>(Flow.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.KILL_NAMED)
    public QueueInterface<ExecutionKilled> kill() {
        return new MemoryQueue<>(ExecutionKilled.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.TEMPLATE_NAMED)
    public QueueInterface<Template> template() {
        return new MemoryQueue<>(Template.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERINSTANCE_NAMED)
    public QueueInterface<WorkerInstance> workerInstance() {
        return new MemoryQueue<>(WorkerInstance.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERJOBRUNNING_NAMED)
    public QueueInterface<WorkerJobRunning> workerJobRunning() {
        return new MemoryQueue<>(WorkerJobRunning.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.TRIGGER_NAMED)
    public QueueInterface<Trigger> trigger() {
        return new MemoryQueue<>(Trigger.class, applicationContext);
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
        return new MemoryQueue<>(SubflowExecutionResult.class, applicationContext);
    }

    @PreDestroy
    void closeAllQueue() throws IOException {
        this.applicationContext.getBeansOfType(MemoryQueue.class).forEach(throwConsumer(queue -> queue.close()));
    }
}
