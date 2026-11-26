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
        return pluginRegistry.plugins().stream()
            .map(registeredPlugin -> registeredPlugin.findClass(type)
                .map(pluginType -> new PluginTypeAliases(
                    pluginType.getName(),
                    registeredPlugin.getAliases().values().stream()
                        .filter(alias -> alias.getValue().equals(pluginType))
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList()
                )))
            .flatMap(Optional::stream)
            .findFirst();
    }

    public record PluginTypeAliases(String type, List<String> aliases) {
    }
}
