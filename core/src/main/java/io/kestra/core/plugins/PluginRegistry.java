package io.kestra.core.plugins;

import io.kestra.core.models.Plugin;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/**
 * Registry for managing all Kestra's {@link Plugin}.
 */
public interface PluginRegistry {

    /**
     * Scans and registers the given plugin path, if the path is not already registered.
     * This method should be a no-op if the given path is {@code null} or does not exist.
     *
     * @param pluginPath the plugin path.
     */
    void registerIfAbsent(final Path pluginPath);

    /**
     * Scans and registers the given plugin path.
     * This method should be a no-op if the given path is {@code null} or does not exist.
     *
     * @param pluginPath the plugin path.
     */
    void register(final Path pluginPath);

    /**
     * Finds the Java class corresponding to the given plugin identifier.
     *
     * @param identifier The plugin identifier - must not be {@code null}.
     * @return the {@link Class} of the plugin or {@code null} if no plugin can be found.
     */
    Class<? extends Plugin> findClassByIdentifier(PluginIdentifier identifier);

    /**
     * Finds the Java class corresponding to the given plugin identifier.
     *
     * @param identifier The raw plugin identifier - must not be {@code null}.
     * @return the {@link Class} of the plugin or {@code null} if no plugin can be found.
     */
    Class<? extends Plugin> findClassByIdentifier(String identifier);

    /**
     * Gets the list of all registered plugins.
     *
     * @return the list of registered plugins.
     */
    default List<RegisteredPlugin> plugins() {
        return plugins(null);
    }

    /**
     * Gets the list of all registered plugins.
     *
     * @param predicate The {@link Predicate} to filter the returned plugins.
     * @return the list of registered plugins.
     */
    List<RegisteredPlugin> plugins(final Predicate<RegisteredPlugin> predicate);

    /**
     * Gets a list containing only external registered plugins.
     *
     * @return the list of external registered plugins.
     */
    List<RegisteredPlugin> externalPlugins();

    /**
     * Clear the registry.
     */
    default void clear() {

    }
}
