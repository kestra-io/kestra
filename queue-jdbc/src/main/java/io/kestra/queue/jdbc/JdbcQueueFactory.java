package io.kestra.queue.jdbc;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.VNodeDispatchQueueInterface;
import io.kestra.core.runners.*;
import io.kestra.core.runners.MultipleConditionEvent;
import io.kestra.core.runners.SubflowExecutionEnd;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.scheduler.events.SchedulerEvent;
import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.queue.QueueFactoryInterface;
import io.kestra.queue.QueueService;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Factory
@JdbcQueueEnabled
public class JdbcQueueFactory implements QueueFactoryInterface {
    @Inject
    private QueueService queueService;

    @Inject
    private JdbcQueueClient jdbcQueueClient;

    @Inject
    private ExecutorsUtils executorsUtils;

    @Inject
    private MetricRegistry metricRegistry;

    @Bean(preDestroy = "close")
    @Singleton
    @Override
    public DispatchQueueInterface<Execution> executionQueue() {
        return new JdbcDispatchQueue<>(Execution.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Override
    public DispatchQueueInterface<ExecutionCommand> executionCommandQueue() {
        return new JdbcDispatchQueue<>(ExecutionCommand.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Override
    public DispatchQueueInterface<ExecutionEvent> executionEventQueue() {
        return new JdbcDispatchQueue<>(ExecutionEvent.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Override
    public BroadcastQueueInterface<ExecutionKilled> killQueue() {
        return new JdbcBroadcastQueue<>(ExecutionKilled.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Override
    public DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue() {
        return new JdbcDispatchQueue<>(SubflowExecutionResult.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Override
    public DispatchQueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue() {
        return new JdbcDispatchQueue<>(SubflowExecutionEnd.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Override
    public DispatchQueueInterface<MultipleConditionEvent> multipleConditionEventQueue() {
        return new JdbcDispatchQueue<>(MultipleConditionEvent.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Override
    public DispatchQueueInterface<FlowInterface> flowQueue() {
        return new JdbcDispatchQueue<>(FlowInterface.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Override
    public BroadcastQueueInterface<SchedulerEvent> schedulerEventQueue() {
        return new JdbcBroadcastQueue<>(SchedulerEvent.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Override
    public VNodeDispatchQueueInterface<TriggerEvent> triggerEventQueue() {
        return new JdbcVNodeDispatchQueue<>(TriggerEvent.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Override
    public DispatchQueueInterface<MetricEntry> metricQueue() {
        return new JdbcDispatchQueue<>(MetricEntry.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Override
    public BroadcastQueueInterface<FollowExecutionEvent> followExecutionQueue() {
        return new JdbcBroadcastQueue<>(FollowExecutionEvent.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Override
    public DispatchQueueInterface<LogEntry> logEntryQueue() {
        return new JdbcDispatchQueue<>(LogEntry.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Override
    public BroadcastQueueInterface<FollowLogEvent> followLogEventQueue() {
        return new JdbcBroadcastQueue<>(FollowLogEvent.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Override
    public KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue() {
        return new JdbcKeyedDispatchQueue<>(WorkerJobEvent.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }

    @Bean(preDestroy = "close")
    @Singleton
    @Override
    public DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue() {
        return new JdbcDispatchQueue<>(WorkerTaskResult.class, queueService, jdbcQueueClient, executorsUtils, metricRegistry);
    }
}
