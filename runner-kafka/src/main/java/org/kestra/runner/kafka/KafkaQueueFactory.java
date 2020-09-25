package org.kestra.runner.kafka;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.ExecutionKilled;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.templates.Template;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.queues.WorkerTaskQueueInterface;
import org.kestra.core.runners.WorkerInstance;
import org.kestra.core.runners.WorkerTask;
import org.kestra.core.runners.WorkerTaskResult;
import org.kestra.core.runners.WorkerTaskRunning;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Factory
@KafkaQueueEnabled
public class KafkaQueueFactory implements QueueFactoryInterface {
    @Inject
    ApplicationContext applicationContext;

    @Override
    @Singleton
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    public QueueInterface<Execution> execution() {
        return new KafkaQueue<>(Execution.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTASK_NAMED)
    public QueueInterface<WorkerTask> workerTask() {
        return new KafkaQueue<>(WorkerTask.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    public QueueInterface<WorkerTaskResult> workerTaskResult() {
        return new KafkaQueue<>(WorkerTaskResult.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    public QueueInterface<LogEntry> logEntry() {
        return new KafkaQueue<>(LogEntry.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.FLOW_NAMED)
    public QueueInterface<Flow> flow() {
        return new KafkaQueue<>(Flow.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.KILL_NAMED)
    public QueueInterface<ExecutionKilled> kill() {
        return new KafkaQueue<>(ExecutionKilled.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.TEMPLATE_NAMED)
    public QueueInterface<Template> template() {
        return new KafkaQueue<>(Template.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERINSTANCE_NAMED)
    public QueueInterface<WorkerInstance> workerInstance() {
        return new KafkaQueue<>(WorkerInstance.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTASKRUNNING_NAMED)
    public QueueInterface<WorkerTaskRunning> workerTaskRunning() {
        return new KafkaQueue<>(WorkerTaskRunning.class, applicationContext);
    }

    @Override
    @Singleton
    public WorkerTaskQueueInterface workerTaskQueue() {
        return new KafkaWorkerTaskQueue(applicationContext);
    }
}
