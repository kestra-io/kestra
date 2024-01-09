package io.kestra.runner.mysql;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.queues.WorkerTriggerResultQueueInterface;
import io.kestra.core.runners.*;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.NotImplementedException;

@Factory
@MysqlQueueEnabled
public class MysqlQueueFactory implements QueueFactoryInterface {
    @Inject
    ApplicationContext applicationContext;

    @Override
    @Singleton
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    public QueueInterface<Execution> execution() {
        return new MysqlQueue<>(Execution.class, applicationContext);
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
        return new MysqlQueue<>(WorkerJob.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    public QueueInterface<WorkerTaskResult> workerTaskResult() {
        return new MysqlQueue<>(WorkerTaskResult.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTRIGGERRESULT_NAMED)
    public QueueInterface<WorkerTriggerResult> workerTriggerResult() {
        return new MysqlQueue<>(WorkerTriggerResult.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    public QueueInterface<LogEntry> logEntry() {
        return new MysqlQueue<>(LogEntry.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.METRIC_QUEUE)
    public QueueInterface<MetricEntry> metricEntry() {
        return new MysqlQueue<>(MetricEntry.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.FLOW_NAMED)
    public QueueInterface<Flow> flow() {
        return new MysqlQueue<>(Flow.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.KILL_NAMED)
    public QueueInterface<ExecutionKilled> kill() {
        return new MysqlQueue<>(ExecutionKilled.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.TEMPLATE_NAMED)
    public QueueInterface<Template> template() {
        return new MysqlQueue<>(Template.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERINSTANCE_NAMED)
    public QueueInterface<WorkerInstance> workerInstance() {
        return new MysqlQueue<>(WorkerInstance.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERJOBRUNNING_NAMED)
    public QueueInterface<WorkerJobRunning> workerJobRunning() {
        return new MysqlQueue<>(WorkerJobRunning.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.TRIGGER_NAMED)
    public QueueInterface<Trigger> trigger() {
        return new MysqlQueue<>(Trigger.class, applicationContext);
    }

    @Override
    @Prototype // must be prototype so we can create two Worker in the same application context for testing purpose.
    public WorkerJobQueueInterface workerJobQueue() {
        return new MysqlWorkerJobQueue(applicationContext);
    }

    @Override
    @Singleton
    public WorkerTriggerResultQueueInterface workerTriggerResultQueue() {
        return new MysqlWorkerTriggerResultQueue(applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.SUBFLOWEXECUTIONRESULT_NAMED)
    public QueueInterface<SubflowExecutionResult> subflowExecutionResult() {
        return new MysqlQueue<>(SubflowExecutionResult.class, applicationContext);
    }
}
