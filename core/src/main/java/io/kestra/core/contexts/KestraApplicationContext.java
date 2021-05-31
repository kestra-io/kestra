package io.kestra.core.contexts;

import io.kestra.core.plugins.PluginRegistry;
import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.DefaultApplicationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.inject.BeanDefinitionReference;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Overload the {@link DefaultApplicationContext} in order to add plugins
 * into the {@link io.micronaut.context.DefaultBeanContext}
 */
@SuppressWarnings("rawtypes")
public class KestraApplicationContext extends DefaultApplicationContext {
    private final PluginRegistry pluginRegistry;

    public PluginRegistry getPluginRegistry() {
        return pluginRegistry;
    }

    public KestraApplicationContext(@NonNull ApplicationContextConfiguration configuration, PluginRegistry pluginRegistry) {
        super(configuration);
        this.pluginRegistry = pluginRegistry;
    }

    @Override
    protected @NonNull List<BeanDefinitionReference> resolveBeanDefinitionReferences(@Nullable Predicate<BeanDefinitionReference> predicate) {
        List<BeanDefinitionReference> resolvedBeanReferences = super.resolveBeanDefinitionReferences(predicate);

        if (pluginRegistry != null) {
            pluginRegistry
                .getPlugins()
                .forEach(plugin -> {
                    final SoftServiceLoader<BeanDefinitionReference> definitions = SoftServiceLoader.load(BeanDefinitionReference.class, plugin.getClassLoader());
                    definitions.collectAll(resolvedBeanReferences, reference -> reference.isPresent() && (predicate == null || predicate.test(reference)));
                });
        }

        return resolvedBeanReferences;
    }
}
