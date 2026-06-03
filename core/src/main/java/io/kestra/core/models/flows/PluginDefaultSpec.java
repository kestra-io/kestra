package io.kestra.core.models.flows;

import java.util.Map;

/**
 * Common contract for a plugin-default entry: a plugin type paired with default property values.
 * Implementations may optionally expose a {@code forced} flag (see {@link PluginDefault});
 * flow-level defaults ({@link FlowPluginDefault}) intentionally omit it.
 */
public interface PluginDefaultSpec {
    String getType();

    Map<String, Object> getValues();

    /**
     * Optional named reference for this plugin default.
     * <p>
     * When set, the default is applied <em>only</em> to plugins that explicitly opt in via
     * {@code pluginDefaultsRef}, never by {@code type} matching.
     */
    String getRef();
}
