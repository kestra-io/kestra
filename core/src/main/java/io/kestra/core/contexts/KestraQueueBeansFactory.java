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
import io.kestra.core.server.ServiceResourceReleaser;
import io.kestra.core.services.BackendVersionProvider;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.management.health.indicator.AbstractHealthIndicator;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.validation.Validator;

import java.util.concurrent.ExecutorService;

/**
 * Beans of the queue layer: the {@link PluginRegistry}, the queue backend plugin selected by {@code kestra.queue.type}
 * and every queue built from it, plus the health, version and resource-releaser hooks a backend may provide.
 * {@link KestraBeansFactory} holds the other configuration-driven beans and depends on this factory; see its
 * Javadoc for why bean creation is split in two.
 * <p>
 * Startup sequence, on any server type but WORKER: an eager {@code @Context} bean injects a queue, so a
 * {@link QueueBean} method runs and asks for the {@link QueueFactoryInterface}; that needs the
 * {@link QueuePluginInterfaceFactory}, hence the {@link PluginRegistry}, whose creation lets
 * {@link io.kestra.core.plugins.ExternalPluginsRegistrar} register the {@code --plugins} directory before any
 * plugin is looked up. The backend plugin is then resolved from the registry, its configuration is bound and validated,
 * and {@code init} receives the {@link QueueBackendDependencies} bean, which makes every bean the
 * backend uses a required component of the queue factory so that the queues are destroyed first on shutdown.
 * Only after the context is up does the CLI command register plugin paths again and start the plugin manager,
 * which is why a queue plugin has to be in the plugins directory and cannot be downloaded at startup.
 * <p>
 * The queue beans are lazy singletons: a server type that never injects a queue never creates the backend.
 */
@Factory
public class KestraQueueBeansFactory {
    @Inject
    private Validator validator;

    @Inject
    private QueueConfig queueConfig;

    @Secondary
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

    // A @Nullable factory method returning null still yields a null element in getBeansOfType(), so
    // the optional hooks are gated by conditions instead; the executor is set by hand as the plugin object is not injected.
    @Requires(condition = QueueHealthIndicatorCondition.class)
    @Singleton
    public HealthIndicator queueHealthIndicator(final QueueFactoryInterface queueFactory,
        @Named(TaskExecutors.BLOCKING) final ExecutorService executorService) {
        HealthIndicator healthIndicator = queueFactory.healthIndicator().orElseThrow();
        if (healthIndicator instanceof AbstractHealthIndicator<?> abstractHealthIndicator) {
            abstractHealthIndicator.setExecutorService(executorService);
        }
        return healthIndicator;
    }

    @Requires(condition = QueueVersionProviderCondition.class)
    @Singleton
    public BackendVersionProvider queueVersionProvider(final QueueFactoryInterface queueFactory) {
        return queueFactory.versionProvider().orElseThrow();
    }

    @Requires(condition = QueueServiceResourceReleaserCondition.class)
    @Singleton
    public ServiceResourceReleaser queueServiceResourceReleaser(final QueueFactoryInterface queueFactory) {
        return queueFactory.serviceResourceReleaser().orElseThrow();
    }

    public static class QueueHealthIndicatorCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            return context.getBeanContext().findBean(QueueFactoryInterface.class).flatMap(QueueFactoryInterface::healthIndicator).isPresent();
        }
    }

    public static class QueueVersionProviderCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            return context.getBeanContext().findBean(QueueFactoryInterface.class).flatMap(QueueFactoryInterface::versionProvider).isPresent();
        }
    }

    public static class QueueServiceResourceReleaserCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            return context.getBeanContext().findBean(QueueFactoryInterface.class).flatMap(QueueFactoryInterface::serviceResourceReleaser).isPresent();
        }
    }

    private String getQueuePluginId(QueuePluginInterfaceFactory queuePluginInterfaceFactory) {
        String type = queueConfig.type().orElseThrow(
            () -> new KestraRuntimeException(
                String.format(
                    "No queue configured through the application property '%s'. Supported types are: %s",
                    QueuePluginInterfaceFactory.KESTRA_QUEUE_TYPE_CONFIG, queuePluginInterfaceFactory.getLoggableTypeIds()
                )
            )
        );
        return QueuePluginInterfaceFactory.pluginId(type);
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
