package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.runners.*;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.NotImplementedException;


public abstract class AbstractJdbcQueueFactory implements QueueFactoryInterface<JdbcQueueDependencies>  {
    @Inject
    protected ApplicationContext applicationContext;

    protected abstract <T> QueueInterface<T> queue(Class<T> clazz);
    protected abstract WorkerJobQueueInterface workerJobQueue();
    protected abstract QueueInterface<WorkerTriggerResult> workerTriggerResultQueue();

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    public QueueInterface<Execution> execution(JdbcQueueDependencies jdbcQueueDependencies) {
        return queue(Execution.class);
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    @Named(QueueFactoryInterface.EXECUTOR_NAMED)
    public QueueInterface<Executor> executor(JdbcQueueDependencies jdbcQueueDependencies) {
        throw new NotImplementedException();
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    @Named(QueueFactoryInterface.WORKERJOB_NAMED)
    public WorkerJobQueueInterface workerJob(JdbcQueueDependencies jdbcQueueDependencies) {
        return workerJobQueue();
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    public QueueInterface<WorkerTaskResult> workerTaskResult(JdbcQueueDependencies jdbcQueueDependencies) {
        return queue(WorkerTaskResult.class);
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    @Named(QueueFactoryInterface.WORKERTRIGGERRESULT_NAMED)
    public QueueInterface<WorkerTriggerResult> workerTriggerResult(JdbcQueueDependencies jdbcQueueDependencies) {
        return workerTriggerResultQueue();
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    public QueueInterface<LogEntry> logEntry(JdbcQueueDependencies jdbcQueueDependencies) {
        return queue(LogEntry.class);
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    @Named(QueueFactoryInterface.METRIC_QUEUE)
    public QueueInterface<MetricEntry> metricEntry(JdbcQueueDependencies jdbcQueueDependencies) {
        return queue(MetricEntry.class);
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    @Named(QueueFactoryInterface.FLOW_NAMED)
    public QueueInterface<FlowInterface> flow(JdbcQueueDependencies jdbcQueueDependencies) {
        return queue(FlowInterface.class);
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    @Named(QueueFactoryInterface.KILL_NAMED)
    public QueueInterface<ExecutionKilled> kill(JdbcQueueDependencies jdbcQueueDependencies) {
        return queue(ExecutionKilled.class);
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    @Named(QueueFactoryInterface.TEMPLATE_NAMED)
    public QueueInterface<Template> template(JdbcQueueDependencies jdbcQueueDependencies) {
        return queue(Template.class);
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    @Named(QueueFactoryInterface.WORKERINSTANCE_NAMED)
    public QueueInterface<WorkerInstance> workerInstance(JdbcQueueDependencies jdbcQueueDependencies) {
        return queue(WorkerInstance.class);
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    @Named(QueueFactoryInterface.WORKERJOBRUNNING_NAMED)
    public QueueInterface<WorkerJobRunning> workerJobRunning(JdbcQueueDependencies jdbcQueueDependencies) {
        return queue(WorkerJobRunning.class);
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    @Named(QueueFactoryInterface.TRIGGER_NAMED)
    public QueueInterface<Trigger> trigger(JdbcQueueDependencies jdbcQueueDependencies) {
        return queue(Trigger.class);
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    @Named(QueueFactoryInterface.SUBFLOWEXECUTIONRESULT_NAMED)
    public QueueInterface<SubflowExecutionResult> subflowExecutionResult(JdbcQueueDependencies jdbcQueueDependencies) {
        return queue(SubflowExecutionResult.class);
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    @Named(QueueFactoryInterface.SUBFLOWEXECUTIONEND_NAMED)
    public QueueInterface<SubflowExecutionEnd> subflowExecutionEnd(JdbcQueueDependencies jdbcQueueDependencies) {
        return queue(SubflowExecutionEnd.class);
    }

    @Override
    @Singleton
    @Bean(preDestroy = "close")
    @Named(QueueFactoryInterface.MULTIPLE_CONDITION_EVENT_NAMED)
    public QueueInterface<MultipleConditionEvent> multipleConditionEvent(JdbcQueueDependencies jdbcQueueDependencies) {
        return queue(MultipleConditionEvent.class);
    }
}
