package io.kestra.core.plugins.endpoint;

/**
 * Raised when a plugin endpoint's {@code handle} throws. It carries the group and name for logging;
 * the cause is never surfaced to the caller so a plugin cannot leak internal detail through an error body.
 */
public class PluginEndpointExecutionException extends RuntimeException {
    public PluginEndpointExecutionException(String group, String name, Throwable cause) {
        super("The plugin endpoint '%s/%s' failed to process the request.".formatted(group, name), cause);
    }
}
