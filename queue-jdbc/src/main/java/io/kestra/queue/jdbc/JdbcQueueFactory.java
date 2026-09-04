package io.kestra.queue.jdbc;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.VNodeDispatchQueueInterface;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.queues.event.DispatchEvent;
import io.kestra.core.queues.event.KeyedDispatchEvent;
import io.kestra.core.queues.event.VNodeDispatchEvent;
import io.kestra.core.queues.factory.QueueBackendDependencies;
import io.kestra.core.queues.factory.QueueFactoryInterface;

@Plugin
@Plugin.Id("jdbc")
public class JdbcQueueFactory implements QueueFactoryInterface, Closeable {

    private JdbcDependencies dependencies;

    @Override
    public void init(QueueBackendDependencies backendDependencies, Map<String, Object> pluginConfiguration) {
        this.dependencies = (JdbcDependencies) backendDependencies;
    }

    @Override
    public <Q extends DispatchEvent> DispatchQueueInterface<Q> dispatchQueue(Class<Q> clazz) {
        return new JdbcDispatchQueue<>(
            clazz, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(), dependencies.ignoreExecutionService()
        );
    }

    @Override
    public <Q extends KeyedDispatchEvent> KeyedDispatchQueueInterface<Q> keyedDispatchQueue(Class<Q> clazz) {
        return new JdbcKeyedDispatchQueue<>(
            clazz, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(), dependencies.ignoreExecutionService()
        );
    }

    @Override
    public <Q extends BroadcastEvent> BroadcastQueueInterface<Q> broadcastQueue(Class<Q> clazz) {
        return new JdbcBroadcastQueue<>(
            clazz, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(), dependencies.ignoreExecutionService()
        );
    }

    @Override
    public <Q extends VNodeDispatchEvent> VNodeDispatchQueueInterface<Q> vNodeDispatchQueue(Class<Q> clazz) {
        return new JdbcVNodeDispatchQueue<>(
            clazz, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(), dependencies.ignoreExecutionService()
        );
    }

    @Override
    public void close() throws IOException {
        // no-op as JDBC dependencies are all beans managed by the container
    }
}
