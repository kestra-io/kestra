package io.kestra.webserver.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

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
        String canonicalType = null;
        Set<String> aliases = new TreeSet<>();

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

            canonicalType = pluginType.getName();
            registeredPlugin.getAliases().values().stream()
                .filter(alias -> alias.getValue().equals(pluginType))
                .map(Map.Entry::getKey)
                .forEach(aliases::add);
        }

        return Optional.ofNullable(canonicalType)
            .map(typeName -> new PluginTypeAliases(typeName, List.copyOf(aliases)));
    }

    public record PluginTypeAliases(String type, List<String> aliases) {
    }
}
