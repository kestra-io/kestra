package io.kestra.runner.mysql;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.queues.WorkerTriggerResultQueueInterface;
import io.kestra.core.runners.*;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
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
    @Bean(preDestroy = "close")
    public QueueInterface<Execution> execution() {
        return new MysqlQueue<>(Execution.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.EXECUTOR_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<Executor> executor() {
        throw new NotImplementedException();
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERJOB_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<WorkerJob> workerJob() {
        return new MysqlQueue<>(WorkerJob.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<WorkerTaskResult> workerTaskResult() {
        return new MysqlQueue<>(WorkerTaskResult.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTRIGGERRESULT_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<WorkerTriggerResult> workerTriggerResult() {
        return new MysqlQueue<>(WorkerTriggerResult.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<LogEntry> logEntry() {
        return new MysqlQueue<>(LogEntry.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.METRIC_QUEUE)
    @Bean(preDestroy = "close")
    public QueueInterface<MetricEntry> metricEntry() {
        return new MysqlQueue<>(MetricEntry.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.FLOW_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<FlowWithSource> flow() {
        return new MysqlQueue<>(FlowWithSource.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.KILL_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<ExecutionKilled> kill() {
        return new MysqlQueue<>(ExecutionKilled.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.TEMPLATE_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<Template> template() {
        return new MysqlQueue<>(Template.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERINSTANCE_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<WorkerInstance> workerInstance() {
        return new MysqlQueue<>(WorkerInstance.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERJOBRUNNING_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<WorkerJobRunning> workerJobRunning() {
        return new MysqlQueue<>(WorkerJobRunning.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.TRIGGER_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<Trigger> trigger() {
        return new MysqlQueue<>(Trigger.class, applicationContext);
    }

    @Override
    @Prototype // must be prototype so we can create two Worker in the same application context for testing purpose.
    @Bean(preDestroy = "close")
    public WorkerJobQueueInterface workerJobQueue() {
        return new MysqlWorkerJobQueue(applicationContext);
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    public WorkerTriggerResultQueueInterface workerTriggerResultQueue() {
        return new MysqlWorkerTriggerResultQueue(applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.SUBFLOWEXECUTIONRESULT_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<SubflowExecutionResult> subflowExecutionResult() {
        return new MysqlQueue<>(SubflowExecutionResult.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.SUBFLOWEXECUTIONEND_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<SubflowExecutionEnd> subflowExecutionEnd() {
        return new MysqlQueue<>(SubflowExecutionEnd.class, applicationContext);
    }
}
