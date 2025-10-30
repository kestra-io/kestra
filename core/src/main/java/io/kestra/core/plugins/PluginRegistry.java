package io.kestra.core.plugins;

import io.kestra.core.models.Plugin;

import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 * Registry for managing all Kestra's {@link Plugin}.
 */
public interface PluginRegistry {

    /**
     * Gets all versions for a given plugin type.
     *
     * @param type  The plugin type.
     * @return      The list of supported versions,or an empty list if the type is unknown.
     */
    List<String> getAllVersionsForType(final String type);

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
     * Unregisters the given plugin bundle.
     *
     * @param plugin the plugin bundle to un-register.
     */
    void unregister(List<RegisteredPlugin> plugin);

    /**
     * Registers a plugin class with the given identifier.
     * <p>
     * Any plugin class registered through this method will be then accessible from
     * the method {@link #findClassByIdentifier(PluginIdentifier)}.
     *
     * @param identifier  The plugin identifier.
     * @param plugin      The class for the register.
     */
    void registerClassForIdentifier(PluginIdentifier identifier, PluginClassAndMetadata<? extends Plugin> plugin);

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
     * Finds the Java class and metadata corresponding to the given identifier.
     *
     * @param identifier The raw plugin identifier - must not be {@code null}.
     * @return the {@link PluginClassAndMetadata} of the plugin or {@link Optional#empty()} if no plugin can be found.
     */
    Optional<PluginClassAndMetadata<? extends Plugin>> findMetadataByIdentifier(String identifier);

    /**
     * Finds the Java class and metadata corresponding to the given identifier.
     *
     * @param identifier The raw plugin identifier - must not be {@code null}.
     * @return the {@link PluginClassAndMetadata} of the plugin or {@link Optional#empty()} if no plugin can be found.
     */
    Optional<PluginClassAndMetadata<? extends Plugin>> findMetadataByIdentifier(PluginIdentifier identifier);

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

    /**
     * Checks whether plugin-versioning is supported by this registry.
     *
     * @return {@code true} if supported. Otherwise {@code false}.
     */
    boolean isVersioningSupported();
    
    /**
     * Computes a CRC32 hash value representing the current content of the plugin registry.
     *
     * @return a {@code long} containing the CRC32 checksum value, serving as a compact
     *         representation of the registry's content
     */
    default long hash() {
        Checksum crc32 = new CRC32();
        
        for (RegisteredPlugin plugin : plugins()) {
            Optional.ofNullable(plugin.getExternalPlugin())
                .map(ExternalPlugin::getCrc32)
                .ifPresent(checksum -> {
                    byte[] bytes = ByteBuffer.allocate(Long.BYTES).putLong(checksum).array();
                    crc32.update(bytes, 0, bytes.length);
                });
        }
        return crc32.getValue();
    }
}
