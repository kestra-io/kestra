package io.kestra.core.contexts;

import io.kestra.core.plugins.DefaultPluginRegistry;
import io.kestra.core.plugins.PluginRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.DefaultApplicationContext;
import io.micronaut.context.DefaultApplicationContextBuilder;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
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

    private final ApplicationContext delegate;

    public static ApplicationContextBuilder builder(@Nullable PluginRegistry pluginRegistry) {
        DefaultApplicationContextBuilder builder = new DefaultApplicationContextBuilder() {
            @Override
            public ApplicationContext build() {
                return new KestraApplicationContext(super.build(), this, pluginRegistry);
            }
        };
        // Register PluginRegistry as singleton
        return builder.singletons(pluginRegistry);
    }

    public KestraApplicationContext(@NonNull ApplicationContext delegate,
                                    @NonNull ApplicationContextConfiguration configuration,
                                    PluginRegistry pluginRegistry) {
        super(configuration);
        this.delegate = delegate;
        this.pluginRegistry = pluginRegistry;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Environment getEnvironment() {
        return delegate.getEnvironment();
    }

    /**
     * Resolves the {@link BeanDefinitionReference} class instances from the {@link io.kestra.core.plugins.PluginRegistry}.
     * to found all external implementations of the following plugin types.
     * <p>
     * - {@link io.kestra.core.secret.SecretPluginInterface}
     * - {@link io.kestra.core.storages.StorageInterface}
     *
     * @return The bean definition classes
     */
    @Override
    protected @NonNull List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
        List<BeanDefinitionReference> resolvedBeanReferences = super.resolveBeanDefinitionReferences();
        if (pluginRegistry != null) {
            ((DefaultPluginRegistry)pluginRegistry)
                .externalPlugins()
                .forEach(plugin -> {
                    final SoftServiceLoader<BeanDefinitionReference> definitions = SoftServiceLoader.load(BeanDefinitionReference.class, plugin.getClassLoader());
                    definitions.collectAll(resolvedBeanReferences, BeanDefinitionReference::isPresent);
                });
        }
        return resolvedBeanReferences;
    }
}