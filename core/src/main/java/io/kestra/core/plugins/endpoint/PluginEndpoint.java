package io.kestra.core.plugins.endpoint;

import io.kestra.core.models.Plugin;

/**
 * A plugin-provided endpoint invoked in-process by Kestra's webserver.
 * Implementations are stateless singletons and must have a public no-arg constructor
 * (they are instantiated by {@link java.util.ServiceLoader}). They receive only the
 * request and must not access Kestra internals; to reach Kestra, call back over HTTP with the SDK.
 */
@io.kestra.core.models.annotations.Plugin
public interface PluginEndpoint extends Plugin {
    String name();

    PluginEndpointResponse handle(PluginEndpointRequest request);
}
