package io.kestra.core.queues.factory;

import java.io.Closeable;
import java.util.Map;

import io.kestra.core.models.Plugin;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.VNodeDispatchQueueInterface;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.queues.event.DispatchEvent;
import io.kestra.core.queues.event.KeyedDispatchEvent;
import io.kestra.core.queues.event.VNodeDispatchEvent;

public interface QueueFactoryInterface extends Plugin, Closeable {

    <Q extends DispatchEvent> DispatchQueueInterface<Q> dispatchQueue(Class<Q> clazz);

    <Q extends BroadcastEvent> BroadcastQueueInterface<Q> broadcastQueue(Class<Q> clazz);

    <Q extends VNodeDispatchEvent> VNodeDispatchQueueInterface<Q> vNodeDispatchQueue(Class<Q> clazz);

    <Q extends KeyedDispatchEvent> KeyedDispatchQueueInterface<Q> keyedDispatchQueue(Class<Q> clazz);

    void init(QueueBackendDependencies backendDependencies, Map<String, Object> pluginConfiguration);
}
