package io.kestra.core.contexts;

import io.kestra.core.plugins.DefaultPluginRegistry;
import io.kestra.core.plugins.PluginRegistry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Factory
public class KestraBeansFactory {

    @Requires(missingBeans = PluginRegistry.class)
    @Singleton
    public PluginRegistry pluginRegistry() {
        return DefaultPluginRegistry.getOrCreate();
    }
}
