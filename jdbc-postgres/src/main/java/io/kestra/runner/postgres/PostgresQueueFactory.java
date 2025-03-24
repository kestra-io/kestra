package io.kestra.runner.postgres;

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
@PostgresQueueEnabled
public class PostgresQueueFactory implements QueueFactoryInterface {
    @Inject
    ApplicationContext applicationContext;

    @Override
    @Singleton
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<Execution> execution() {
        return new PostgresQueue<>(Execution.class, applicationContext);
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
        return new PostgresQueue<>(WorkerJob.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<WorkerTaskResult> workerTaskResult() {
        return new PostgresQueue<>(WorkerTaskResult.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTRIGGERRESULT_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<WorkerTriggerResult> workerTriggerResult() {
        return new PostgresQueue<>(WorkerTriggerResult.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<LogEntry> logEntry() {
        return new PostgresQueue<>(LogEntry.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.METRIC_QUEUE)
    @Bean(preDestroy = "close")
    public QueueInterface<MetricEntry> metricEntry() {
        return new PostgresQueue<>(MetricEntry.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.FLOW_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<FlowWithSource> flow() {
        return new PostgresQueue<>(FlowWithSource.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.KILL_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<ExecutionKilled> kill() {
        return new PostgresQueue<>(ExecutionKilled.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.TEMPLATE_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<Template> template() {
        return new PostgresQueue<>(Template.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERINSTANCE_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<WorkerInstance> workerInstance() {
        return new PostgresQueue<>(WorkerInstance.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERJOBRUNNING_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<WorkerJobRunning> workerJobRunning() {
        return new PostgresQueue<>(WorkerJobRunning.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.TRIGGER_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<Trigger> trigger() {
        return new PostgresQueue<>(Trigger.class, applicationContext);
    }

    @Override
    @Prototype // must be prototype so we can create two Worker in the same application context for testing purpose.
    @Bean(preDestroy = "close")
    public WorkerJobQueueInterface workerJobQueue() {
        return new PostgresWorkerJobQueue(applicationContext);
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    public WorkerTriggerResultQueueInterface workerTriggerResultQueue() {
        return new PostgresWorkerTriggerResultQueue(applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.SUBFLOWEXECUTIONRESULT_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<SubflowExecutionResult> subflowExecutionResult() {
        return new PostgresQueue<>(SubflowExecutionResult.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.SUBFLOWEXECUTIONEND_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<SubflowExecutionEnd> subflowExecutionEnd() {
        return new PostgresQueue<>(SubflowExecutionEnd.class, applicationContext);
    }
}
