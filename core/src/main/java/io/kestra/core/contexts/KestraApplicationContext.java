package io.kestra.core.contexts;

import io.kestra.core.plugins.PluginRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.DefaultApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.inject.BeanDefinitionReference;

import java.util.List;

/**
 * Overload the {@link DefaultApplicationContext} in order to add plugins
 * into the {@link io.micronaut.context.DefaultBeanContext}
 */
@SuppressWarnings("rawtypes")
public class KestraApplicationContext extends DefaultApplicationContext {
    private final PluginRegistry pluginRegistry;

    private ApplicationContext delegate;

    public PluginRegistry getPluginRegistry() {
        return pluginRegistry;
    }

    public KestraApplicationContext(@NonNull ApplicationContext delegate,
                                    @NonNull ApplicationContextConfiguration configuration,
                                    PluginRegistry pluginRegistry) {
        super(configuration);
        this.delegate = delegate;
        this.pluginRegistry = pluginRegistry;
    }

    /** {@inheritDoc} **/
    @Override
    public Environment getEnvironment() {
        return delegate.getEnvironment();
    }

    /** {@inheritDoc} **/
    @Override
    protected @NonNull List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
        List<BeanDefinitionReference> resolvedBeanReferences = super.resolveBeanDefinitionReferences();

        if (pluginRegistry != null) {
            pluginRegistry
                .getPlugins()
                .forEach(plugin -> {
                    final SoftServiceLoader<BeanDefinitionReference> definitions = SoftServiceLoader.load(BeanDefinitionReference.class, plugin.getClassLoader());
                    definitions.collectAll(resolvedBeanReferences, BeanDefinitionReference::isPresent);
                });
        }
        return resolvedBeanReferences;
    }
}
