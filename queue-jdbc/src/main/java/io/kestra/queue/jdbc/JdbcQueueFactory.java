package io.kestra.queue.jdbc;

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
import io.kestra.queue.QueueBean;
import io.kestra.queue.QueueFactoryInterface;
import io.micronaut.context.annotation.Factory;

@Factory
@JdbcQueueEnabled
public class JdbcQueueFactory implements QueueFactoryInterface<JdbcDependencies> {
    @QueueBean
    @Override
    public DispatchQueueInterface<Execution> executionQueue(JdbcDependencies dependencies) {
        return new JdbcDispatchQueue<>(Execution.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry());
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<ExecutionCommand> executionCommandQueue(JdbcDependencies dependencies) {
        return new JdbcDispatchQueue<>(ExecutionCommand.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry());
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<ExecutionEvent> executionEventQueue(JdbcDependencies dependencies) {
        return new JdbcDispatchQueue<>(ExecutionEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry());
    }

    @QueueBean
    @Override
    public BroadcastQueueInterface<ExecutionKilled> killQueue(JdbcDependencies dependencies) {
        return new JdbcBroadcastQueue<>(ExecutionKilled.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry());
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue(JdbcDependencies dependencies) {
        return new JdbcDispatchQueue<>(SubflowExecutionResult.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry());
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue(JdbcDependencies dependencies) {
        return new JdbcDispatchQueue<>(SubflowExecutionEnd.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry());
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<MultipleConditionEvent> multipleConditionEventQueue(JdbcDependencies dependencies) {
        return new JdbcDispatchQueue<>(MultipleConditionEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry());
    }

    @QueueBean
    @Override
    public BroadcastQueueInterface<FlowInterface> flowQueue(JdbcDependencies dependencies) {
        return new JdbcBroadcastQueue<>(FlowInterface.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry());
    }

    @QueueBean
    @Override
    public BroadcastQueueInterface<SchedulerEvent> schedulerEventQueue(JdbcDependencies dependencies) {
        return new JdbcBroadcastQueue<>(SchedulerEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry());
    }

    @QueueBean
    @Override
    public VNodeDispatchQueueInterface<TriggerEvent> triggerEventQueue(JdbcDependencies dependencies) {
        return new JdbcVNodeDispatchQueue<>(TriggerEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry());
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<MetricEntry> metricQueue(JdbcDependencies dependencies) {
        return new JdbcDispatchQueue<>(MetricEntry.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry());
    }

    @QueueBean
    @Override
    public BroadcastQueueInterface<FollowExecutionEvent> followExecutionQueue(JdbcDependencies dependencies) {
        return new JdbcBroadcastQueue<>(FollowExecutionEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry());
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<LogEntry> logEntryQueue(JdbcDependencies dependencies) {
        return new JdbcDispatchQueue<>(LogEntry.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry());
    }

    @QueueBean
    @Override
    public BroadcastQueueInterface<FollowLogEvent> followLogEventQueue(JdbcDependencies dependencies) {
        return new JdbcBroadcastQueue<>(FollowLogEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry());
    }

    @QueueBean
    @Override
    public KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue(JdbcDependencies dependencies) {
        return new JdbcKeyedDispatchQueue<>(WorkerJobEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry());
    }

    @QueueBean
    @Override
    public DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue(JdbcDependencies dependencies) {
        return new JdbcDispatchQueue<>(WorkerTaskResult.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry());
    }
}
