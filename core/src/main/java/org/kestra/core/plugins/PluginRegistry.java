package org.kestra.core.plugins;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import javax.inject.Singleton;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@Singleton
public class PluginRegistry  {
    private List<RegisteredPlugin> plugins;
    private Map<String, RegisteredPlugin> pluginsByClass;

    public PluginRegistry(List<RegisteredPlugin> registeredPlugin) {
        this.plugins = registeredPlugin;
        this.pluginsByClass = registeredPlugin
            .stream()
            .flatMap(plugin -> Stream.of(
                plugin.getTasks()
                    .stream()
                    .map(r -> new AbstractMap.SimpleEntry<>(r.getName(), plugin)),
                plugin.getConditions()
                    .stream()
                    .map(r -> new AbstractMap.SimpleEntry<>(r.getName(), plugin)),
                plugin.getControllers()
                    .stream()
                    .map(r -> new AbstractMap.SimpleEntry<>(r.getName(), plugin))

                ).flatMap(i -> i)
            )
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Optional<ClassLoader> find(String name) {
        if (pluginsByClass.containsKey(name)) {
            return Optional.of(pluginsByClass.get(name).getClassLoader());
        }

        return Optional.empty();
    }
}
