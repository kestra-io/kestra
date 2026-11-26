package io.kestra.webserver.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;

import jakarta.inject.Singleton;

@Singleton
public class PluginAliasService {
    private final PluginRegistry pluginRegistry;

    public PluginAliasService(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    public Optional<PluginTypeAliases> findByType(String type) {
        for (RegisteredPlugin registeredPlugin : pluginRegistry.plugins()) {
            Class<?> pluginType = registeredPlugin.allClass().stream()
                .filter(pluginClass -> pluginClass.getName().equals(type))
                .findFirst()
                .orElseGet(() -> registeredPlugin.getAliases().values().stream()
                    .filter(alias -> alias.getKey().equalsIgnoreCase(type))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null));

            if (pluginType == null) {
                continue;
            }

            List<String> aliases = registeredPlugin.getAliases().values().stream()
                .filter(alias -> alias.getValue().equals(pluginType))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

            return Optional.of(new PluginTypeAliases(pluginType.getName(), aliases));
        }

        return Optional.empty();
    }

    public record PluginTypeAliases(String type, List<String> aliases) {
    }
}
