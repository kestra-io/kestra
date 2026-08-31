package io.kestra.core.queues.factory;

/**
 * Marker for the bean aggregating the Micronaut dependencies of a queue backend plugin.
 * <p>
 * Queue plugins are instantiated from configuration, not by the container, so the beans they use
 * (e.g. the JDBC datasource) are invisible to Micronaut's destruction-order topological sort and
 * could be destroyed before the queues that still use them. The queue factory bean injects the single
 * {@link QueueBackendDependencies} bean, which makes its dependencies a required component of the
 * whole queue bean graph and guarantees queues close first.
 * <p>
 * A backend needing container-managed clients declares its own {@code @Primary} implementation, guarded
 * with {@code @Requires} on the configured {@code kestra.queue.type}; otherwise the {@code @Secondary}
 * {@link DefaultDependencies} is injected.
 */
public interface QueueBackendDependencies {
}
