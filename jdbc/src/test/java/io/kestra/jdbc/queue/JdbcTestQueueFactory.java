package io.kestra.jdbc.queue;

import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.VNodeDispatchQueueInterface;
import io.kestra.queue.*;
import io.kestra.queue.jdbc.*;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;

@Factory
@Requires(classes = AbstractDispatchQueueTest.class)
public class JdbcTestQueueFactory {

    @QueueBean
    public BroadcastQueueInterface<AbstractBroadcastQueueTest.TestBroadcast> broadCastQueue(JdbcDependencies dependencies) {
        return new JdbcBroadcastQueue<>(
            AbstractBroadcastQueueTest.TestBroadcast.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    public DispatchQueueInterface<AbstractDispatchQueueTest.TestDispatch> dispatchQueue(JdbcDependencies dependencies) {
        return new JdbcDispatchQueue<>(
            AbstractDispatchQueueTest.TestDispatch.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    public KeyedDispatchQueueInterface<AbstractKeyedDispatchQueueTest.TestKeyedDispatch> keyDispatchQueue(JdbcDependencies dependencies) {
        return new JdbcKeyedDispatchQueue<>(
            AbstractKeyedDispatchQueueTest.TestKeyedDispatch.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    public VNodeDispatchQueueInterface<AbstractVNodeDispatchQueueTest.TestVNodeDispatchDispatch> vNodeDispatchQueue(JdbcDependencies dependencies) {
        return new JdbcVNodeDispatchQueue<>(
            AbstractVNodeDispatchQueueTest.TestVNodeDispatchDispatch.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(),
            dependencies.metricRegistry(), dependencies.ignoreExecutionService()
        );
    }

    @QueueBean
    public BroadcastQueueInterface<AbstractQueueCacheTest.DeletableBroadcastTestEvent> deletableBroadcastTestEventDispatchQueue(JdbcDependencies dependencies) {
        return new JdbcBroadcastQueue<>(
            AbstractQueueCacheTest.DeletableBroadcastTestEvent.class, dependencies.queueService(), dependencies.jdbcQueueClient(), dependencies.executorsUtils(), dependencies.metricRegistry(),
            dependencies.ignoreExecutionService()
        );
    }
}
