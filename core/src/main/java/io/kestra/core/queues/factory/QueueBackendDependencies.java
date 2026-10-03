package io.kestra.core.queues.factory;

/**
 * Marker for the bean aggregating the Micronaut dependencies of a queue backend plugin.
 * <p>
 * Queue plugins are instantiated from configuration, not by the container, so the beans they use
 * (e.g. the JDBC datasource) are invisible to Micronaut's destruction-order topological sort and
 * could be destroyed before the queues that still use them. Each backend declares one singleton
 * implementing this interface with its dependencies constructor-injected, and the queue factory
 * bean injects {@code List<QueueBackendDependencies>}, which makes every backend's dependencies a
 * required component of the whole queue bean graph and guarantees queues close first.
 * <p>
 * Implementations must guard themselves with {@code @Requires} on the configured
 * {@code kestra.queue.type}: the {@code List} injection eagerly instantiates every present
 * implementation, so an unguarded bean would start an unconfigured backend's clients.
 */
public interface QueueBackendDependencies {
}
