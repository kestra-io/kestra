package io.kestra.core.plugins;

import java.nio.file.Path;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Registers the external plugins directory as soon as the {@link PluginRegistry} bean is created,
 * because eager {@code @Context} beans resolve plugin-provided queues during context start, before
 * the CLI command gets a chance to register the directory itself.
 */
@Singleton
public class ExternalPluginsRegistrar implements BeanCreatedEventListener<PluginRegistry> {

    public static final String PLUGINS_PATH_PROPERTY = "kestra.plugins.cli-path";

    private final Path pluginsPath;

    @Inject
    public ExternalPluginsRegistrar(@Nullable @Property(name = PLUGINS_PATH_PROPERTY) String pluginsPath) {
        this.pluginsPath = pluginsPath == null ? null : Path.of(pluginsPath);
    }

    @Override
    public PluginRegistry onCreated(BeanCreatedEvent<PluginRegistry> event) {
        PluginRegistry registry = event.getBean();
        if (pluginsPath != null) {
            registry.registerIfAbsent(pluginsPath);
        }
        return registry;
    }
}
