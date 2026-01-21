package io.kestra.runner.mysql;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.runners.*;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

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
    @Named(QueueFactoryInterface.EXECUTION_EVENT_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<ExecutionEvent> executionEvent() {
        return new MysqlQueue<>(ExecutionEvent.class, applicationContext);
    }

    @Override
    @Singleton
    @Named(QueueFactoryInterface.WORKERJOB_NAMED)
    @Bean(preDestroy = "close")
    public WorkerJobQueueInterface workerJob() {
        return new MysqlWorkerJobQueue(applicationContext);
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
        return new MysqlWorkerTriggerResultQueue(applicationContext);
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
    @Named(QueueFactoryInterface.WORKERJOBRUNNING_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<WorkerJobRunning> workerJobRunning() {
        return new MysqlQueue<>(WorkerJobRunning.class, applicationContext);
    }
}
