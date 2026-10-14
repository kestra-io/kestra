package io.kestra.core.serializers;

import com.fasterxml.jackson.databind.Module;

/**
 * Discovered via {@link java.util.ServiceLoader} by {@link JacksonMapper}, for a module outside {@code core} that
 * can't register its own type in {@link io.kestra.core.plugins.PluginModule} — not a general-purpose extension point.
 */
public interface JacksonMapperModuleProvider {
    Module module();
}
