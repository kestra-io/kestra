package io.kestra.core.queues.factory;

import java.io.Closeable;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.models.Plugin;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.VNodeDispatchQueueInterface;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.queues.event.DispatchEvent;
import io.kestra.core.queues.event.KeyedDispatchEvent;
import io.kestra.core.queues.event.VNodeDispatchEvent;
import io.kestra.core.server.ServiceResourceReleaser;
import io.kestra.core.services.BackendVersionProvider;

import io.micronaut.management.health.indicator.HealthIndicator;

/**
 * Queue backend plugin, instantiated from configuration rather than by the container.
 * <p>
 * The optional hooks ({@link #healthIndicator()}, {@link #versionProvider()}, {@link #serviceResourceReleaser()})
 * are exposed as beans by the core queue factory when present, since the objects a plugin creates
 * are not scanned by Micronaut. They are called more than once (bean condition and bean creation) and only
 * one returned instance becomes the bean, so they must be cheap and free of side effects.
 */
public interface QueueFactoryInterface extends Plugin, Closeable {

    <Q extends DispatchEvent> DispatchQueueInterface<Q> dispatchQueue(Class<Q> clazz);

    <Q extends BroadcastEvent> BroadcastQueueInterface<Q> broadcastQueue(Class<Q> clazz);

    <Q extends VNodeDispatchEvent> VNodeDispatchQueueInterface<Q> vNodeDispatchQueue(Class<Q> clazz);

    <Q extends KeyedDispatchEvent> KeyedDispatchQueueInterface<Q> keyedDispatchQueue(Class<Q> clazz);

    void init(QueueBackendDependencies backendDependencies, Map<String, Object> pluginConfiguration);

    default Optional<HealthIndicator> healthIndicator() {
        return Optional.empty();
    }

    default Optional<BackendVersionProvider> versionProvider() {
        return Optional.empty();
    }

    default Optional<ServiceResourceReleaser> serviceResourceReleaser() {
        return Optional.empty();
    }
}
