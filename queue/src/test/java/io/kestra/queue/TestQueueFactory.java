package io.kestra.queue;

import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.VNodeDispatchQueueInterface;
import io.kestra.core.queues.factory.QueueBean;
import io.kestra.core.queues.factory.QueueFactoryInterface;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;

/**
 * Builds the queues the abstract queue tests inject from whichever backend the context is configured with.
 */
@Factory
@Requires(beans = QueueFactoryInterface.class)
public class TestQueueFactory {

    @QueueBean
    public BroadcastQueueInterface<AbstractBroadcastQueueTest.TestBroadcast> broadCastQueue(QueueFactoryInterface queueFactory) {
        return queueFactory.broadcastQueue(AbstractBroadcastQueueTest.TestBroadcast.class);
    }

    @QueueBean
    public DispatchQueueInterface<AbstractDispatchQueueTest.TestDispatch> dispatchQueue(QueueFactoryInterface queueFactory) {
        return queueFactory.dispatchQueue(AbstractDispatchQueueTest.TestDispatch.class);
    }

    @QueueBean
    public KeyedDispatchQueueInterface<AbstractKeyedDispatchQueueTest.TestKeyedDispatch> keyDispatchQueue(QueueFactoryInterface queueFactory) {
        return queueFactory.keyedDispatchQueue(AbstractKeyedDispatchQueueTest.TestKeyedDispatch.class);
    }

    @QueueBean
    public VNodeDispatchQueueInterface<AbstractVNodeDispatchQueueTest.TestVNodeDispatchDispatch> vNodeDispatchQueue(QueueFactoryInterface queueFactory) {
        return queueFactory.vNodeDispatchQueue(AbstractVNodeDispatchQueueTest.TestVNodeDispatchDispatch.class);
    }

    @QueueBean
    public BroadcastQueueInterface<AbstractQueueCacheTest.DeletableBroadcastTestEvent> deletableBroadcastTestEventQueue(QueueFactoryInterface queueFactory) {
        return queueFactory.broadcastQueue(AbstractQueueCacheTest.DeletableBroadcastTestEvent.class);
    }
}
