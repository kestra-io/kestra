package org.kestra.cli.contexts;

import org.kestra.core.plugins.PluginRegistry;

import java.util.Optional;

/**
 * Ugly {@link ClassLoader} that will use {@link PluginRegistry} declared class, if found, it will use the
 * {@link ClassLoader} from the plugin, else use standard {@link ClassLoader}
 */
public class KestraClassLoader extends ClassLoader {
    private static KestraClassLoader INSTANCE = new KestraClassLoader();
    private PluginRegistry pluginRegistry;

    private KestraClassLoader() {
    }

    public static KestraClassLoader instance() {
        return INSTANCE;
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

        Optional<ClassLoader> classLoader = pluginRegistry.find(name);
        if (classLoader.isPresent()) {
            return classLoader.get().loadClass(name);
        }

        return super.loadClass(name, resolve);
    }
}
