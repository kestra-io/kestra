package io.kestra.core.plugins.endpoint;

import io.kestra.core.models.Plugin;

/**
 * A plugin-provided endpoint invoked in-process by Kestra's webserver.
 * Implementations are stateless singletons and must have a public no-arg constructor
 * (they are instantiated by {@link java.util.ServiceLoader}). They receive only the
 * {@link PluginEndpointContext}, whose capabilities are scoped by construction to the single
 * execution / taskRun the endpoint was called for; they are handed no repository, storage root or bean.
 */
@io.kestra.core.models.annotations.Plugin
public interface PluginEndpoint extends Plugin {
    String name();

    PluginEndpointResponse handle(PluginEndpointContext context);
}
