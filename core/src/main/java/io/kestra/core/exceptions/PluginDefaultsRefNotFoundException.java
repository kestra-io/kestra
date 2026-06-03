package io.kestra.core.exceptions;

import java.io.Serial;

/**
 * Thrown when a plugin (task, trigger or task runner) references named plugin-defaults via
 * {@code pluginDefaultsRef} that cannot be resolved — either none exists with that {@code ref} at flow,
 * namespace or global level, or the one that exists is scoped to a different plugin type.
 */
public class PluginDefaultsRefNotFoundException extends KestraRuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public PluginDefaultsRefNotFoundException(String ref) {
        super("No plugin-defaults exists for pluginDefaultsRef: '" + ref + "'");
    }
}
