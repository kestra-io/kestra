package io.kestra.core.contexts;

import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.mcp.models.McpSessionEvent;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.executions.statistics.ExecutionStatistic;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.plugins.DefaultPluginRegistry;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.VNodeDispatchQueueInterface;
import io.kestra.core.queues.factory.QueueBackendDependencies;
import io.kestra.core.queues.factory.QueueBean;
import io.kestra.core.queues.factory.QueueConfig;
import io.kestra.core.queues.factory.QueueFactoryInterface;
import io.kestra.core.queues.factory.QueuePluginInterfaceFactory;
import io.kestra.core.runners.*;
import io.kestra.core.scheduler.events.SchedulerEvent;
import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.server.ClusterEvent;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.Validator;

@Factory
public class KestraQueueBeansFactory {
    @Inject
    private Validator validator;

    @Inject
    private QueueConfig queueConfig;

    @Requires(missingBeans = PluginRegistry.class)
    @Singleton
    public PluginRegistry pluginRegistry() {
        return DefaultPluginRegistry.getOrCreate();
    }

    @Singleton
    public QueuePluginInterfaceFactory queuePluginInterfaceFactory(final PluginRegistry pluginRegistry,
        final ApplicationContext applicationContext) {
        return new QueuePluginInterfaceFactory(pluginRegistry, validator, applicationContext);
    }

    @Requires(property = "kestra.server-type", notEquals = "WORKER")
    @Bean(preDestroy = "close")
    @Singleton
    public QueueFactoryInterface queueFactory(final QueuePluginInterfaceFactory queuePluginInterfaceFactory,
        final QueueBackendDependencies backendDependencies) {
        String pluginId = getQueuePluginId(queuePluginInterfaceFactory);
        return queuePluginInterfaceFactory.make(pluginId, queueConfig.getQueueConfig(pluginId), backendDependencies);
    }

    /**
     * Resolves the configured queue factory type.
     */
    private String getQueuePluginId(QueuePluginInterfaceFactory queuePluginInterfaceFactory) {
        String type = queueConfig.type().orElseThrow(
            () -> new KestraRuntimeException(
                String.format(
                    "No queue configured through the application property '%s'. Supported types are: %s",
                    QueuePluginInterfaceFactory.KESTRA_QUEUE_TYPE_CONFIG, queuePluginInterfaceFactory.getLoggableTypeIds()
                )
            )
        );

        // The in-memory backend is H2 with an in-memory datasource.
        return "memory".equalsIgnoreCase(type) || "h2".equalsIgnoreCase(type) || "mysql".equalsIgnoreCase(type) || "postgres".equalsIgnoreCase(type) ? "jdbc" : type;
    }

    @QueueBean
    public DispatchQueueInterface<Execution> executionQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.dispatchQueue(Execution.class);
    }

    @QueueBean
    public DispatchQueueInterface<ExecutionCommand> executionCommandQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.dispatchQueue(ExecutionCommand.class);
    }

    @QueueBean
    public DispatchQueueInterface<ExecutionEvent> executionEventQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.dispatchQueue(ExecutionEvent.class);
    }

    @QueueBean
    public BroadcastQueueInterface<ExecutionKilled> killQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.broadcastQueue(ExecutionKilled.class);
    }

    @QueueBean
    public DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.dispatchQueue(SubflowExecutionResult.class);
    }

    @QueueBean
    public DispatchQueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.dispatchQueue(SubflowExecutionEnd.class);
    }

    @QueueBean
    public DispatchQueueInterface<MultipleConditionEvent> multipleConditionEventQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.dispatchQueue(MultipleConditionEvent.class);
    }

    @QueueBean
    public BroadcastQueueInterface<FlowInterface> flowQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.broadcastQueue(FlowInterface.class);
    }

    @QueueBean
    public BroadcastQueueInterface<SchedulerEvent> schedulerEventQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.broadcastQueue(SchedulerEvent.class);
    }

    @QueueBean
    public VNodeDispatchQueueInterface<TriggerEvent> triggerEventQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.vNodeDispatchQueue(TriggerEvent.class);
    }

    @QueueBean
    public DispatchQueueInterface<MetricEntry> metricQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.dispatchQueue(MetricEntry.class);
    }

    @QueueBean
    public DispatchQueueInterface<ExecutionStatistic> executionStatisticQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.dispatchQueue(ExecutionStatistic.class);
    }

    @QueueBean
    public BroadcastQueueInterface<FollowExecutionEvent> followExecutionQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.broadcastQueue(FollowExecutionEvent.class);
    }

    @QueueBean
    public BroadcastQueueInterface<AsyncOperationProcessedEvent> asyncOperationProcessedEventQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.broadcastQueue(AsyncOperationProcessedEvent.class);
    }

    @QueueBean
    public DispatchQueueInterface<LogEntry> logEntryQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.dispatchQueue(LogEntry.class);
    }

    @QueueBean
    public BroadcastQueueInterface<FollowLogEvent> followLogEventQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.broadcastQueue(FollowLogEvent.class);
    }

    @QueueBean
    public KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.keyedDispatchQueue(WorkerJobEvent.class);
    }

    @QueueBean
    public DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.dispatchQueue(WorkerTaskResult.class);
    }

    @QueueBean
    public BroadcastQueueInterface<McpSessionEvent> mcpSessionQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.broadcastQueue(McpSessionEvent.class);
    }

    @QueueBean
    public DispatchQueueInterface<LoopExecutionEvent> loopExecutionEventQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.dispatchQueue(LoopExecutionEvent.class);
    }

    @QueueBean
    public BroadcastQueueInterface<ClusterEvent> clusterEventQueue(QueueFactoryInterface queueFactoryInterface) {
        return queueFactoryInterface.broadcastQueue(ClusterEvent.class);
    }
}
