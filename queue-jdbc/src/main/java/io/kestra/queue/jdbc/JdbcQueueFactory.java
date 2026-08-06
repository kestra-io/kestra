package io.kestra.queue.jdbc;

import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.mcp.models.McpSessionEvent;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.statistics.ExecutionStatistic;
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
import io.kestra.core.server.ClusterEvent;
import io.kestra.queue.QueueBean;
import io.kestra.queue.QueueFactoryInterface;

import io.kestra.queue.jdbc.client.JdbcQueueClient;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Secondary;

@Factory
@JdbcQueueEnabled
public class JdbcQueueFactory implements QueueFactoryInterface<JdbcDependencies> {
    @QueueBean
    @Override
    public DispatchQueueInterface<Execution> executionQueue(JdbcDependencies dependencies) {
        ensureTable(Execution.class, dependencies.jdbcQueueClient());
        return new JdbcDispatchQueue<>(
            Execution.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(), dependencies.ignoreExecutionService()
        );
    }



    @QueueBean
    @Override
    public DispatchQueueInterface<ExecutionCommand> executionCommandQueue(JdbcDependencies dependencies) {
        ensureTable(ExecutionCommand.class, dependencies.jdbcQueueClient());
        return new JdbcDispatchQueue<>(
            ExecutionCommand.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<ExecutionEvent> executionEventQueue(JdbcDependencies dependencies) {
        ensureTable(ExecutionEvent.class, dependencies.jdbcQueueClient());
        return new JdbcDispatchQueue<>(
            ExecutionEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public BroadcastQueueInterface<ExecutionKilled> killQueue(JdbcDependencies dependencies) {
        ensureTable(ExecutionKilled.class, dependencies.jdbcQueueClient());
        return new JdbcBroadcastQueue<>(
            ExecutionKilled.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue(JdbcDependencies dependencies) {
        ensureTable(SubflowExecutionResult.class, dependencies.jdbcQueueClient());
        return new JdbcDispatchQueue<>(
            SubflowExecutionResult.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue(JdbcDependencies dependencies) {
        ensureTable(SubflowExecutionEnd.class, dependencies.jdbcQueueClient());
        return new JdbcDispatchQueue<>(
            SubflowExecutionEnd.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<MultipleConditionEvent> multipleConditionEventQueue(JdbcDependencies dependencies) {
        ensureTable(MultipleConditionEvent.class, dependencies.jdbcQueueClient());
        return new JdbcDispatchQueue<>(
            MultipleConditionEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public BroadcastQueueInterface<FlowInterface> flowQueue(JdbcDependencies dependencies) {
        ensureTable(FlowInterface.class, dependencies.jdbcQueueClient());
        return new JdbcBroadcastQueue<>(
            FlowInterface.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public BroadcastQueueInterface<SchedulerEvent> schedulerEventQueue(JdbcDependencies dependencies) {
        ensureTable(SchedulerEvent.class, dependencies.jdbcQueueClient());
        return new JdbcBroadcastQueue<>(
            SchedulerEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public VNodeDispatchQueueInterface<TriggerEvent> triggerEventQueue(JdbcDependencies dependencies) {
        ensureTable(TriggerEvent.class, dependencies.jdbcQueueClient());
        return new JdbcVNodeDispatchQueue<>(
            TriggerEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(), dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<MetricEntry> metricQueue(JdbcDependencies dependencies) {
        ensureTable(MetricEntry.class, dependencies.jdbcQueueClient());
        return new JdbcDispatchQueue<>(
            MetricEntry.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(), dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<ExecutionStatistic> executionStatisticQueue(JdbcDependencies dependencies) {
        ensureTable(ExecutionStatistic.class, dependencies.jdbcQueueClient());
        return new JdbcDispatchQueue<>(
            ExecutionStatistic.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public BroadcastQueueInterface<FollowExecutionEvent> followExecutionQueue(JdbcDependencies dependencies) {
        ensureTable(FollowExecutionEvent.class, dependencies.jdbcQueueClient());
        return new JdbcBroadcastQueue<>(
            FollowExecutionEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public BroadcastQueueInterface<AsyncOperationProcessedEvent> asyncOperationProcessedEventQueue(JdbcDependencies dependencies) {
        ensureTable(AsyncOperationProcessedEvent.class, dependencies.jdbcQueueClient());
        return new JdbcBroadcastQueue<>(
            AsyncOperationProcessedEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<LogEntry> logEntryQueue(JdbcDependencies dependencies) {
        ensureTable(LogEntry.class, dependencies.jdbcQueueClient());
        return new JdbcDispatchQueue<>(
            LogEntry.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(), dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public BroadcastQueueInterface<FollowLogEvent> followLogEventQueue(JdbcDependencies dependencies) {
        ensureTable(FollowLogEvent.class, dependencies.jdbcQueueClient());
        return new JdbcBroadcastQueue<>(
            FollowLogEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue(JdbcDependencies dependencies) {
        ensureTable(WorkerJobEvent.class, dependencies.jdbcQueueClient());
        return new JdbcKeyedDispatchQueue<>(
            WorkerJobEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue(JdbcDependencies dependencies) {
        ensureTable(WorkerTaskResult.class, dependencies.jdbcQueueClient());
        return new JdbcDispatchQueue<>(
            WorkerTaskResult.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public BroadcastQueueInterface<McpSessionEvent> mcpSessionQueue(JdbcDependencies dependencies) {
        ensureTable(McpSessionEvent.class, dependencies.jdbcQueueClient());
        return new JdbcBroadcastQueue<>(
            McpSessionEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Secondary
    @Override
    public BroadcastQueueInterface<ClusterEvent> clusterEventQueue(JdbcDependencies dependencies) {
        ensureTable(ClusterEvent.class, dependencies.jdbcQueueClient());
        return new JdbcBroadcastQueue<>(
            ClusterEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<LoopExecutionEvent> loopExecutionEventQueue(JdbcDependencies dependencies) {
        ensureTable(LoopExecutionEvent.class, dependencies.jdbcQueueClient());
        return new JdbcDispatchQueue<>(
            LoopExecutionEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    protected void ensureTable(Class<?> cls, JdbcQueueClient jdbcQueueClient) {
        jdbcQueueClient.createTableIfNotExist(cls);
    }
}
