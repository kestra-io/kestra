package org.kestra.runner.memory;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.runners.*;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

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
    @Named(QueueFactoryInterface.WORKERTASK_NAMED)
    public QueueInterface<WorkerTask> workerTask() {
        return new MemoryQueue<>(WorkerTask.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    public QueueInterface<WorkerTaskResult> workerTaskResult() {
        return new MemoryQueue<>(WorkerTaskResult.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    public QueueInterface<LogEntry> logEntry() {
        return new MemoryQueue<>(LogEntry.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.FLOW_NAMED)
    public QueueInterface<Flow> flow() {
        return new MemoryQueue<>(Flow.class, applicationContext);
    }
}
