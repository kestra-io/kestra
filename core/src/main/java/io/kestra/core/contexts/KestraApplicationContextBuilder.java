package io.kestra.core.contexts;

import io.kestra.core.plugins.PluginRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultApplicationContext;
import io.micronaut.context.DefaultApplicationContextBuilder;

/**
 * This DefaultApplicationContextBuilder will create a KestraApplicationContext.
 * The pluginRegistry(PluginRegistry) must be called before calling the build() method, so the application context will have
 * access to the plugin repository.
 */
public class KestraApplicationContextBuilder extends DefaultApplicationContextBuilder {
    private PluginRegistry pluginRegistry;

    public KestraApplicationContextBuilder pluginRegistry(PluginRegistry pluginRegistry) {
        if (pluginRegistry != null) {
            this.pluginRegistry = pluginRegistry;
        }
        return this;
    }

    @Override
    public ApplicationContext build() {
        ApplicationContext defaultApplicationContext = super.build();

        DefaultApplicationContext applicationContext = new KestraApplicationContext(this, this.pluginRegistry);
        applicationContext.setEnvironment(defaultApplicationContext.getEnvironment());

        return applicationContext;
    }
}
