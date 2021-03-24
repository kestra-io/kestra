package io.kestra.core.contexts;

import lombok.extern.slf4j.Slf4j;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;

import java.util.Optional;

/**
 * Ugly {@link ClassLoader} that will use {@link PluginRegistry} declared class, if found, it will use the
 * {@link ClassLoader} from the plugin, else use standard {@link ClassLoader}
 */
@Slf4j
public class KestraClassLoader extends ClassLoader {
    private static KestraClassLoader INSTANCE;
    private PluginRegistry pluginRegistry;

    private KestraClassLoader(ClassLoader classLoader) {
        super("kestra", classLoader);
    }

    public static KestraClassLoader create(ClassLoader classLoader) {
        if (INSTANCE != null) {
            throw new IllegalStateException("Can't init classLoader, already init");
        }

        return INSTANCE = new KestraClassLoader(classLoader);
    }

    public static boolean isInit() {
        return INSTANCE != null;
    }

    public static KestraClassLoader instance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("ClassLoader is not init for now.");
        }

        return INSTANCE;
    }

    public PluginRegistry getPluginRegistry() {
        return pluginRegistry;
    }

    public void setPluginRegistry(PluginRegistry registry) {
        pluginRegistry = registry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
        if (pluginRegistry == null) {
            return super.loadClass(name, resolve);
        }

        Optional<RegisteredPlugin> pluginSearch = pluginRegistry.find(name);
        if (pluginSearch.isPresent()) {
            RegisteredPlugin plugin = pluginSearch.get();
            if (log.isTraceEnabled()) {
                log.trace(
                    "Class '{}' found on '{}' for plugin '{}'",
                    name,
                    plugin.getClassLoader().getName(),
                    plugin.getExternalPlugin().getLocation()
                );
            }

            return plugin.getClassLoader().loadClass(name);
        }

        return super.loadClass(name, resolve);
    }
}
