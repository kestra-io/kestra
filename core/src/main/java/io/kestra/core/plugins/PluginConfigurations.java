package io.kestra.core.plugins;

import io.kestra.core.models.Plugin;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Class holding all the static plugin configurations passed through the Kestra application configuration file.
 *
 * @see PluginConfiguration
 */
@Singleton
public final class PluginConfigurations {

    private final List<PluginConfiguration> configurations;

    /**
     * Creates a new {@link PluginConfigurations} instance.
     *
     * @param configurations the list of configuration - must not be {@code null}.
     */
    @Inject
    public PluginConfigurations(final List<PluginConfiguration> configurations) {
        Objects.requireNonNull(configurations, "configuration cannot be null");
        this.configurations = new ArrayList<>(configurations);
        this.configurations.sort(PluginConfiguration.COMPARATOR);
    }

    /**
     * Returns the key/value properties for the given plugin name.
     *
     * @param pluginType The fully qualified class name of a plugin.  Must not be {@code null}.
     * @return a flat {@link Map} containing the key/value properties associated to the plugin.
     * If no configuration exists for the given plugin, an empty {@link Map} is returned.
     */
    public Map<String, Object> getConfigurationByPluginType(final String pluginType) {
        return configurations.stream()
            .filter(config -> config.type().equalsIgnoreCase(pluginType))
            .map(PluginConfiguration::values)
            .reduce(new HashMap<>(), (accumulator, map) -> {
                accumulator.putAll(map);
                return accumulator;
            });
    }

    /**
     * Returns the key/value properties for the given plugin name.
     *
     * @param pluginType The fully qualified class name of a plugin. Must not be {@code null}.
     * @param plugin     The plugin class used to resolved aliases.
     * @return a flat {@link Map} containing the key/value properties associated to the plugin.
     * If no configuration exists for the given plugin, an empty {@link Map} is returned.
     */
    public Map<String, Object> getConfigurationByPluginTypeOrAliases(final String pluginType, final Class<? extends Plugin> plugin) {
        Map<String, Object> configuration = getConfigurationByPluginType(pluginType);
        if (configuration.isEmpty()) {
            // let's check if the plugin-configuration was provided using type alias.
            Set<String> aliases = Plugin.getAliases(plugin);
            for (String alias: aliases) {
                configuration = getConfigurationByPluginType(alias);
                if (!configuration.isEmpty()) {
                    break; // non-empty configuration was found for a plugin alias.
                }
            }
        }
        return configuration;
    }
}
