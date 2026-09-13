package io.kestra.core.plugins;

import io.micronaut.context.ApplicationContext;

/**
 * Implemented by plugins that are deserialized from configuration (rather than injected) and need
 * access to the {@link ApplicationContext} to wire their runtime dependencies — for example a
 * secret manager or a JDBC log store resolving beans it cannot receive through constructor injection.
 * <p>
 * Using this should be avoided as much as possible: it breaks the Micronaut bean life-cycle graph.
 * It exists only because such plugins are instantiated by a factory from a configuration map.
 */
public interface ApplicationContextInitializable {

    /**
     * Initializes the plugin using the given application context to resolve required beans.
     *
     * @param applicationContext the application context to inject the required beans.
     */
    void init(ApplicationContext applicationContext);
}
