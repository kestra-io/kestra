package io.kestra.core.reporter;

/**
 * All supported reportable event type.
 */
public enum Types implements Type {
    USAGE,
    SYSTEM_INFORMATION,
    PLUGIN_METRICS,
    SERVICE_USAGE,
    PLUGIN_USAGE;
}
